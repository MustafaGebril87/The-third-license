package com.thethirdlicense.controllers;

import com.thethirdlicense.exceptions.UsernameAlreadyExistsException;
import com.thethirdlicense.models.User;
import com.thethirdlicense.repositories.UserRepository;
import com.thethirdlicense.requests.LoginRequest;
import com.thethirdlicense.requests.RegisterRequest;
import com.thethirdlicense.responses.AuthResponse;
import com.thethirdlicense.security.JWTUtil;
import com.thethirdlicense.services.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private UserRepository userRepository;
    @Mock private UserService userService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JWTUtil jwtUtil;

    @InjectMocks
    private AuthController controller;

    // ── helpers ──────────────────────────────────────────────────────────────

    private RegisterRequest registerRequest(String username, String email, String password) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username);
        req.setEmail(email);
        req.setPassword(password);
        return req;
    }

    private LoginRequest loginRequest(String username, String password) {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }

    private User mockUser(String username, String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword("hashed");
        return user;
    }

    // ── Scenario: User registers with valid credentials ───────────────────────

    @Test
    void register_validRequest_returns200() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("pass123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = controller.register(registerRequest("alice", "alice@test.com", "pass123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("User registered successfully");
        verify(userRepository).save(any(User.class));
    }

    // ── Scenario: User tries to register with a taken username ───────────────

    @Test
    void register_usernameTaken_throwsUsernameAlreadyExistsException() {
        User existing = mockUser("alice", "alice@other.com");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existing));

        assertThrows(UsernameAlreadyExistsException.class,
            () -> controller.register(registerRequest("alice", "alice@test.com", "pass123")));

        verify(userRepository, never()).save(any());
    }

    // ── Scenario: Registration request missing required fields ────────────────

    @Test
    void register_missingUsername_returnsBadRequest() {
        RegisterRequest req = registerRequest(null, "alice@test.com", "pass123");

        ResponseEntity<?> response = controller.register(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().toString()).contains("must not be null");
    }

    @Test
    void register_missingEmail_returnsBadRequest() {
        RegisterRequest req = registerRequest("alice", null, "pass123");

        ResponseEntity<?> response = controller.register(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void register_missingPassword_returnsBadRequest() {
        RegisterRequest req = registerRequest("alice", "alice@test.com", null);

        ResponseEntity<?> response = controller.register(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Scenario: User logs in with correct credentials ──────────────────────

    @Test
    void login_validCredentials_returnsTokens() {
        User user = mockUser("alice", "alice@test.com");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
            .thenReturn(mock(org.springframework.security.core.Authentication.class));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(user)).thenReturn("access-jwt");
        when(jwtUtil.generateRefreshToken(user)).thenReturn("refresh-jwt");

        ResponseEntity<?> response = controller.login(loginRequest("alice", "pass123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AuthResponse body = (AuthResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getAccessToken()).isEqualTo("access-jwt");
        assertThat(body.getRefreshToken()).isEqualTo("refresh-jwt");
    }

    // ── Scenario: User logs in with wrong password ────────────────────────────

    @Test
    void login_wrongPassword_throwsBadCredentialsException() {
        when(authenticationManager.authenticate(any()))
            .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class,
            () -> controller.login(loginRequest("alice", "wrong")));

        verify(userRepository, never()).findByUsername(anyString());
    }

    // ── Scenario: Login for non-existent user ─────────────────────────────────

    @Test
    void login_userNotFound_afterAuthentication_throwsException() {
        when(authenticationManager.authenticate(any()))
            .thenReturn(mock(org.springframework.security.core.Authentication.class));
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(Exception.class,
            () -> controller.login(loginRequest("ghost", "pass123")));
    }
}
