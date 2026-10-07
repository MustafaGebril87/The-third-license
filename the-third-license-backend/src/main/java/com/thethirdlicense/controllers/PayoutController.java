package com.thethirdlicense.controllers;

import com.stripe.exception.StripeException;
import com.thethirdlicense.exceptions.UnauthorizedException;
import com.thethirdlicense.models.User;
import com.thethirdlicense.security.UserPrincipal;
import com.thethirdlicense.services.PayoutService;
import com.thethirdlicense.services.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Seller payout setup (Stripe Connect Express). */
@RestController
@RequestMapping("/api/payouts")
public class PayoutController {

    private final PayoutService payoutService;
    private final UserService userService;

    public PayoutController(PayoutService payoutService, UserService userService) {
        this.payoutService = payoutService;
        this.userService = userService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(@AuthenticationPrincipal UserPrincipal principal) throws StripeException {
        return ResponseEntity.ok(payoutService.refreshStatus(currentUser(principal)));
    }

    /** Returns the Stripe-hosted onboarding URL to redirect the browser to. */
    @PostMapping("/onboard")
    public ResponseEntity<Map<String, String>> onboard(@AuthenticationPrincipal UserPrincipal principal) throws StripeException {
        return ResponseEntity.ok(Map.of("url", payoutService.startOnboarding(currentUser(principal))));
    }

    /** Returns a one-time login URL for the seller's Stripe Express dashboard. */
    @PostMapping("/dashboard")
    public ResponseEntity<Map<String, String>> dashboard(@AuthenticationPrincipal UserPrincipal principal) throws StripeException {
        return ResponseEntity.ok(Map.of("url", payoutService.dashboardLink(currentUser(principal))));
    }

    private User currentUser(UserPrincipal principal) {
        if (principal == null) throw new UnauthorizedException("Unauthorized");
        return userService.findById(principal.getId());
    }
}
