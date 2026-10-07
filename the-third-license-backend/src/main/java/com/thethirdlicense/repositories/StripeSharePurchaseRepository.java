package com.thethirdlicense.repositories;

import com.thethirdlicense.models.StripeSharePurchase;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StripeSharePurchaseRepository extends JpaRepository<StripeSharePurchase, UUID> {
    Optional<StripeSharePurchase> findByStripeSessionId(String stripeSessionId);

    /** Row-locked lookup so two concurrent confirmations of one session cannot both complete. Requires a transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM StripeSharePurchase p WHERE p.stripeSessionId = :sessionId")
    Optional<StripeSharePurchase> findForUpdateByStripeSessionId(@Param("sessionId") String sessionId);
}
