package com.thethirdlicense.controllers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thethirdlicense.Util.TokenRequest;
import com.thethirdlicense.Util.TokenResponse;
import com.thethirdlicense.Util.TokenValidationResponse;
import com.thethirdlicense.models.Token;
import com.thethirdlicense.services.TokenService;

@RestController
@RequestMapping("/api/external")
public class ExternalAPIController {

    private final TokenService tokenService;

    @Autowired
    public ExternalAPIController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

//    @PostMapping("/generate-token")
//    public ResponseEntity<TokenResponse> generateToken(@RequestBody TokenRequest request) {
//        Token token = tokenService.generateTokenForExternalClient(request.getClientId(), request.getAmount());
//        return ResponseEntity.ok(new TokenResponse(token.getTokenValue(), token.getAmount()));
//    }

    @GetMapping("/validate-token/{tokenValue}")
    public ResponseEntity<TokenValidationResponse> validateToken(@PathVariable String tokenValue) {
        boolean isValid = tokenService.validateToken(tokenValue);
        return ResponseEntity.ok(new TokenValidationResponse(isValid));
    }

    @PostMapping("/revoke-token/{tokenValue}")
    public ResponseEntity<String> revokeToken(@PathVariable String tokenValue) {
        tokenService.revokeToken(tokenValue);
        return ResponseEntity.ok("Token revoked successfully.");
    }
}
