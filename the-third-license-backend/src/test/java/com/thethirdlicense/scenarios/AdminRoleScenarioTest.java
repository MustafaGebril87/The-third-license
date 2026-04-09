package com.thethirdlicense.scenarios;

import com.thethirdlicense.models.Role;
import com.thethirdlicense.models.User;
import com.thethirdlicense.repositories.UserRepository;
import com.thethirdlicense.security.JWTUtil;
import com.thethirdlicense.services.TokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

/**
 * Scenario: Role-based access control
 *
 * Verifies that @PreAuthorize("hasRole('ADMIN')") is enforced end-to-end:
 *
 *   1. Regular user (no ADMIN role) → 403 Forbidden on admin endpoints
 *   2. ADMIN user → reaches the admin endpoint (200 or service-level error, not 403)
 *   3. Unauthenticated request → 401 Unauthorized (not 403)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminRoleScenarioTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private JWTUtil jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;

    // Admin endpoint calls TokenService.revokeToken — mock it so we control the outcome
    @MockBean private TokenService tokenService;

    private User regularUser;
    private User adminUser;
    private String regularJwt;
    private String adminJwt;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        regularUser = userRepository.save(new User(
                "regular_" + suffix, "regular_" + suffix + "@test.com",
                passwordEncoder.encode("strongpass1"), new HashSet<>()));
        regularJwt = jwtUtil.generateToken(regularUser);

        adminUser = userRepository.save(new User(
                "admin_" + suffix, "admin_" + suffix + "@test.com",
                passwordEncoder.encode("strongpass1"), Set.of(Role.ADMIN)));
        adminJwt = jwtUtil.generateToken(adminUser);
    }

    @AfterEach
    void tearDown() {
        userRepository.delete(regularUser);
        userRepository.delete(adminUser);
    }

    private HttpHeaders bearer(String jwt) {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, "Bearer " + jwt);
        return h;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 1: Regular user → 403 Forbidden
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void regularUser_cannotAccessAdminEndpoint_403() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/admin/currency/revoke?token=some-token",
                HttpMethod.POST,
                new HttpEntity<>(bearer(regularJwt)),
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 2: ADMIN user → reaches the endpoint (not 403)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void adminUser_canAccessAdminEndpoint_notForbidden() {
        doNothing().when(tokenService).revokeToken(anyString());

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/admin/currency/revoke?token=some-token",
                HttpMethod.POST,
                new HttpEntity<>(bearer(adminJwt)),
                String.class
        );
        // May return 200 (success) or 500 (service error), but NOT 403
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminUser_revokeToken_returns200() {
        doNothing().when(tokenService).revokeToken("valid-token-value");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/admin/currency/revoke?token=valid-token-value",
                HttpMethod.POST,
                new HttpEntity<>(bearer(adminJwt)),
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("revoked");
    }

    @Test
    void adminUser_revokeInvalidToken_propagatesServiceError() {
        doThrow(new IllegalArgumentException("Token not found"))
                .when(tokenService).revokeToken("bad-token");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/admin/currency/revoke?token=bad-token",
                HttpMethod.POST,
                new HttpEntity<>(bearer(adminJwt)),
                String.class
        );
        // Must reach the service layer (not blocked at auth) — returns 5xx, not 403
        assertThat(response.getStatusCode().is5xxServerError()).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 3: Unauthenticated → 401, not 403
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void unauthenticated_getsUnauthorizedNotForbidden() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/admin/currency/revoke?token=some-token",
                null,
                String.class
        );
        // 401 = not authenticated; 403 = authenticated but not authorized
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 4: JWT with ADMIN role grants access; JWT without does not
    //             (verifies token roles are actually read by AuthFilter)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void jwtRoleScope_adminTokenAllowed_regularTokenDenied() {
        doNothing().when(tokenService).revokeToken(anyString());

        // Admin JWT → OK
        ResponseEntity<String> adminResp = restTemplate.exchange(
                "/api/admin/currency/revoke?token=tok",
                HttpMethod.POST,
                new HttpEntity<>(bearer(adminJwt)),
                String.class
        );
        assertThat(adminResp.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);

        // Same endpoint, regular JWT → 403
        ResponseEntity<String> regularResp = restTemplate.exchange(
                "/api/admin/currency/revoke?token=tok",
                HttpMethod.POST,
                new HttpEntity<>(bearer(regularJwt)),
                String.class
        );
        assertThat(regularResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
