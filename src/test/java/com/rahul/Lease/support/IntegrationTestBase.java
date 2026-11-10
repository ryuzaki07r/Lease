package com.rahul.Lease.support;

import com.jayway.jsonpath.JsonPath;
import com.rahul.Lease.Enums.Role;
import com.rahul.Lease.Models.User;
import com.rahul.Lease.Repository.UserRepository;
import com.stripe.Stripe;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the whole application against a real (embedded) Postgres, so the JPQL queries, row locks and
 * transactions are exercised exactly as in production. All subclasses share one Spring context and database;
 * tests stay independent by creating their own users and hotels with unique names.
 */
@SpringBootTest(properties = {
        "jwt.secretKey=integration-test-secret-key-0123456789abcdef",
        "frontend.url=http://frontend.test",
        "stripe.secret.key=sk_test_integration",
        "stripe.webhook.secret=" + IntegrationTestBase.WEBHOOK_SECRET,
        // Tests run the expiry job themselves instead of waiting for the schedule
        "booking.expiry.check-interval-ms=3600000"
})
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    public static final String WEBHOOK_SECRET = "whsec_integration";
    public static final String PASSWORD = "password123";

    private static final EmbeddedPostgres POSTGRES;

    static {
        try {
            POSTGRES = EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                POSTGRES.close();
            } catch (IOException ignored) {
            }
        }));
        // Nothing listens here, so any Stripe API call fails fast instead of reaching the internet
        Stripe.overrideApiBase("http://127.0.0.1:9");
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected UserRepository userRepository;

    protected final LocalDate tomorrow = LocalDate.now().plusDays(1);
    protected final LocalDate in3Days = LocalDate.now().plusDays(3);

    protected static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected static <T> T json(MvcResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected static long id(MvcResult result) {
        return ((Number) json(result, "$.data.id")).longValue();
    }

    protected String signup(String email) throws Exception {
        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"name\":\"Test\"}"))
                .andExpect(status().isCreated());
        return email;
    }

    /** There is no API for creating managers, so the role is granted directly. */
    protected void makeManager(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setRoles(Set.of(Role.MANAGER));
        userRepository.save(user);
    }

    protected MvcResult loginResult(String email) throws Exception {
        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
    }

    protected String bearer(String email) throws Exception {
        return "Bearer " + json(loginResult(email), "$.data.accessToken");
    }

    protected String newGuest() throws Exception {
        return bearer(signup(unique("guest") + "@test.com"));
    }

    protected String newManager() throws Exception {
        String email = signup(unique("manager") + "@test.com");
        makeManager(email);
        return bearer(email);
    }

    protected long createHotel(String manager, String city) throws Exception {
        return id(mvc.perform(post("/admin/hotels").header("Authorization", manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + unique("Hotel") + "\",\"city\":\"" + city + "\"}"))
                .andExpect(status().isCreated())
                .andReturn());
    }

    protected long createRoom(String manager, long hotelId, int totalCount) throws Exception {
        return id(mvc.perform(post("/admin/hotels/" + hotelId + "/rooms").header("Authorization", manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"Deluxe\",\"basePrice\":1000,\"totalCount\":" + totalCount
                                + ",\"capacity\":2}"))
                .andExpect(status().isOk())
                .andReturn());
    }

    protected void activate(String manager, long hotelId) throws Exception {
        mvc.perform(patch("/admin/hotels/" + hotelId + "/activate").header("Authorization", manager))
                .andExpect(status().isNoContent());
    }

    /** An active hotel with one room type, inventory and prices, in a city no other test uses. */
    protected record ActiveHotel(String manager, String city, long hotelId, long roomId) {}

    protected ActiveHotel activeHotel(int rooms) throws Exception {
        String manager = newManager();
        String city = unique("City");
        long hotelId = createHotel(manager, city);
        long roomId = createRoom(manager, hotelId, rooms);
        activate(manager, hotelId);
        return new ActiveHotel(manager, city, hotelId, roomId);
    }

    protected String bookingJson(long hotelId, long roomId, LocalDate checkIn, LocalDate checkOut, int rooms) {
        return "{\"hotelId\":" + hotelId + ",\"roomId\":" + roomId + ",\"checkInDate\":\"" + checkIn
                + "\",\"checkOutDate\":\"" + checkOut + "\",\"roomsCount\":" + rooms + "}";
    }

    /** "reserved/booked" summed over the nights tomorrow and the day after (checkout day excluded). */
    protected String inventory(long roomId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(reserved_count),0) || '/' || COALESCE(SUM(book_count),0)"
                + " FROM inventory WHERE room_id = ? AND date >= ? AND date < ?", String.class,
                roomId, tomorrow, in3Days);
    }

    protected String bookingStatus(long bookingId) {
        return jdbc.queryForObject("SELECT booking_status FROM booking WHERE id = ?", String.class, bookingId);
    }

    /**
     * A Stripe checkout session event. Its API version deliberately differs from stripe-java's, which is
     * what made the old code drop paid events.
     */
    protected String webhookPayload(String type, String sessionId, String paymentStatus) {
        return "{\"id\":\"" + unique("evt") + "\",\"object\":\"event\",\"api_version\":\"2020-08-27\",\"type\":\""
                + type + "\",\"data\":{\"object\":{\"id\":\"" + sessionId + "\",\"object\":\"checkout.session\","
                + "\"payment_status\":\"" + paymentStatus + "\",\"payment_intent\":\"pi_test\"}}}";
    }

    protected static String stripeSignature(String payload) throws Exception {
        long timestamp = System.currentTimeMillis() / 1000;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signature = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(signature);
    }
}
