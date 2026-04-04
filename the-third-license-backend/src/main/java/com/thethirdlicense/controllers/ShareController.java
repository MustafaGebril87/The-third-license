package com.thethirdlicense.controllers;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.thethirdlicense.models.RepositoryAccess;
import com.thethirdlicense.models.Repository_;
import com.thethirdlicense.models.Share;
import com.thethirdlicense.models.User;
import com.thethirdlicense.repositories.RepositoryAccessRepository;
import com.thethirdlicense.repositories.ShareRepository;
import com.thethirdlicense.models.ShareTransactionRequest;
import com.thethirdlicense.security.UserPrincipal;
import com.thethirdlicense.services.ShareService;
import com.thethirdlicense.services.TokenService;
import com.thethirdlicense.services.UserService;
import com.thethirdlicense.exceptions.ResourceNotFoundException;
import com.thethirdlicense.exceptions.UnauthorizedException;

@RestController
@RequestMapping("/api/shares")
public class ShareController {

    private final ShareService shareService;
    private final UserService userService;
    private final TokenService currencyService;
	private final ShareRepository shareRepository;
	private final RepositoryAccessRepository repositoryAccessRepository;

    @Autowired
    public ShareController(RepositoryAccessRepository repositoryAccessRepository,ShareRepository shareRepository,ShareService shareService, UserService userService, TokenService currencyService) {
        this.shareService = shareService;
        this.userService = userService;
        this.currencyService = currencyService;
        this.repositoryAccessRepository = repositoryAccessRepository;
        this.shareRepository = shareRepository;
    }
    @GetMapping("/marketplace")
    public ResponseEntity<List<ShareDTO>> getMarketplaceShares(@AuthenticationPrincipal UserPrincipal principal) {
        List<ShareDTO> shares;

        if (principal != null) {
            // Authenticated: exclude current user's shares
            User user = userService.findById(principal.getId());
            shares = shareService.getMarketplaceShares(user)
                    .stream()
                    .map(ShareDTO::new)
                    .toList();
        } else {
            // Unauthenticated: show all shares for sale
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

    @PostMapping("/buy/{shareId}")
    public ResponseEntity<String> buyShare(
            @PathVariable UUID shareId,
            @RequestParam("price") double price,
            Principal principal
    ) {
        if (!(principal instanceof Authentication)) {
            throw new UnauthorizedException("Unauthorized");
        }

        UserPrincipal userPrincipal = (UserPrincipal) ((Authentication) principal).getPrincipal();
        if (userPrincipal == null) throw new UnauthorizedException("Unauthorized");

        UUID buyerId = userPrincipal.getId();
        currencyService.purchaseShare(buyerId, shareId, price);

        // After purchase, grant repository access manually
        Share share = shareRepository.findById(shareId)
                .orElseThrow(() -> new ResourceNotFoundException("Share not found"));
        User buyer = userService.findById(buyerId);

        for (Repository_ repo : share.getCompany().getRepositories()) {
            boolean hasAccess = repositoryAccessRepository.findByUserAndRepository(buyer, repo).isPresent();
            if (!hasAccess) {
                RepositoryAccess access = new RepositoryAccess();
                access.setUser(buyer);
                access.setRepository(repo);
                access.setAccessLevel(RepositoryAccess.AccessLevel.CONTRIBUTOR);
                access.setGrantedAt(LocalDateTime.now());
                repositoryAccessRepository.save(access);
            }
        }

        return ResponseEntity.ok("Share purchased successfully for " + price + " currency.");
    }

}
