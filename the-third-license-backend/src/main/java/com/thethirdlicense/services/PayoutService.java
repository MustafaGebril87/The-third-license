package com.thethirdlicense.services;

import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.thethirdlicense.models.User;
import com.thethirdlicense.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Seller payouts via Stripe Connect Express accounts. Stripe hosts onboarding (identity, bank
 * details), so the platform never handles that data. A user can list shares for sale only once
 * their account can receive payouts.
 */
@Service
public class PayoutService {

    private final StripeService stripeService;
    private final UserRepository userRepository;

    @Value("${app.cors.allowed-origin}")
    private String frontendOrigin;

    public PayoutService(StripeService stripeService, UserRepository userRepository) {
        this.stripeService = stripeService;
        this.userRepository = userRepository;
    }

    /** Creates the user's connected account if needed and returns a Stripe-hosted onboarding URL. */
    public String startOnboarding(User user) throws StripeException {
        if (user.getStripeAccountId() == null) {
            Account account = stripeService.createExpressAccount(user.getEmail());
            user.setStripeAccountId(account.getId());
            userRepository.save(user);
        }
        String origin = origin();
        return stripeService.createOnboardingLink(user.getStripeAccountId(),
                origin + "/payouts/refresh", origin + "/payouts/return");
    }

    /** Re-reads the account from Stripe and caches whether payouts are enabled. */
    public Map<String, Object> refreshStatus(User user) throws StripeException {
        if (user.getStripeAccountId() == null) {
            return Map.of("connected", false, "payoutsEnabled", false, "detailsSubmitted", false);
        }
        Account account = stripeService.retrieveAccount(user.getStripeAccountId());
        applyAccount(user, account);
        return Map.of(
                "connected", true,
                "payoutsEnabled", user.isPayoutsEnabled(),
                "detailsSubmitted", Boolean.TRUE.equals(account.getDetailsSubmitted()));
    }

    public String dashboardLink(User user) throws StripeException {
        if (user.getStripeAccountId() == null) {
            throw new IllegalStateException("Set up payouts first.");
        }
        return stripeService.createDashboardLink(user.getStripeAccountId());
    }

    /** Webhook: account.updated. */
    public void handleAccountUpdated(String accountId) throws StripeException {
        User user = userRepository.findByStripeAccountId(accountId).orElse(null);
        if (user == null) return; // not one of ours
        applyAccount(user, stripeService.retrieveAccount(accountId));
    }

    private void applyAccount(User user, Account account) {
        boolean enabled = StripeService.canReceivePayouts(account);
        if (user.isPayoutsEnabled() != enabled) {
            user.setPayoutsEnabled(enabled);
            userRepository.save(user);
        }
    }

    private String origin() {
        return frontendOrigin.endsWith("/") ? frontendOrigin.substring(0, frontendOrigin.length() - 1) : frontendOrigin;
    }
}
