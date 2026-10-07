package com.thethirdlicense.controllers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
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

    @Value("${app.cors.allowed-origin}")
    private String frontendOrigin;

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
            @RequestParam("percentage") double percentage,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Share newShare = shareService.splitShare(shareId, percentage, requireUser(principal));
        return ResponseEntity.ok(new ShareDTO(newShare));
    }

    @PostMapping("/{shareId}/mark-for-sale")
    public ResponseEntity<ShareDTO> markShareForSale(
            @PathVariable("shareId") UUID shareId,
            @RequestParam("price") BigDecimal price,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Share updated = shareService.markShareForSale(shareId, price, requireUser(principal));
        return ResponseEntity.ok(new ShareDTO(updated));
    }

    @PostMapping("/{shareId}/unmark-for-sale")
    public ResponseEntity<ShareDTO> unmarkShareForSale(
            @PathVariable UUID shareId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Share updated = shareService.unmarkShareForSale(shareId, requireUser(principal));
        return ResponseEntity.ok(new ShareDTO(updated));
    }

    @GetMapping("/company/{companyId}")
    public ResponseEntity<List<ShareDTO>> getSharesByCompany(@PathVariable UUID companyId) {
        List<ShareDTO> shares = shareService.getSharesByCompany(companyId).stream()
                .map(ShareDTO::new)
                .toList();
        return ResponseEntity.ok(shares);
    }

    /**
     * Starts a Stripe checkout. Success/cancel URLs are built server-side from the configured
     * frontend origin so the checkout can't be used to redirect users to arbitrary sites.
     */
    @PostMapping("/buy/{shareId}/stripe/create")
    public ResponseEntity<StripeCheckoutResponse> initiateSharePurchase(
            @PathVariable UUID shareId,
            @AuthenticationPrincipal UserPrincipal principal
    ) throws StripeException {
        UUID buyerId = requireUser(principal);
        String origin = frontendOrigin.endsWith("/") ? frontendOrigin.substring(0, frontendOrigin.length() - 1) : frontendOrigin;

        StripeCheckoutResponse response = shareStripeService.initiatePurchase(
                buyerId, shareId, origin + "/stripe/success", origin + "/stripe/cancel");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/buy/stripe/confirm")
    public ResponseEntity<String> confirmSharePurchase(
            @RequestParam String sessionId,
            @AuthenticationPrincipal UserPrincipal principal
    ) throws StripeException {
        shareStripeService.confirmPurchase(requireUser(principal), sessionId);
        return ResponseEntity.ok("Share purchased successfully.");
    }

    private static UUID requireUser(UserPrincipal principal) {
        if (principal == null) throw new UnauthorizedException("Unauthorized");
        return principal.getId();
    }
}
