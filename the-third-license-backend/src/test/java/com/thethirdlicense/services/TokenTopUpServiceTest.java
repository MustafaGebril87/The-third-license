package com.thethirdlicense.services;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.thethirdlicense.controllers.StripeCheckoutResponse;
import com.thethirdlicense.models.StripeTopUp;
import com.thethirdlicense.repositories.StripeTopUpRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenTopUpServiceTest {

    @Mock
    private StripeService stripeService;

    @Mock
    private TokenService tokenService;

    @Mock
    private StripeTopUpRepository topUpRepository;

    @InjectMocks
    private TokenTopUpService tokenTopUpService;

    // ── createTopUpOrder ────────────────────────────────────────────────────

    @Test
    void createTopUpOrder_zeroAmount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> tokenTopUpService.createTopUpOrder(UUID.randomUUID(), 0.0, "http://success", "http://cancel"));
    }

    @Test
    void createTopUpOrder_negativeAmount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> tokenTopUpService.createTopUpOrder(UUID.randomUUID(), -10.0, "http://success", "http://cancel"));
    }

    @Test
    void createTopUpOrder_validAmount_savesRecordAndReturnsCheckoutResponse() throws StripeException {
        UUID userId = UUID.randomUUID();
        StripeCheckoutResponse mockCheckout =
            new StripeCheckoutResponse("cs_test_123", "https://checkout.stripe.com/test");

        when(stripeService.createCheckoutSession(eq(10.0), anyString(), anyString()))
            .thenReturn(mockCheckout);
        when(topUpRepository.save(any(StripeTopUp.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        StripeCheckoutResponse result =
            tokenTopUpService.createTopUpOrder(userId, 10.0, "http://success", "http://cancel");

        // return value
        assertThat(result.getSessionId()).isEqualTo("cs_test_123");
        assertThat(result.getCheckoutUrl()).isEqualTo("https://checkout.stripe.com/test");

        // verify persisted entity
        ArgumentCaptor<StripeTopUp> captor = ArgumentCaptor.forClass(StripeTopUp.class);
        verify(topUpRepository).save(captor.capture());
        StripeTopUp saved = captor.getValue();

        assertThat(saved.getStripeSessionId()).isEqualTo("cs_test_123");
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getCoinAmount()).isEqualTo(10.0);
        assertThat(saved.getFee()).isCloseTo(0.10, within(0.001));
        assertThat(saved.getTotalUsd()).isCloseTo(10.10, within(0.001));
        assertThat(saved.getStatus()).isEqualTo(StripeTopUp.Status.CREATED);
    }

    // ── captureTopUp ────────────────────────────────────────────────────────

    @Test
    void captureTopUp_completedAndPaidSession_creditsCorrectCoins() throws StripeException {
        UUID userId = UUID.randomUUID();
        Session mockSession = mock(Session.class);
        when(mockSession.getStatus()).thenReturn("complete");
        when(mockSession.getPaymentStatus()).thenReturn("paid");
        when(mockSession.getAmountTotal()).thenReturn(1000L); // $10.00 in cents
        when(stripeService.retrieveSession("cs_test_456")).thenReturn(mockSession);

        tokenTopUpService.captureTopUp(userId, "cs_test_456");

        // $10.00 * 10 coins/USD = 100 coins
        verify(tokenService).generateTokenForUser(userId, 100.0);
    }

    @Test
    void captureTopUp_statusNotComplete_throwsIllegalStateException() throws StripeException {
        Session mockSession = mock(Session.class);
        when(mockSession.getStatus()).thenReturn("open");
        when(stripeService.retrieveSession("cs_incomplete")).thenReturn(mockSession);

        assertThrows(IllegalStateException.class,
            () -> tokenTopUpService.captureTopUp(UUID.randomUUID(), "cs_incomplete"));
    }

    @Test
    void captureTopUp_statusCompleteButUnpaid_throwsIllegalStateException() throws StripeException {
        Session mockSession = mock(Session.class);
        when(mockSession.getStatus()).thenReturn("complete");
        when(mockSession.getPaymentStatus()).thenReturn("unpaid");
        when(stripeService.retrieveSession("cs_partial")).thenReturn(mockSession);

        assertThrows(IllegalStateException.class,
            () -> tokenTopUpService.captureTopUp(UUID.randomUUID(), "cs_partial"));
    }

}
