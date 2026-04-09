package com.thethirdlicense.controllers;

import com.thethirdlicense.models.Role;
import com.thethirdlicense.models.User;
import com.thethirdlicense.repositories.UserRepository;
import com.thethirdlicense.requests.LoginRequest;
import com.thethirdlicense.requests.RegisterRequest;
import com.thethirdlicense.responses.AuthResponse;
import com.thethirdlicense.security.JWTUtil;
import com.thethirdlicense.services.UserService;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;
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
        user.setRoles(Set.of(Role.CONTRIBUTOR));
        return user;
    }

    private void setExpirations(long accessMs, long refreshMs) {
        ReflectionTestUtils.setField(controller, "accessTokenExpirationMs", accessMs);
        ReflectionTestUtils.setField(controller, "refreshTokenExpirationMs", refreshMs);
    }

    // ── register: valid request ───────────────────────────────────────────────

    @Test
    void register_validRequest_returns200() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password1")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = controller.register(registerRequest("alice", "alice@test.com", "password1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("User registered successfully");
        verify(userRepository).save(any(User.class));
    }

    // ── register: null fields ────────────────────────────────────────────────

    @Test
    void register_nullUsername_returnsBadRequest() {
        ResponseEntity<?> response = controller.register(registerRequest(null, "alice@test.com", "password1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().toString()).contains("required");
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_nullEmail_returnsBadRequest() {
        ResponseEntity<?> response = controller.register(registerRequest("alice", null, "password1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_nullPassword_returnsBadRequest() {
        ResponseEntity<?> response = controller.register(registerRequest("alice", "alice@test.com", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(userRepository, never()).save(any());
    }

    // ── register: validation rules ───────────────────────────────────────────

    @Test
    void register_usernameTooShort_returnsBadRequest() {
        ResponseEntity<?> response = controller.register(registerRequest("ab", "alice@test.com", "password1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().toString()).contains("3");
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_passwordTooShort_returnsBadRequest() {
        ResponseEntity<?> response = controller.register(registerRequest("alice", "alice@test.com", "short"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().toString()).contains("8");
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_invalidEmail_returnsBadRequest() {
        ResponseEntity<?> response = controller.register(registerRequest("alice", "not-an-email", "password1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().toString()).contains("email");
        verify(userRepository, never()).save(any());
    }

    // ── register: credential conflict returns generic message (no enumeration) ─

    @Test
    void register_usernameTaken_returns400WithGenericMessage() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(mockUser("alice", "other@test.com")));

        ResponseEntity<?> response = controller.register(registerRequest("alice", "alice@test.com", "password1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // Must NOT reveal which field caused the conflict
        assertThat(response.getBody().toString()).doesNotContain("username");
        assertThat(response.getBody().toString()).doesNotContain("taken");
        assertThat(response.getBody().toString()).contains("Registration failed");
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_emailTaken_returns400WithSameGenericMessage() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(mockUser("other", "alice@test.com")));

        ResponseEntity<?> response = controller.register(registerRequest("alice", "alice@test.com", "password1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().toString()).contains("Registration failed");
        verify(userRepository, never()).save(any());
    }

    // ── login: valid credentials → HttpOnly cookies + user info body ─────────

    @Test
    void login_validCredentials_setsHttpOnlyCookiesAndReturnsUserInfo() {
        setExpirations(900_000L, 604_800_000L);
        User user = mockUser("alice", "alice@test.com");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
            .thenReturn(mock(org.springframework.security.core.Authentication.class));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(user)).thenReturn("access-jwt");
        when(jwtUtil.generateRefreshToken(user)).thenReturn("refresh-jwt");

        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        ResponseEntity<?> response = controller.login(loginRequest("alice", "pass123"), httpResponse);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AuthResponse body = (AuthResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getUsername()).isEqualTo("alice");
        assertThat(body.getEmail()).isEqualTo("alice@test.com");
        assertThat(body.getId()).isEqualTo(user.getId());
        assertThat(body.getRoles()).contains("CONTRIBUTOR");

        // Verify HttpOnly cookies were added (not raw token in the body)
        verify(httpResponse, times(2)).addCookie(any());
    }

    // ── login: wrong password ─────────────────────────────────────────────────

    @Test
    void login_wrongPassword_throwsBadCredentialsException() {
        when(authenticationManager.authenticate(any()))
            .thenThrow(new BadCredentialsException("Bad credentials"));

        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        assertThrows(BadCredentialsException.class,
            () -> controller.login(loginRequest("alice", "wrong"), httpResponse));

        verify(userRepository, never()).findByUsername(anyString());
    }

    // ── login: user not found after authentication ────────────────────────────

    @Test
    void login_userNotFoundAfterAuthentication_throwsException() {
        when(authenticationManager.authenticate(any()))
            .thenReturn(mock(org.springframework.security.core.Authentication.class));
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        assertThrows(Exception.class,
            () -> controller.login(loginRequest("ghost", "pass123"), httpResponse));
    }

    // ── logout: clears cookies ────────────────────────────────────────────────

    @Test
    void logout_clearsBothCookiesAndReturns200() {
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        ResponseEntity<?> response = controller.logout(httpResponse);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Two cookies cleared: access_token + refresh_token
        verify(httpResponse, times(2)).addCookie(any());
    }
}
