package com.thethirdlicense.controllers;

import com.stripe.exception.StripeException;
import com.thethirdlicense.exceptions.UnauthorizedException;
import com.thethirdlicense.security.UserPrincipal;
import com.thethirdlicense.services.TokenTopUpService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeTopUpControllerTest {

    @Mock
    private TokenTopUpService tokenTopUpService;

    @InjectMocks
    private StripeTopUpController controller;

    // ── helpers ─────────────────────────────────────────────────────────────

    private Authentication authFor(UUID userId) {
        UserPrincipal principal = new UserPrincipal(
            userId, "testuser", "pass", "user@test.com", Collections.emptyList());
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        return auth;
    }

    private Authentication invalidAuth() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn("not-a-user-principal");
        return auth;
    }

    // ── createTopUp ─────────────────────────────────────────────────────────

    @Test
    void createTopUp_validUser_returns200WithCheckoutResponse() throws StripeException {
        UUID userId = UUID.randomUUID();
        StripeCheckoutResponse mockResponse =
            new StripeCheckoutResponse("cs_test_001", "https://checkout.stripe.com/test");
        when(tokenTopUpService.createTopUpOrder(eq(userId), eq(10.0), anyString(), anyString()))
            .thenReturn(mockResponse);

        ResponseEntity<StripeCheckoutResponse> result =
            controller.createTopUp(10.0, "http://success", "http://cancel", authFor(userId));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getSessionId()).isEqualTo("cs_test_001");
        assertThat(result.getBody().getCheckoutUrl()).isEqualTo("https://checkout.stripe.com/test");
        verify(tokenTopUpService).createTopUpOrder(eq(userId), eq(10.0), anyString(), anyString());
    }

    @Test
    void createTopUp_invalidPrincipal_throwsUnauthorizedException() {
        assertThrows(UnauthorizedException.class,
            () -> controller.createTopUp(10.0, "http://success", "http://cancel", invalidAuth()));
    }

    // ── captureTopUp ────────────────────────────────────────────────────────

    @Test
    void captureTopUp_validUser_returns200WithMessage() throws StripeException {
        UUID userId = UUID.randomUUID();
        doNothing().when(tokenTopUpService).captureTopUp(userId, "cs_test_789");

        ResponseEntity<String> result = controller.captureTopUp("cs_test_789", authFor(userId));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo("Top-up captured and tokens credited.");
        verify(tokenTopUpService).captureTopUp(userId, "cs_test_789");
    }

    @Test
    void captureTopUp_invalidPrincipal_throwsUnauthorizedException() {
        assertThrows(UnauthorizedException.class,
            () -> controller.captureTopUp("cs_test", invalidAuth()));
    }

    @Test
    void captureTopUp_serviceThrowsStripeException_propagates() throws StripeException {
        UUID userId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new StripeException("Stripe error", "req_test", "stripe_error", 500, null) {})
            .when(tokenTopUpService).captureTopUp(any(), anyString());

        assertThrows(StripeException.class,
            () -> controller.captureTopUp("cs_fail", authFor(userId)));
    }
}
