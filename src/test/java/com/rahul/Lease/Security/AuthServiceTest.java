package com.rahul.Lease.Security;

import com.rahul.Lease.Dto.SignupRequestDTO;
import com.rahul.Lease.Enums.Role;
import com.rahul.Lease.Models.User;
import com.rahul.Lease.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock JWTService jwtService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authenticationManager;
    private AuthService authService;

    private SignupRequestDTO request;

    @BeforeEach
    void setUp() {
        // Real mapper: the bug being guarded against was the mapper copying fields it should not
        authService = new AuthService(userRepository, new ModelMapper(), jwtService, passwordEncoder,
                authenticationManager);
        request = new SignupRequestDTO();
        request.setEmail("new@test.com");
        request.setPassword("password123");
        request.setName("New");
    }

    @Test
    void signupRejectsRegisteredEmail() {
        when(userRepository.findByEmail("new@test.com")).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> authService.signUp(request)).isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void signupCreatesGuestWithHashedPassword() {
        when(userRepository.findByEmail("new@test.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.signUp(request);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isNull();
        assertThat(saved.getValue().getRoles()).containsExactly(Role.GUEST);
        assertThat(saved.getValue().getPassword()).isEqualTo("hashed");
    }

    @Test
    void refreshAcceptsOnlyRefreshTokens() {
        User user = new User();
        user.setId(7L);
        when(jwtService.getUserIdFromToken("rt", JWTService.REFRESH_TOKEN)).thenReturn(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(jwtService.getAccessToken(user)).thenReturn("new-access");

        assertThat(authService.refreshToken("rt")).isEqualTo("new-access");
    }
}
