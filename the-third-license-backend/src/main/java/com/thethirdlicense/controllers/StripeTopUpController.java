package com.thethirdlicense.controllers;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.stripe.exception.StripeException;
import com.thethirdlicense.exceptions.UnauthorizedException;
import com.thethirdlicense.security.UserPrincipal;
import com.thethirdlicense.services.TokenTopUpService;

@RestController
@RequestMapping("/api/stripe")
public class StripeTopUpController {

    private final TokenTopUpService tokenTopUpService;

    @Autowired
    public StripeTopUpController(TokenTopUpService tokenTopUpService) {
        this.tokenTopUpService = tokenTopUpService;
    }

    @PostMapping("/topup/create")
    public ResponseEntity<StripeCheckoutResponse> createTopUp(
            @RequestParam double coinAmount,
            @RequestParam String successUrl,
            @RequestParam String cancelUrl,
            Authentication authentication
    ) throws StripeException {
        if (!(authentication.getPrincipal() instanceof UserPrincipal)) {
            throw new UnauthorizedException("Unauthorized");
        }
        UUID userId = ((UserPrincipal) authentication.getPrincipal()).getId();
        StripeCheckoutResponse res = tokenTopUpService.createTopUpOrder(userId, coinAmount, successUrl, cancelUrl);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/topup/capture")
    public ResponseEntity<String> captureTopUp(
            @RequestParam String sessionId,
            Authentication authentication
    ) throws StripeException {
        if (!(authentication.getPrincipal() instanceof UserPrincipal)) {
            throw new UnauthorizedException("Unauthorized");
        }
        UUID userId = ((UserPrincipal) authentication.getPrincipal()).getId();
        tokenTopUpService.captureTopUp(userId, sessionId);
        return ResponseEntity.ok("Top-up captured and tokens credited.");
    }
}
