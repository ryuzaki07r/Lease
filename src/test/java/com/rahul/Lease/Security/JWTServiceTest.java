package com.rahul.Lease.Security;

import com.rahul.Lease.Enums.Role;
import com.rahul.Lease.Models.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JWTServiceTest {

    private static final String SECRET = "unit-test-secret-key-0123456789abcdefghij";

    private final JWTService jwtService = new JWTService();
    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtService, "jwtSecretKey", SECRET);
        user = new User();
        user.setId(42L);
        user.setEmail("user@test.com");
        user.setRoles(Set.of(Role.GUEST));
    }

    @Test
    void accessTokenRoundTrips() {
        String token = jwtService.getAccessToken(user);
        assertThat(jwtService.getUserIdFromToken(token, JWTService.ACCESS_TOKEN)).isEqualTo(42L);
    }

    @Test
    void refreshTokenRoundTrips() {
        String token = jwtService.generateRefreshToken(user);
        assertThat(jwtService.getUserIdFromToken(token, JWTService.REFRESH_TOKEN)).isEqualTo(42L);
    }

    @Test
    void refreshTokenIsNotAnAccessToken() {
        String token = jwtService.generateRefreshToken(user);
        assertThatThrownBy(() -> jwtService.getUserIdFromToken(token, JWTService.ACCESS_TOKEN))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void accessTokenIsNotARefreshToken() {
        String token = jwtService.getAccessToken(user);
        assertThatThrownBy(() -> jwtService.getUserIdFromToken(token, JWTService.REFRESH_TOKEN))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JWTService other = new JWTService();
        ReflectionTestUtils.setField(other, "jwtSecretKey", "another-secret-key-0123456789abcdefghijkl");
        String token = other.getAccessToken(user);

        assertThatThrownBy(() -> jwtService.getUserIdFromToken(token, JWTService.ACCESS_TOKEN))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.getAccessToken(user);
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThatThrownBy(() -> jwtService.getUserIdFromToken(tampered, JWTService.ACCESS_TOKEN))
                .isInstanceOf(JwtException.class);
    }
}
