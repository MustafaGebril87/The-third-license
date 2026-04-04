package com.thethirdlicense.integration;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.thethirdlicense.controllers.StripeCheckoutResponse;
import com.thethirdlicense.models.StripeTopUp;
import com.thethirdlicense.models.Token;
import com.thethirdlicense.models.User;
import com.thethirdlicense.repositories.StripeTopUpRepository;
import com.thethirdlicense.repositories.TokenRepository;
import com.thethirdlicense.repositories.UserRepository;
import com.thethirdlicense.security.JWTUtil;
import com.thethirdlicense.services.StripeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StripeTopUpIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StripeTopUpRepository stripeTopUpRepository;

    @Autowired
    private TokenRepository tokenRepository;

    @Autowired
    private JWTUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private StripeService stripeService;

    private User testUser;
    private String jwtToken;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        testUser = new User(
            "integtest_" + suffix,
            "integtest_" + suffix + "@test.com",
            passwordEncoder.encode("pass123"),
            new HashSet<>()
        );
        testUser = userRepository.save(testUser);
        jwtToken = jwtUtil.generateToken(testUser);
    }

    @AfterEach
    void tearDown() {
        // tokens have FK → users, must go first
        tokenRepository.deleteAll(tokenRepository.findByOwner(testUser));
        // stripe_topups only has a plain UUID column, no FK constraint
        List<StripeTopUp> topUps = stripeTopUpRepository.findAll().stream()
            .filter(t -> t.getUserId().equals(testUser.getId()))
            .collect(Collectors.toList());
        stripeTopUpRepository.deleteAll(topUps);
        // delete user last (cascades refreshTokens, ownedCompanies)
        userRepository.delete(testUser);
    }

    private HttpHeaders bearerHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + jwtToken);
        return headers;
    }

    // ── POST /api/stripe/topup/create ────────────────────────────────────────

    @Test
    void createTopUp_validJwt_returns200AndPersistsRecord() throws StripeException {
        when(stripeService.createCheckoutSession(eq(10.0), anyString(), anyString()))
            .thenReturn(new StripeCheckoutResponse("cs_integ_create", "https://checkout.stripe.com/integ"));

        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/stripe/topup/create?coinAmount=10.0&successUrl=http://localhost/ok&cancelUrl=http://localhost/cancel",
            HttpMethod.POST,
            new HttpEntity<>(bearerHeaders()),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("sessionId");
        assertThat(response.getBody().get("sessionId")).isEqualTo("cs_integ_create");
        assertThat(response.getBody().get("checkoutUrl")).isEqualTo("https://checkout.stripe.com/integ");

        // verify DB record
        Optional<StripeTopUp> saved = stripeTopUpRepository.findByStripeSessionId("cs_integ_create");
        assertThat(saved).isPresent();
        StripeTopUp topUp = saved.get();
        assertThat(topUp.getUserId()).isEqualTo(testUser.getId());
        assertThat(topUp.getCoinAmount()).isEqualTo(10.0);
        assertThat(topUp.getFee()).isCloseTo(0.10, within(0.001));
        assertThat(topUp.getTotalUsd()).isCloseTo(10.10, within(0.001));
        assertThat(topUp.getStatus()).isEqualTo(StripeTopUp.Status.CREATED);
    }

    @Test
    void createTopUp_noJwt_returns4xx() {
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/stripe/topup/create?coinAmount=10.0&successUrl=http://localhost/ok&cancelUrl=http://localhost/cancel",
            null,
            String.class
        );

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void createTopUp_zeroCoinAmount_returns500() throws StripeException {
        when(stripeService.createCheckoutSession(eq(0.0), anyString(), anyString()))
            .thenThrow(new IllegalArgumentException("Invalid coin amount"));

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/stripe/topup/create?coinAmount=0.0&successUrl=http://localhost/ok&cancelUrl=http://localhost/cancel",
            HttpMethod.POST,
            new HttpEntity<>(bearerHeaders()),
            String.class
        );

        // IllegalArgumentException from the service bubbles up as 500
        assertThat(response.getStatusCode().is5xxServerError()).isTrue();
    }

    // ── POST /api/stripe/topup/capture ───────────────────────────────────────

    @Test
    void captureTopUp_completedSession_creditsTokensAndReturns200() throws StripeException {
        Session mockSession = mock(Session.class);
        when(mockSession.getStatus()).thenReturn("complete");
        when(mockSession.getPaymentStatus()).thenReturn("paid");
        when(mockSession.getAmountTotal()).thenReturn(1000L); // $10.00 in cents
        when(stripeService.retrieveSession("cs_integ_capture")).thenReturn(mockSession);

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/stripe/topup/capture?sessionId=cs_integ_capture",
            HttpMethod.POST,
            new HttpEntity<>(bearerHeaders()),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("Top-up captured and tokens credited.");

        // verify Token persisted in DB: $10.00 * 10 coins/USD = 100 coins
        List<Token> tokens = tokenRepository.findByOwner(testUser);
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getAmount()).isCloseTo(100.0, within(0.001));
        assertThat(tokens.get(0).isRevoked()).isFalse();
        assertThat(tokens.get(0).isUsed()).isFalse();
    }

    @Test
    void captureTopUp_incompleteSession_returns500() throws StripeException {
        Session mockSession = mock(Session.class);
        when(mockSession.getStatus()).thenReturn("open");
        when(stripeService.retrieveSession("cs_integ_incomplete")).thenReturn(mockSession);

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/stripe/topup/capture?sessionId=cs_integ_incomplete",
            HttpMethod.POST,
            new HttpEntity<>(bearerHeaders()),
            String.class
        );

        assertThat(response.getStatusCode().is5xxServerError()).isTrue();
        // no token should be created
        assertThat(tokenRepository.findByOwner(testUser)).isEmpty();
    }

    @Test
    void captureTopUp_noJwt_returns4xx() {
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/stripe/topup/capture?sessionId=cs_integ_noauth",
            null,
            String.class
        );

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }
}
