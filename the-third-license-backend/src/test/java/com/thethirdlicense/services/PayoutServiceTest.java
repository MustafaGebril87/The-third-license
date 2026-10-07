package com.thethirdlicense.services;

import com.stripe.model.Account;
import com.thethirdlicense.models.User;
import com.thethirdlicense.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayoutServiceTest {

    @Mock private StripeService stripeService;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private PayoutService payoutService;

    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(payoutService, "frontendOrigin", "http://localhost:5173/");
        user = new User("alice", "alice@test.com", "hashed", new HashSet<>());
        user.setId(UUID.randomUUID());
    }

    private static Account account(String id, boolean payouts, String transfers) {
        Account a = new Account();
        a.setId(id);
        a.setPayoutsEnabled(payouts);
        a.setDetailsSubmitted(payouts);
        Account.Capabilities caps = new Account.Capabilities();
        caps.setTransfers(transfers);
        a.setCapabilities(caps);
        return a;
    }

    @Test
    void startOnboarding_createsAccountOnce_andReturnsStripeLink() throws Exception {
        when(stripeService.createExpressAccount("alice@test.com")).thenReturn(account("acct_new", false, "inactive"));
        when(stripeService.createOnboardingLink("acct_new", "http://localhost:5173/payouts/refresh",
                "http://localhost:5173/payouts/return")).thenReturn("https://connect.stripe.com/setup/x");

        String url = payoutService.startOnboarding(user);
        payoutService.startOnboarding(user); // second time: reuse the same account

        assertThat(url).isEqualTo("https://connect.stripe.com/setup/x");
        assertThat(user.getStripeAccountId()).isEqualTo("acct_new");
        verify(stripeService, times(1)).createExpressAccount(anyString());
    }

    @Test
    void refreshStatus_enablesPayoutsOnlyWhenTransfersActive() throws Exception {
        user.setStripeAccountId("acct_1");
        when(stripeService.retrieveAccount("acct_1")).thenReturn(account("acct_1", true, "pending"));

        Map<String, Object> pending = payoutService.refreshStatus(user);
        assertThat(pending.get("payoutsEnabled")).isEqualTo(false);

        when(stripeService.retrieveAccount("acct_1")).thenReturn(account("acct_1", true, "active"));
        Map<String, Object> active = payoutService.refreshStatus(user);
        assertThat(active.get("payoutsEnabled")).isEqualTo(true);
        assertThat(user.isPayoutsEnabled()).isTrue();
    }

    @Test
    void refreshStatus_noAccount_reportsNotConnected() throws Exception {
        assertThat(payoutService.refreshStatus(user).get("connected")).isEqualTo(false);
        verifyNoInteractions(stripeService);
    }

    @Test
    void accountUpdatedWebhook_disablesPayoutsWhenStripeRestrictsAccount() throws Exception {
        user.setStripeAccountId("acct_1");
        user.setPayoutsEnabled(true);
        when(userRepository.findByStripeAccountId("acct_1")).thenReturn(Optional.of(user));
        when(stripeService.retrieveAccount("acct_1")).thenReturn(account("acct_1", false, "inactive"));

        payoutService.handleAccountUpdated("acct_1");

        assertThat(user.isPayoutsEnabled()).isFalse();
        verify(userRepository).save(user);
    }
}
