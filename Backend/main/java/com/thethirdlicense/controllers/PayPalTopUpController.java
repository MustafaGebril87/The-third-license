package com.thethirdlicense.controllers;

import java.io.IOException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.thethirdlicense.exceptions.UnauthorizedException;
import com.thethirdlicense.security.UserPrincipal;
import com.thethirdlicense.services.TokenTopUpService;

@RestController
@RequestMapping("/api/paypal")
public class PayPalTopUpController {

    private final TokenTopUpService tokenTopUpService;

    @Autowired
    public PayPalTopUpController(TokenTopUpService tokenTopUpService) {
        this.tokenTopUpService = tokenTopUpService;
    }

    @PostMapping("/topup/create")
    public ResponseEntity<PayPalCreateOrderResponse> createTopUp(
            @RequestParam double coinAmount,
            @RequestParam String returnUrl,
            @RequestParam String cancelUrl,
            Authentication authentication
    ) throws IOException {

        if (!(authentication.getPrincipal() instanceof UserPrincipal)) {
            throw new UnauthorizedException("Unauthorized");
        }

        UUID userId = ((UserPrincipal) authentication.getPrincipal()).getId();
        PayPalCreateOrderResponse res = tokenTopUpService.createTopUpOrder(userId, coinAmount, returnUrl, cancelUrl);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/topup/capture")
    public ResponseEntity<String> captureTopUp(
            @RequestParam String orderId,
            Authentication authentication
    ) throws IOException {
        if (!(authentication.getPrincipal() instanceof UserPrincipal)) {
            throw new UnauthorizedException("Unauthorized");
        }
        UUID userId = ((UserPrincipal) authentication.getPrincipal()).getId();

        tokenTopUpService.captureTopUp(userId, orderId);
        return ResponseEntity.ok("Top-up captured and tokens credited.");
    }

}
