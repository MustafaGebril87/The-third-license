package com.thethirdlicense.controllers;

import com.thethirdlicense.models.User;
import com.thethirdlicense.repositories.UserRepository;
import com.thethirdlicense.requests.LoginRequest;
import com.thethirdlicense.requests.RegisterRequest;
import com.thethirdlicense.responses.AuthResponse;
import com.thethirdlicense.security.JWTUtil;
import com.thethirdlicense.services.LoginAttemptService;
import com.thethirdlicense.services.RevokedTokenService;
import com.thethirdlicense.services.UserService;
import com.thethirdlicense.exceptions.TooManyAttemptsException;
import com.thethirdlicense.exceptions.UserNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    static final String ACCESS_COOKIE = "access_token";
    static final String REFRESH_COOKIE = "refresh_token";

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JWTUtil jwtUtil;
    private final RevokedTokenService revokedTokenService;
    private final LoginAttemptService loginAttemptService;

    @Value("${security.jwt.expiration-ms:900000}")
    private long accessTokenExpirationMs;

    @Value("${security.jwt.refresh-expiration-ms:604800000}")
    private long refreshTokenExpirationMs;

    /** true in production (HTTPS). Browsers also accept Secure cookies on http://localhost. */
    @Value("${app.cookie.secure:true}")
    private boolean secureCookies;

    @Autowired
    public AuthController(UserRepository userRepository, UserService userService,
                          PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
                          JWTUtil jwtUtil, RevokedTokenService revokedTokenService,
                          LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.revokedTokenService = revokedTokenService;
        this.loginAttemptService = loginAttemptService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (isBlank(request.getUsername()) || isBlank(request.getPassword()) || isBlank(request.getEmail())) {
            return ResponseEntity.badRequest().body("Username, password, and email are required.");
        }
        if (request.getUsername().length() < 3 || request.getUsername().length() > 50) {
            return ResponseEntity.badRequest().body("Username must be 3–50 characters.");
        }
        // Usernames become part of directory names for Git working copies
        if (!request.getUsername().matches("^[A-Za-z0-9][A-Za-z0-9._-]{2,49}$") || request.getUsername().contains("..")) {
            return ResponseEntity.badRequest().body("Username may only contain letters, digits, '.', '_' and '-'.");
        }
        if (request.getPassword().length() < 8) {
            return ResponseEntity.badRequest().body("Password must be at least 8 characters.");
        }
        if (!request.getEmail().contains("@")) {
            return ResponseEntity.badRequest().body("Invalid email address.");
        }

        // Check both username and email to prevent enumeration — same generic message either way
        if (userRepository.findByUsername(request.getUsername()).isPresent()
                || userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body("Registration failed. Please try different credentials.");
        }

        User newUser = new User();
        newUser.setUsername(request.getUsername());
        newUser.setPassword(passwordEncoder.encode(request.getPassword()));
        newUser.setEmail(request.getEmail());

        userRepository.save(newUser);
        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest,
                                   HttpServletResponse response) {
        String ip = httpRequest != null ? httpRequest.getRemoteAddr() : null;
        if (loginAttemptService.isBlocked(request.getUsername(), ip)) {
            throw new TooManyAttemptsException("Too many failed login attempts. Try again in 15 minutes.");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        } catch (AuthenticationException e) {
            loginAttemptService.recordFailure(request.getUsername(), ip);
            throw e;
        }
        loginAttemptService.recordSuccess(request.getUsername(), ip);

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        issueTokens(user, response);
        return ResponseEntity.ok(new AuthResponse(user));
    }

    /** Exchanges a valid refresh_token cookie for a new access token (and rotates the refresh token). */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = readCookie(request, REFRESH_COOKIE).orElse(null);
        if (refreshToken == null || !jwtUtil.validateRefreshToken(refreshToken)
                || revokedTokenService.isRevoked(refreshToken)) {
            clearCookie(response, ACCESS_COOKIE);
            clearCookie(response, REFRESH_COOKIE);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Session expired. Please log in again.");
        }

        UUID userId = jwtUtil.getUserIdFromToken(refreshToken);
        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Session expired. Please log in again.");
        }

        revokedTokenService.revoke(refreshToken);
        issueTokens(user.get(), response);
        return ResponseEntity.ok(new AuthResponse(user.get()));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        // Revoke server-side so a copied token stops working immediately, not just at expiry
        readCookie(request, ACCESS_COOKIE).filter(jwtUtil::validateToken).ifPresent(revokedTokenService::revoke);
        readCookie(request, REFRESH_COOKIE).filter(jwtUtil::validateToken).ifPresent(revokedTokenService::revoke);

        clearCookie(response, ACCESS_COOKIE);
        clearCookie(response, REFRESH_COOKIE);
        return ResponseEntity.ok("Logged out successfully");
    }

    private void issueTokens(User user, HttpServletResponse response) {
        addCookie(response, ACCESS_COOKIE, jwtUtil.generateToken(user), accessTokenExpirationMs / 1000);
        addCookie(response, REFRESH_COOKIE, jwtUtil.generateRefreshToken(user), refreshTokenExpirationMs / 1000);
    }

    private Optional<String> readCookie(HttpServletRequest request, String name) {
        if (request == null || request.getCookies() == null) return Optional.empty();
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName()) && !isBlank(cookie.getValue())) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private void addCookie(HttpServletResponse response, String name, String value, long maxAgeSeconds) {
        // SameSite=Lax blocks cross-site POSTs (CSRF) while still sending the cookie on top-level
        // navigations back from Stripe. Frontend and API must be on the same site (e.g. app.x.com + api.x.com).
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearCookie(HttpServletResponse response, String name) {
        addCookie(response, name, "", 0);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
