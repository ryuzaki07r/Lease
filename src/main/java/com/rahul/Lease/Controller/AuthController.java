package com.rahul.Lease.Controller;

import com.rahul.Lease.Dto.LoginDTO;
import com.rahul.Lease.Dto.LoginResponseDTO;
import com.rahul.Lease.Dto.SignupRequestDTO;
import com.rahul.Lease.Dto.UserDTO;
import com.rahul.Lease.Security.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Signup, login and token refresh")
public class AuthController {
    private static final String REFRESH_COOKIE = "refreshToken";

    private final AuthService authService;

    @PostMapping("/signup")
    @Operation(summary = "Create a new account", tags = {"Authentication"})
    public ResponseEntity<UserDTO> signUp(@RequestBody @Valid SignupRequestDTO signupRequestDTO){
        return new ResponseEntity<>(authService.signUp(signupRequestDTO), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Returns an access token; the refresh token is set as an HttpOnly cookie",
            tags = {"Authentication"})
    public ResponseEntity<LoginResponseDTO> login(@RequestBody @Valid LoginDTO loginDTO){
        String[] tokens = authService.login(loginDTO);
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, tokens[1])
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(Duration.ofDays(180))
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new LoginResponseDTO(tokens[0]));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Uses the refresh token cookie", tags = {"Authentication"})
    public ResponseEntity<LoginResponseDTO> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken){
        if(refreshToken == null){
            throw new AuthenticationCredentialsNotFoundException("Refresh token cookie is missing");
        }
        return ResponseEntity.ok(new LoginResponseDTO(authService.refreshToken(refreshToken)));
    }
}
