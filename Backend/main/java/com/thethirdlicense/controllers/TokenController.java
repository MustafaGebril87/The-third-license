package com.thethirdlicense.controllers;

import com.thethirdlicense.controllers.TokenDTO;
import com.thethirdlicense.security.UserPrincipal;
import com.thethirdlicense.services.TokenService;
import com.thethirdlicense.exceptions.UnauthorizedException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import com.thethirdlicense.services.CoinMarketService;

@RestController
@RequestMapping("/api/tokens")
public class TokenController {

    private final TokenService tokenService;
    private final CoinMarketService coinMarketService;

    @Autowired
    public TokenController(TokenService tokenService, CoinMarketService coinMarketService) {
        this.tokenService = tokenService;
        this.coinMarketService=coinMarketService;
    }

    @GetMapping("/my")
    public List<TokenDTO> getMyTokens(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof UserPrincipal)) {
            throw new UnauthorizedException("Unauthorized access: Invalid user session");
        }

        UUID userId = ((UserPrincipal) authentication.getPrincipal()).getId();
        return tokenService.getTokensForUser(userId);
    }


@PostMapping("/offer")
public void offer(
        @RequestParam UUID tokenId,
        @RequestParam double coinAmount,
        @RequestParam double pricePerCoin,
        Authentication authentication
) {
    if (!(authentication.getPrincipal() instanceof UserPrincipal)) {
        throw new UnauthorizedException("Unauthorized");
    }
    UUID userId = ((UserPrincipal) authentication.getPrincipal()).getId();

    coinMarketService.createOfferFromToken(userId, tokenId, coinAmount, pricePerCoin);
}
}
