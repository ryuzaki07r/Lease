package com.rahul.Lease.integration;

import com.rahul.Lease.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthIT extends IntegrationTestBase {

    @Test
    void signupRejectsInvalidInput() throws Exception {
        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\",\"name\":\"A\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.subErrors").isNotEmpty());
    }

    @Test
    void signupDoesNotReturnThePasswordAndRejectsDuplicates() throws Exception {
        String email = unique("dup") + "@test.com";
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"name\":\"A\"}";

        String response = mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(PASSWORD).doesNotContain("\"password\"");

        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void signupIgnoresClientSuppliedIdAndRoles() throws Exception {
        String victim = signup(unique("victim") + "@test.com");
        long victimId = userRepository.findByEmail(victim).orElseThrow().getId();
        String attacker = unique("attacker") + "@test.com";

        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + victimId + ",\"email\":\"" + attacker + "\",\"password\":\""
                                + PASSWORD + "\",\"name\":\"A\"}"))
                .andExpect(status().isCreated());

        assertThat(userRepository.findByEmail(victim)).isPresent();
        assertThat(userRepository.findByEmail(attacker).orElseThrow().getId()).isNotEqualTo(victimId);
    }

    @Test
    void loginWithWrongPasswordIs401() throws Exception {
        String email = signup(unique("user") + "@test.com");
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrongpass1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginReturnsAccessTokenAndSetsRefreshCookie() throws Exception {
        MvcResult login = loginResult(signup(unique("user") + "@test.com"));

        assertThat((String) json(login, "$.data.accessToken")).isNotBlank();
        mvc.perform(get("/users/profile").header("Authorization", "Bearer " + json(login, "$.data.accessToken")))
                .andExpect(status().isOk());

        Cookie refresh = login.getResponse().getCookie("refreshToken");
        assertThat(refresh).isNotNull();
        assertThat(refresh.isHttpOnly()).isTrue();
        assertThat(refresh.getSecure()).isTrue();
        assertThat(refresh.getPath()).isEqualTo("/api/v1/auth");
    }

    @Test
    void refreshIssuesNewAccessTokenFromCookie() throws Exception {
        Cookie refresh = loginResult(signup(unique("user") + "@test.com")).getResponse().getCookie("refreshToken");

        mvc.perform(post("/auth/refresh").cookie(refresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
        mvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokensCannotBeUsedAsTheOtherType() throws Exception {
        MvcResult login = loginResult(signup(unique("user") + "@test.com"));
        String accessToken = json(login, "$.data.accessToken");
        String refreshToken = login.getResponse().getCookie("refreshToken").getValue();

        mvc.perform(get("/users/profile").header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", accessToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingOrMalformedCredentialsAre401() throws Exception {
        mvc.perform(get("/users/profile")).andExpect(status().isUnauthorized());
        mvc.perform(get("/users/profile").header("Authorization", "Bearer"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/users/profile").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenOfDeletedUserIs401() throws Exception {
        String email = signup(unique("gone") + "@test.com");
        String token = bearer(email);
        userRepository.delete(userRepository.findByEmail(email).orElseThrow());

        mvc.perform(get("/users/profile").header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminRoutesNeedManagerRole() throws Exception {
        mvc.perform(get("/admin/hotels").header("Authorization", newGuest()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/hotels").header("Authorization", newManager()))
                .andExpect(status().isOk());
    }
}
