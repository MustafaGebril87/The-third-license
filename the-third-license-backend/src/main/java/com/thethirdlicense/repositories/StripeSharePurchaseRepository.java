package com.thethirdlicense.repositories;

import com.thethirdlicense.models.StripeSharePurchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StripeSharePurchaseRepository extends JpaRepository<StripeSharePurchase, UUID> {
    Optional<StripeSharePurchase> findByStripeSessionId(String stripeSessionId);
}
