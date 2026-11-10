package com.rahul.Lease.integration;

import com.rahul.Lease.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ErrorHandlingIT extends IntegrationTestBase {

    @Test
    void unknownRouteIs404() throws Exception {
        mvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").isNotEmpty());
    }

    @Test
    void wrongMethodIs405() throws Exception {
        mvc.perform(delete("/auth/login")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void malformedJsonIs400() throws Exception {
        mvc.perform(post("/bookings/init").header("Authorization", newGuest())
                        .contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void badPathVariableIs400() throws Exception {
        mvc.perform(get("/bookings/abc/status").header("Authorization", newGuest()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingResourceIs404() throws Exception {
        mvc.perform(get("/bookings/999999999/status").header("Authorization", newGuest()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/admin/hotels/999999999").header("Authorization", newManager()))
                .andExpect(status().isNotFound());
    }

    @Test
    void corsAllowsOnlyTheFrontend() throws Exception {
        mvc.perform(options("/auth/login")
                        .header("Origin", "http://frontend.test")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://frontend.test"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(options("/auth/login")
                        .header("Origin", "http://evil.test")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
