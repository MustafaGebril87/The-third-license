package com.thethirdlicense.controllers;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.thethirdlicense.services.TokenService;
import com.thethirdlicense.models.CurrencyTransaction;
@RestController
@RequestMapping("/api/admin/currency")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCurrencyController {

    private final TokenService currencyService;

    @Autowired
    public AdminCurrencyController(TokenService currencyService) {
        this.currencyService = currencyService;
    }

    // Revoke a specific currency token
    @PostMapping("/revoke")
    public ResponseEntity<String> revokeToken(@RequestParam String token) {
        currencyService.revokeToken(token);
        return ResponseEntity.ok("Token revoked successfully.");
    }

//    // Approve a user's withdrawal request
//    @PostMapping("/approve-withdrawal")
//    public ResponseEntity<String> approveWithdrawal(@RequestParam Long withdrawalId) {
//        currencyService.approveWithdrawal(withdrawalId);
//        return ResponseEntity.ok("Withdrawal approved.");
//    }
//
//    // View all transactions (for monitoring)
//    @GetMapping("/transactions")
//    public ResponseEntity<List<CurrencyTransaction>> getAllTransactions() {
//        return ResponseEntity.ok(currencyService.getAllTransactions());
//    }
//
//    // Set exchange rate (how much currency per KB of code)
//    @PostMapping("/set-exchange-rate")
//    public ResponseEntity<String> setExchangeRate(@RequestParam double rate) {
//        currencyService.setExchangeRate(rate);
//        return ResponseEntity.ok("Exchange rate updated.");
//    }
}
