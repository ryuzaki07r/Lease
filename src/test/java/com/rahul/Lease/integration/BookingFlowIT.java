package com.rahul.Lease.integration;

import com.rahul.Lease.Services.BookingExpiryScheduler;
import com.rahul.Lease.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingFlowIT extends IntegrationTestBase {

    @Autowired
    private BookingExpiryScheduler expiryScheduler;

    private ResultActions book(String guest, ActiveHotel h, int rooms) throws Exception {
        return mvc.perform(post("/bookings/init").header("Authorization", guest)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson(h.hotelId(), h.roomId(), tomorrow, in3Days, rooms)));
    }

    private long reserve(String guest, ActiveHotel h) throws Exception {
        return id(book(guest, h, 1).andExpect(status().isOk()).andReturn());
    }

    private void addGuests(String guest, long bookingId) throws Exception {
        mvc.perform(post("/bookings/" + bookingId + "/addGuests").header("Authorization", guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"name\":\"Traveller\",\"gender\":\"FEMALE\",\"age\":28}]"))
                .andExpect(status().isOk());
    }

    /** Stands in for a real Stripe checkout session, which needs a live Stripe key to create. */
    private String awaitPayment(long bookingId) {
        String sessionId = unique("cs");
        jdbc.update("UPDATE booking SET booking_status = 'PAYMENT_PENDING', payment_session_id = ? WHERE id = ?",
                sessionId, bookingId);
        return sessionId;
    }

    private ResultActions webhook(String payload) throws Exception {
        return mvc.perform(post("/webhook/payments").contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", stripeSignature(payload)).content(payload));
    }

    private long confirmedBooking(String guest, ActiveHotel h) throws Exception {
        long bookingId = reserve(guest, h);
        addGuests(guest, bookingId);
        String sessionId = awaitPayment(bookingId);
        webhook(webhookPayload("checkout.session.completed", sessionId, "paid")).andExpect(status().isNoContent());
        assertThat(bookingStatus(bookingId)).isEqualTo("CONFIRMED");
        return bookingId;
    }

    @Test
    void bookingReservesOnlyTheNightsStayed() throws Exception {
        ActiveHotel h = activeHotel(2);
        reserve(newGuest(), h);

        assertThat(inventory(h.roomId())).isEqualTo("2/0");
        assertThat(jdbc.queryForObject("SELECT reserved_count FROM inventory WHERE room_id = ? AND date = ?",
                Integer.class, h.roomId(), in3Days)).isZero();
    }

    @Test
    void bookingAmountIsPriceOfEachNightTimesRooms() throws Exception {
        ActiveHotel h = activeHotel(2);
        double amount = ((Number) json(book(newGuest(), h, 2).andExpect(status().isOk()).andReturn(),
                "$.data.amount")).doubleValue();
        // Base price 1000, two nights, two rooms: at least 4000 before surge/urgency multipliers
        assertThat(amount).isGreaterThanOrEqualTo(4000);
    }

    @Test
    void invalidBookingRequestsAreRejected() throws Exception {
        ActiveHotel h = activeHotel(1);
        long otherHotel = createHotel(h.manager(), h.city());
        String guest = newGuest();

        book(guest, h, -1).andExpect(status().isBadRequest());
        mvc.perform(post("/bookings/init").header("Authorization", guest).contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(h.hotelId(), h.roomId(), in3Days, tomorrow, 1)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/bookings/init").header("Authorization", guest).contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(h.hotelId(), h.roomId(), tomorrow.minusDays(2), in3Days, 1)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/bookings/init").header("Authorization", guest).contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(otherHotel, h.roomId(), tomorrow, in3Days, 1)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/bookings/init").contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(h.hotelId(), h.roomId(), tomorrow, in3Days, 1)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cannotOverbook() throws Exception {
        ActiveHotel h = activeHotel(2);
        reserve(newGuest(), h);

        book(newGuest(), h, 2).andExpect(status().isConflict());
        reserve(newGuest(), h);
        book(newGuest(), h, 1).andExpect(status().isConflict());
        assertThat(inventory(h.roomId())).isEqualTo("4/0");
    }

    @Test
    void concurrentRequestsForTheLastRoomOnlyOneWins() throws Exception {
        ActiveHotel h = activeHotel(1);
        int attempts = 6;
        List<String> guests = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            guests.add(newGuest());
        }

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (String guest : guests) {
            results.add(pool.submit(() -> {
                start.await();
                return book(guest, h, 1).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(attempts - 1);
        assertThat(inventory(h.roomId())).isEqualTo("2/0");
    }

    @Test
    void addGuestsValidatesInputAndOwnership() throws Exception {
        ActiveHotel h = activeHotel(1);
        String guest = newGuest();
        long bookingId = reserve(guest, h);

        mvc.perform(post("/bookings/" + bookingId + "/addGuests").header("Authorization", guest)
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/bookings/" + bookingId + "/addGuests").header("Authorization", guest)
                        .contentType(MediaType.APPLICATION_JSON).content("[{\"name\":\"\"}]"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/bookings/" + bookingId + "/addGuests").header("Authorization", newGuest())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"name\":\"Intruder\",\"gender\":\"MALE\"}]"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/bookings/" + bookingId + "/status").header("Authorization", newGuest()))
                .andExpect(status().isForbidden());

        addGuests(guest, bookingId);
        assertThat(bookingStatus(bookingId)).isEqualTo("GUESTS_ADDED");
    }

    @Test
    void paymentCannotStartBeforeGuestsAreAdded() throws Exception {
        ActiveHotel h = activeHotel(1);
        String guest = newGuest();
        long bookingId = reserve(guest, h);

        mvc.perform(post("/bookings/" + bookingId + "/payment").header("Authorization", guest))
                .andExpect(status().isConflict());
    }

    @Test
    void failedCheckoutSessionLeavesBookingUnchanged() throws Exception {
        ActiveHotel h = activeHotel(1);
        String guest = newGuest();
        long bookingId = reserve(guest, h);
        addGuests(guest, bookingId);

        // Stripe is unreachable in tests, so creating the checkout session fails
        mvc.perform(post("/bookings/" + bookingId + "/payment").header("Authorization", guest))
                .andExpect(status().isBadGateway());
        assertThat(bookingStatus(bookingId)).isEqualTo("GUESTS_ADDED");
    }

    @Test
    void abandonedBookingsAreExpiredAndRoomsReleased() throws Exception {
        ActiveHotel h = activeHotel(2);
        String guest = newGuest();
        long abandoned = reserve(guest, h);
        long fresh = reserve(newGuest(), h);
        jdbc.update("UPDATE booking SET created_at = now() - interval '11 minutes' WHERE id = ?", abandoned);

        expiryScheduler.expireStaleBookings();

        assertThat(bookingStatus(abandoned)).isEqualTo("EXPIRED");
        assertThat(bookingStatus(fresh)).isEqualTo("RESERVED");
        assertThat(inventory(h.roomId())).isEqualTo("2/0");
        mvc.perform(post("/bookings/" + abandoned + "/addGuests").header("Authorization", guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"name\":\"Late\",\"gender\":\"MALE\"}]"))
                .andExpect(status().isConflict());
    }

    @Test
    void expiryJobDoesNotTouchBookingsAwaitingPayment() throws Exception {
        ActiveHotel h = activeHotel(1);
        String guest = newGuest();
        long bookingId = reserve(guest, h);
        addGuests(guest, bookingId);
        awaitPayment(bookingId);
        jdbc.update("UPDATE booking SET created_at = now() - interval '11 minutes' WHERE id = ?", bookingId);

        expiryScheduler.expireStaleBookings();

        assertThat(bookingStatus(bookingId)).isEqualTo("PAYMENT_PENDING");
        assertThat(inventory(h.roomId())).isEqualTo("2/0");
    }

    @Test
    void webhookWithBadSignatureIsRejected() throws Exception {
        mvc.perform(post("/webhook/payments").contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=1,v1=bad").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/webhook/payments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void paidSessionConfirmsBookingOnceEvenIfDeliveredTwice() throws Exception {
        ActiveHotel h = activeHotel(2);
        String guest = newGuest();
        long bookingId = reserve(guest, h);
        addGuests(guest, bookingId);
        String payload = webhookPayload("checkout.session.completed", awaitPayment(bookingId), "paid");

        webhook(payload).andExpect(status().isNoContent());
        assertThat(bookingStatus(bookingId)).isEqualTo("CONFIRMED");
        assertThat(inventory(h.roomId())).isEqualTo("0/2");

        webhook(payload).andExpect(status().isNoContent());
        assertThat(inventory(h.roomId())).isEqualTo("0/2");
    }

    @Test
    void completedButUnpaidSessionWaitsForAsyncPayment() throws Exception {
        ActiveHotel h = activeHotel(1);
        String guest = newGuest();
        long bookingId = reserve(guest, h);
        addGuests(guest, bookingId);
        String sessionId = awaitPayment(bookingId);

        webhook(webhookPayload("checkout.session.completed", sessionId, "unpaid")).andExpect(status().isNoContent());
        assertThat(bookingStatus(bookingId)).isEqualTo("PAYMENT_PENDING");

        webhook(webhookPayload("checkout.session.async_payment_succeeded", sessionId, "paid"))
                .andExpect(status().isNoContent());
        assertThat(bookingStatus(bookingId)).isEqualTo("CONFIRMED");
        assertThat(inventory(h.roomId())).isEqualTo("0/2");
    }

    @Test
    void expiredOrFailedSessionReleasesRooms() throws Exception {
        ActiveHotel h = activeHotel(2);
        String guest = newGuest();
        long expired = reserve(guest, h);
        addGuests(guest, expired);
        long failed = reserve(guest, h);
        addGuests(guest, failed);
        String expiredSession = awaitPayment(expired);
        String failedSession = awaitPayment(failed);

        webhook(webhookPayload("checkout.session.expired", expiredSession, "unpaid"))
                .andExpect(status().isNoContent());
        webhook(webhookPayload("checkout.session.async_payment_failed", failedSession, "unpaid"))
                .andExpect(status().isNoContent());

        assertThat(bookingStatus(expired)).isEqualTo("EXPIRED");
        assertThat(bookingStatus(failed)).isEqualTo("EXPIRED");
        assertThat(inventory(h.roomId())).isEqualTo("0/0");
    }

    @Test
    void unknownEventTypesAreAcknowledged() throws Exception {
        webhook(webhookPayload("customer.created", "cs_none", "paid")).andExpect(status().isNoContent());
    }

    @Test
    void cancellingOnCheckInDayIsRejected() throws Exception {
        ActiveHotel h = activeHotel(1);
        String guest = newGuest();
        long bookingId = confirmedBooking(guest, h);
        jdbc.update("UPDATE booking SET check_in_date = current_date WHERE id = ?", bookingId);

        mvc.perform(post("/bookings/" + bookingId + "/cancelBooking").header("Authorization", guest))
                .andExpect(status().isConflict());
    }

    @Test
    void cancellingSomeoneElsesBookingIsForbidden() throws Exception {
        ActiveHotel h = activeHotel(1);
        long bookingId = confirmedBooking(newGuest(), h);

        mvc.perform(post("/bookings/" + bookingId + "/cancelBooking").header("Authorization", newGuest()))
                .andExpect(status().isForbidden());
    }

    @Test
    void failedRefundRollsBackCancellationOfFullRoom() throws Exception {
        ActiveHotel h = activeHotel(1);
        String guest = newGuest();
        long bookingId = confirmedBooking(guest, h);
        assertThat(inventory(h.roomId())).isEqualTo("0/2");

        // The room is full, which the old cancel query could not match. The release now succeeds and the
        // request reaches the refund, which fails because Stripe is unreachable in tests.
        mvc.perform(post("/bookings/" + bookingId + "/cancelBooking").header("Authorization", guest))
                .andExpect(status().isBadGateway());

        assertThat(bookingStatus(bookingId)).isEqualTo("CONFIRMED");
        assertThat(inventory(h.roomId())).isEqualTo("0/2");
    }

    @Test
    void reportCountsConfirmedBookings() throws Exception {
        ActiveHotel h = activeHotel(2);
        confirmedBooking(newGuest(), h);
        reserve(newGuest(), h);

        mvc.perform(get("/admin/hotels/" + h.hotelId() + "/reports").header("Authorization", h.manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookingCount").value(1));
    }
}
