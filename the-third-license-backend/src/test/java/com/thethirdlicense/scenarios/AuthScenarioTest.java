package com.thethirdlicense.scenarios;

import com.thethirdlicense.models.User;
import com.thethirdlicense.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario: Full authentication lifecycle
 *
 * 1. New user registers
 * 2. User logs in → server sets HttpOnly cookies, returns user info (not tokens)
 * 3. User accesses a protected endpoint with the cookie → 200
 * 4. User accesses a protected endpoint without any auth → 401
 * 5. User logs out → cookies cleared (Max-Age=0)
 * 6. Wrong password → 401
 * 7. Duplicate registration → 400 (generic message, no enumeration)
 * 8. Input validation → 400
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthScenarioTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;

    private String uniqueSuffix;

    @BeforeEach
    void setUp() {
        uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void tearDown() {
        userRepository.findByUsername("scenario_" + uniqueSuffix)
                .ifPresent(userRepository::delete);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String username() { return "scenario_" + uniqueSuffix; }
    private String email()    { return "scenario_" + uniqueSuffix + "@test.com"; }

    private Map<String, String> registerBody() {
        return Map.of("username", username(), "email", email(), "password", "strongpass1");
    }

    /** Extracts cookie value from Set-Cookie header. */
    private String extractCookie(ResponseEntity<?> response, String name) {
        return response.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of()).stream()
                .filter(c -> c.startsWith(name + "="))
                .map(c -> c.split(";")[0].substring(name.length() + 1))
                .findFirst().orElse(null);
    }

    private HttpHeaders cookieHeader(String name, String value) {
        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, name + "=" + value);
        return h;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 1: Register → Login → access protected endpoint → logout
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void fullAuthFlow_registerLoginAccessLogout() {
        // Step 1: Register
        ResponseEntity<String> reg = restTemplate.postForEntity(
                "/api/auth/register", registerBody(), String.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reg.getBody()).contains("registered");

        // Verify user is persisted in DB
        Optional<User> saved = userRepository.findByUsername(username());
        assertThat(saved).isPresent();
        assertThat(saved.get().getEmail()).isEqualTo(email());

        // Step 2: Login → should set HttpOnly cookies, return user info (NOT raw tokens)
        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/api/auth/login",
                Map.of("username", username(), "password", "strongpass1"),
                Map.class
        );
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<?, ?> body = login.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("username")).isEqualTo(username());
        assertThat(body.get("email")).isEqualTo(email());
        assertThat(body.get("id")).isNotNull();
        // Tokens must NOT appear in the response body
        assertThat(body.containsKey("accessToken")).isFalse();
        assertThat(body.containsKey("refreshToken")).isFalse();

        // Verify access_token cookie is HttpOnly
        String accessCookie = extractCookie(login, "access_token");
        assertThat(accessCookie).isNotNull().isNotBlank();
        String rawSetCookie = login.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of())
                .stream().filter(c -> c.startsWith("access_token=")).findFirst().orElse("");
        assertThat(rawSetCookie.toLowerCase()).contains("httponly");

        // Step 3: Access a protected endpoint using the cookie
        HttpEntity<?> withCookie = new HttpEntity<>(cookieHeader("access_token", accessCookie));
        ResponseEntity<String> protectedCall = restTemplate.exchange(
                "/api/companies/all", HttpMethod.GET, withCookie, String.class);
        assertThat(protectedCall.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Step 4: Logout → server clears the cookie
        ResponseEntity<String> logout = restTemplate.exchange(
                "/api/auth/logout", HttpMethod.POST, withCookie, String.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify Set-Cookie instructs browser to delete access_token (Max-Age=0)
        String clearedCookie = logout.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of())
                .stream().filter(c -> c.startsWith("access_token=")).findFirst().orElse("");
        assertThat(clearedCookie).contains("Max-Age=0");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 2: No auth → 401
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void unauthenticatedRequest_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/companies/my-companies", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 3: Wrong password → 401
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void login_wrongPassword_returns401() {
        // Register first
        restTemplate.postForEntity("/api/auth/register", registerBody(), String.class);

        // Serialise to String so Content-Length is known and SimpleClientHttpRequestFactory
        // doesn't use chunked/streaming mode — which would trigger HttpRetryException on 401.
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        String jsonBody = String.format(
                "{\"username\":\"%s\",\"password\":\"wrongpassword\"}", username());
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(jsonBody, jsonHeaders),
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Ensure no auth cookie is set
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 4: Duplicate registration → generic 400 (no enumeration)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void register_duplicateCredentials_returns400WithGenericMessage() {
        restTemplate.postForEntity("/api/auth/register", registerBody(), String.class);

        ResponseEntity<String> duplicate = restTemplate.postForEntity(
                "/api/auth/register", registerBody(), String.class);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String respBody = duplicate.getBody();
        // Must NOT reveal which field caused the conflict (user enumeration prevention)
        assertThat(respBody).doesNotContainIgnoringCase("username");
        assertThat(respBody).doesNotContainIgnoringCase("taken");
        assertThat(respBody).contains("Registration failed");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 5: Validation rules enforced
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void register_shortPassword_returns400() {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/register",
                Map.of("username", "validuser", "email", "valid@test.com", "password", "short"),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("8");
    }

    @Test
    void register_invalidEmail_returns400() {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/register",
                Map.of("username", "validuser", "email", "not-an-email", "password", "strongpass1"),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void register_usernameTooShort_returns400() {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/register",
                Map.of("username", "ab", "email", "valid@test.com", "password", "strongpass1"),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Security regressions: token types, revocation, /me
    // ─────────────────────────────────────────────────────────────────────────

    private ResponseEntity<Map> registerAndLogin() {
        restTemplate.postForEntity("/api/auth/register", registerBody(), String.class);
        return restTemplate.postForEntity("/api/auth/login",
                Map.of("username", username(), "password", "strongpass1"), Map.class);
    }

    @Test
    void refreshToken_cannotBeUsedAsAccessToken() {
        ResponseEntity<Map> login = registerAndLogin();
        String refresh = extractCookie(login, "refresh_token");

        HttpHeaders bearer = new HttpHeaders();
        bearer.setBearerAuth(refresh);
        ResponseEntity<String> call = restTemplate.exchange(
                "/api/companies/all", HttpMethod.GET, new HttpEntity<>(bearer), String.class);
        assertThat(call.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> viaCookie = restTemplate.exchange(
                "/api/companies/all", HttpMethod.GET, new HttpEntity<>(cookieHeader("access_token", refresh)), String.class);
        assertThat(viaCookie.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void afterLogout_oldAccessTokenIsRejected() {
        ResponseEntity<Map> login = registerAndLogin();
        String access = extractCookie(login, "access_token");
        HttpEntity<?> withCookie = new HttpEntity<>(cookieHeader("access_token", access));

        restTemplate.exchange("/api/auth/logout", HttpMethod.POST, withCookie, String.class);

        ResponseEntity<String> reuse = restTemplate.exchange(
                "/api/companies/all", HttpMethod.GET, withCookie, String.class);
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refresh_issuesNewAccessToken_andOldRefreshTokenCannotBeReused() {
        ResponseEntity<Map> login = registerAndLogin();
        String refresh = extractCookie(login, "refresh_token");
        HttpEntity<?> withRefresh = new HttpEntity<>(cookieHeader("refresh_token", refresh));

        ResponseEntity<Map> refreshed = restTemplate.exchange("/api/auth/refresh", HttpMethod.POST, withRefresh, Map.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        String newAccess = extractCookie(refreshed, "access_token");
        assertThat(newAccess).isNotBlank();

        ResponseEntity<String> me = restTemplate.exchange("/api/users/me", HttpMethod.GET,
                new HttpEntity<>(cookieHeader("access_token", newAccess)), String.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).contains(username()).doesNotContain("password");

        // Rotated: the original refresh token is now revoked
        ResponseEntity<String> again = restTemplate.exchange("/api/auth/refresh", HttpMethod.POST, withRefresh, String.class);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void regularUser_cannotListUsers_orGrantThemselvesAdmin() {
        ResponseEntity<Map> login = registerAndLogin();
        String access = extractCookie(login, "access_token");
        String id = String.valueOf(login.getBody().get("id"));
        HttpHeaders h = cookieHeader("access_token", access);

        ResponseEntity<String> list = restTemplate.exchange("/api/users", HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        ResponseEntity<String> grant = restTemplate.exchange("/api/users/" + id + "/roles", HttpMethod.POST,
                new HttpEntity<>("\"ADMIN\"", h), String.class);
        assertThat(grant.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(userRepository.findByUsername(username()).orElseThrow().getRoles())
                .doesNotContain(com.thethirdlicense.models.Role.ADMIN);
    }
}
