package com.thethirdlicense.services;

import com.thethirdlicense.models.Share;
import org.springframework.transaction.annotation.Transactional;

import com.thethirdlicense.models.User;
import com.thethirdlicense.controllers.ShareDTO;
import com.thethirdlicense.models.Company;
import com.thethirdlicense.models.Contribution;
import com.thethirdlicense.repositories.ShareRepository;
import com.thethirdlicense.repositories.CompanyRepository;
import com.thethirdlicense.repositories.ContributionRepository;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
@Service
public class ShareService {

    private final ShareRepository shareRepository;

    @Autowired
    public ShareService(ShareRepository shareRepository) {
        this.shareRepository = shareRepository;
    }

    public void offerShare(Share share, User owner) {
        if (!share.getOwner().equals(owner)) {
            throw new AccessDeniedException("You can't offer shares that you don't own.");
        }
        share.setForSale(true);
        shareRepository.save(share);
    }

    public void buyShare(UUID  shareId, User buyer, int amount) {
        Share share = shareRepository.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("Share not found"));

        if (!share.isForSale()) {
            throw new IllegalStateException("This share is not for sale.");
        }

        // Transfer ownership
        share.setOwner(buyer);
        share.setForSale(false);
        shareRepository.save(share);
    }
    public void recalculateShares(Company company) {
        List<Contribution> contributions = company.getRepositories().stream()
                .flatMap(repo -> repo.getContributions().stream())
                .filter(Contribution::isApproved)
                .collect(Collectors.toList());

        System.out.println(">>> Total approved contributions: " + contributions.size());

        int totalModifiedSize = contributions.stream()
                .mapToInt(Contribution::getModifiedCodeSize)
                .sum();

        if (totalModifiedSize == 0) {
            System.out.println(">>> Total modified size is 0. Resetting all shares to 0.");
            List<Share> allShares = shareRepository.findByCompany(company);
            for (Share share : allShares) {
                share.setPercentage(0.0);
                shareRepository.save(share);
            }
            return;
        }

        Set<User> contributors = contributions.stream()
                .map(Contribution::getUser)
                .collect(Collectors.toSet());

        for (User user : contributors) {
            int userModifiedSize = contributions.stream()
                    .filter(c -> c.getUser().equals(user))
                    .mapToInt(Contribution::getModifiedCodeSize)
                    .sum();

            double sharePercentage = (double) userModifiedSize / totalModifiedSize;

            System.out.println(">>> Recalculating share for user: " + user.getId() + " - " + user.getUsername());
            System.out.println("    Modified code size: " + userModifiedSize);
            System.out.println("    Share %: " + (sharePercentage * 100.0));

            List<Share> userShares = shareRepository.findByUserAndCompany(user, company);
            Share share;

            if (!userShares.isEmpty()) {
                share = userShares.get(0); // Assume 1 share per user per company
            } else {
                share = new Share();
                share.setUser(user);
                share.setCompany(company);
            }

            share.setPercentage(sharePercentage);
            shareRepository.save(share);
        }
    }







    public List<Share> getSharesForUser(User user) {
    	return shareRepository.findByUserId(user.getId());
    }
    @Transactional
    public Share splitShare(UUID shareId, double splitPercentage) {
        Share original = shareRepository.findById(shareId)
            .orElseThrow(() -> new IllegalArgumentException("Original share not found"));

        if (splitPercentage <= 0 || splitPercentage >= original.getPercentage()) {
            throw new IllegalArgumentException("Invalid split percentage.");
        }

        // Reduce the original share
        double remaining = original.getPercentage() - splitPercentage;
        original.setPercentage(remaining);
        shareRepository.save(original);

        // Create the new split share with same owner and company
        Share newShare = new Share();
        newShare.setCompany(original.getCompany());
        newShare.setOwner(original.getOwner());
        newShare.setPercentage(splitPercentage);

        return shareRepository.save(newShare);
    }

    public Share markShareForSale(UUID shareId, BigDecimal price) {
        Share share = shareRepository.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("Share not found"));

        share.setForSale(true);
        share.setPrice(price);
        return shareRepository.save(share);
    }

    public List<Share> getAllSharesForSale() {
        return shareRepository.findByIsForSaleTrue();
    }

    public Share unmarkShareForSale(UUID shareId) {
        Share share = shareRepository.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("Share not found"));

        share.setForSale(false);
        return shareRepository.save(share);
    }
    
    public List<Share> getSharesByCompany(UUID companyId) {
        return shareRepository.findByCompanyId(companyId);
    }

    public List<Share> getMarketplaceShares(User currentUser) {
        return shareRepository.findByIsForSaleTrueAndUserIdNot(currentUser.getId());
    }

    
    
}
