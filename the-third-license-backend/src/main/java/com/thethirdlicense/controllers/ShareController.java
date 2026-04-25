package com.thethirdlicense.controllers;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.stripe.exception.StripeException;
import com.thethirdlicense.models.Share;
import com.thethirdlicense.models.User;
import com.thethirdlicense.security.UserPrincipal;
import com.thethirdlicense.services.ShareService;
import com.thethirdlicense.services.ShareStripeService;
import com.thethirdlicense.services.UserService;
import com.thethirdlicense.exceptions.ResourceNotFoundException;
import com.thethirdlicense.exceptions.UnauthorizedException;

@RestController
@RequestMapping("/api/shares")
public class ShareController {

    private final ShareService shareService;
    private final UserService userService;
    private final ShareStripeService shareStripeService;

    @Autowired
    public ShareController(ShareService shareService, UserService userService, ShareStripeService shareStripeService) {
        this.shareService = shareService;
        this.userService = userService;
        this.shareStripeService = shareStripeService;
    }

    @GetMapping("/marketplace")
    public ResponseEntity<List<ShareDTO>> getMarketplaceShares(@AuthenticationPrincipal UserPrincipal principal) {
        List<ShareDTO> shares;

        if (principal != null) {
            User user = userService.findById(principal.getId());
            shares = shareService.getMarketplaceShares(user)
                    .stream()
                    .map(ShareDTO::new)
                    .toList();
        } else {
            shares = shareService.getAllSharesForSale()
                    .stream()
                    .map(ShareDTO::new)
                    .toList();
        }

        return ResponseEntity.ok(shares);
    }

    @GetMapping("/my")
    public ResponseEntity<List<ShareDTO>> getMyShares(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) throw new UnauthorizedException("Unauthorized");

        UUID userId = principal.getId();
        User user = userService.findById(userId);
        if (user == null) throw new ResourceNotFoundException("User not found");

        List<ShareDTO> shares = shareService.getSharesForUser(user).stream()
                .map(ShareDTO::new)
                .toList();

        return ResponseEntity.ok(shares);
    }

    @PostMapping("/{shareId}/split")
    public ResponseEntity<ShareDTO> splitShare(
            @PathVariable("shareId") UUID shareId,
            @RequestParam("percentage") double percentage
    ) {
        Share newShare = shareService.splitShare(shareId, percentage);
        return ResponseEntity.ok(new ShareDTO(newShare));
    }

    @PostMapping("/{shareId}/mark-for-sale")
    public ResponseEntity<ShareDTO> markShareForSale(
            @PathVariable("shareId") UUID shareId,
            @RequestParam("price") BigDecimal price
    ) {
        Share updated = shareService.markShareForSale(shareId, price);
        return ResponseEntity.ok(new ShareDTO(updated));
    }

    @PostMapping("/{shareId}/unmark-for-sale")
    public ResponseEntity<Share> unmarkShareForSale(@PathVariable UUID shareId) {
        Share updated = shareService.unmarkShareForSale(shareId);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/company/{companyId}")
    public ResponseEntity<List<Share>> getSharesByCompany(@PathVariable UUID companyId) {
        List<Share> shares = shareService.getSharesByCompany(companyId);
        return ResponseEntity.ok(shares);
    }

    @PostMapping("/buy/{shareId}/stripe/create")
    public ResponseEntity<StripeCheckoutResponse> initiateSharePurchase(
            @PathVariable UUID shareId,
            @RequestParam String successUrl,
            @RequestParam String cancelUrl,
            Principal principal
    ) throws StripeException {
        if (!(principal instanceof Authentication)) {
            throw new UnauthorizedException("Unauthorized");
        }
        UserPrincipal userPrincipal = (UserPrincipal) ((Authentication) principal).getPrincipal();
        if (userPrincipal == null) throw new UnauthorizedException("Unauthorized");

        StripeCheckoutResponse response = shareStripeService.initiatePurchase(
                userPrincipal.getId(), shareId, successUrl, cancelUrl);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/buy/stripe/confirm")
    public ResponseEntity<String> confirmSharePurchase(
            @RequestParam String sessionId,
            Principal principal
    ) throws StripeException {
        if (!(principal instanceof Authentication)) {
            throw new UnauthorizedException("Unauthorized");
        }
        UserPrincipal userPrincipal = (UserPrincipal) ((Authentication) principal).getPrincipal();
        if (userPrincipal == null) throw new UnauthorizedException("Unauthorized");

        shareStripeService.confirmPurchase(userPrincipal.getId(), sessionId);
        return ResponseEntity.ok("Share purchased successfully.");
    }
}
