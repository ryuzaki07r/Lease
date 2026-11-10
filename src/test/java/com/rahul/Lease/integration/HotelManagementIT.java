package com.rahul.Lease.integration;

import com.rahul.Lease.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HotelManagementIT extends IntegrationTestBase {

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void createHotelValidatesInput() throws Exception {
        mvc.perform(post("/admin/hotels").header("Authorization", newManager())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clientSuppliedIdCannotOverwriteAnotherManagersHotel() throws Exception {
        String owner = newManager();
        long hotelId = createHotel(owner, "Pune");
        String nameBefore = jdbc.queryForObject("SELECT name FROM hotel WHERE id = ?", String.class, hotelId);

        long created = id(mvc.perform(post("/admin/hotels").header("Authorization", newManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + hotelId + ",\"name\":\"Hijack\",\"city\":\"Pune\"}"))
                .andExpect(status().isCreated())
                .andReturn());

        assertThat(created).isNotEqualTo(hotelId);
        assertThat(jdbc.queryForObject("SELECT name FROM hotel WHERE id = ?", String.class, hotelId))
                .isEqualTo(nameBefore);
    }

    @Test
    void roomsOfOtherHotelsAreNotReachableThroughOwnHotel() throws Exception {
        String owner = newManager();
        long roomId = createRoom(owner, createHotel(owner, "Pune"), 1);
        String other = newManager();
        long otherHotel = createHotel(other, "Pune");

        mvc.perform(get("/admin/hotels/" + otherHotel + "/rooms/" + roomId).header("Authorization", other))
                .andExpect(status().isNotFound());
        mvc.perform(put("/admin/hotels/" + otherHotel + "/rooms/" + roomId).header("Authorization", other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"X\",\"basePrice\":1,\"totalCount\":0,\"capacity\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void activationCreatesInventoryAndPricesAndIsIdempotent() throws Exception {
        ActiveHotel h = activeHotel(2);

        assertThat(count("SELECT COUNT(*) FROM inventory WHERE room_id = ?", h.roomId())).isGreaterThan(360);
        assertThat(count("SELECT COUNT(*) FROM hotel_min_price WHERE hotel_id = ?", h.hotelId())).isGreaterThan(360);

        activate(h.manager(), h.hotelId());
    }

    @Test
    void updateKeepsHotelActive() throws Exception {
        ActiveHotel h = activeHotel(1);
        mvc.perform(put("/admin/hotels/" + h.hotelId()).header("Authorization", h.manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"city\":\"" + h.city() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    void deletingActivatedHotelWithoutBookingsRemovesItsData() throws Exception {
        ActiveHotel h = activeHotel(1);

        mvc.perform(delete("/admin/hotels/" + h.hotelId()).header("Authorization", h.manager()))
                .andExpect(status().isNoContent());

        assertThat(count("SELECT COUNT(*) FROM hotel_min_price WHERE hotel_id = ?", h.hotelId())).isZero();
        assertThat(count("SELECT COUNT(*) FROM inventory WHERE hotel_id = ?", h.hotelId())).isZero();
    }

    @Test
    void hotelsAndRoomsWithBookingsCannotBeDeleted() throws Exception {
        ActiveHotel h = activeHotel(1);
        mvc.perform(post("/bookings/init").header("Authorization", newGuest())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(h.hotelId(), h.roomId(), tomorrow, in3Days, 1)))
                .andExpect(status().isOk());

        mvc.perform(delete("/admin/hotels/" + h.hotelId()).header("Authorization", h.manager()))
                .andExpect(status().isConflict());
        mvc.perform(delete("/admin/hotels/" + h.hotelId() + "/rooms/" + h.roomId()).header("Authorization", h.manager()))
                .andExpect(status().isConflict());
    }

    @Test
    void searchFindsHotelsWithEnoughRoomsAndHidesOwner() throws Exception {
        ActiveHotel h = activeHotel(2);
        String search = "/hotels/search?city=" + h.city() + "&startDate=" + tomorrow + "&endDate=" + in3Days;

        String body = mvc.perform(get(search + "&roomsCount=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].hotel.id").value(h.hotelId()))
                .andExpect(jsonPath("$.data.content[0].price").isNumber())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("owner").doesNotContain("password");

        mvc.perform(get(search + "&roomsCount=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void searchExcludesClosedDates() throws Exception {
        ActiveHotel h = activeHotel(1);
        mvc.perform(patch("/admin/inventory/rooms/" + h.roomId()).header("Authorization", h.manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"" + tomorrow + "\",\"endDate\":\"" + tomorrow
                                + "\",\"surgeFactor\":1,\"closed\":true}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/hotels/search?city=" + h.city() + "&startDate=" + tomorrow + "&endDate=" + in3Days
                        + "&roomsCount=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void searchValidatesParameters() throws Exception {
        mvc.perform(get("/hotels/search?city=Pune")).andExpect(status().isBadRequest());
        mvc.perform(get("/hotels/search?city=Pune&startDate=" + in3Days + "&endDate=" + tomorrow + "&roomsCount=1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inventoryUpdateAndRead() throws Exception {
        ActiveHotel h = activeHotel(1);
        mvc.perform(patch("/admin/inventory/rooms/" + h.roomId()).header("Authorization", h.manager())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/admin/inventory/rooms/" + h.roomId()).header("Authorization", h.manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"" + tomorrow + "\",\"endDate\":\"" + in3Days
                                + "\",\"surgeFactor\":1.5,\"closed\":false}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/admin/inventory/rooms/" + h.roomId()).header("Authorization", h.manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].bookCount").value(0));
        assertThat(jdbc.queryForObject("SELECT surge_factor FROM inventory WHERE room_id = ? AND date = ?",
                Double.class, h.roomId(), tomorrow)).isEqualTo(1.5);
    }

    @Test
    void inventoryOfAnotherManagersRoomIsForbidden() throws Exception {
        ActiveHotel h = activeHotel(1);
        mvc.perform(get("/admin/inventory/rooms/" + h.roomId()).header("Authorization", newManager()))
                .andExpect(status().isForbidden());
    }

    @Test
    void reportAcceptsIsoDatesAndRejectsReversedRange() throws Exception {
        ActiveHotel h = activeHotel(1);
        mvc.perform(get("/admin/hotels/" + h.hotelId() + "/reports?startDate=2026-01-01&endDate=" + in3Days)
                        .header("Authorization", h.manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookingCount").value(0));
        mvc.perform(get("/admin/hotels/" + h.hotelId() + "/reports?startDate=" + in3Days + "&endDate=2026-01-01")
                        .header("Authorization", h.manager()))
                .andExpect(status().isBadRequest());
    }
}
