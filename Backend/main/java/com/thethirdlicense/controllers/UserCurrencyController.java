package com.thethirdlicense.controllers;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thethirdlicense.Util.BalanceResponse;
import com.thethirdlicense.Util.TransferRequest;
import com.thethirdlicense.Util.WithdrawRequest;
import com.thethirdlicense.models.User;
import com.thethirdlicense.repositories.UserRepository;
import com.thethirdlicense.security.UserPrincipal;
import com.thethirdlicense.services.TokenService;

import org.springframework.http.MediaType;

@RestController
@RequestMapping("/api/currency")
public class UserCurrencyController {

    private final TokenService currencyService;
    private final UserRepository userRepository;


    @Autowired
    public UserCurrencyController(UserRepository userRepository, TokenService currencyService) {
        this.userRepository = userRepository;
        this.currencyService = currencyService;
    }
    @GetMapping(value = "/balance", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BalanceResponse> getBalance(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        UUID userId = userPrincipal.getId();
        double balance = currencyService.getUserCurrencyBalance(userId);  // <- call correct method
        return ResponseEntity.ok(new BalanceResponse(balance));
    }



    // Withdraw currency (convert to real money)
    @PostMapping("/withdraw")
    public ResponseEntity<String> withdraw(@AuthenticationPrincipal User user, @RequestBody WithdrawRequest request) {
        currencyService.withdraw(user.getId(), request.getAmount());
        return ResponseEntity.ok("Withdrawal request submitted.");
    }

    // Transfer currency to another user
    @PostMapping("/transfer")
    public ResponseEntity<String> transferCurrency(@AuthenticationPrincipal User sender, @RequestBody TransferRequest request) {
        currencyService.transferCurrency(sender.getId(), request.getRecipientId(), request.getAmount());
        return ResponseEntity.ok("Transfer successful.");
    }
}
