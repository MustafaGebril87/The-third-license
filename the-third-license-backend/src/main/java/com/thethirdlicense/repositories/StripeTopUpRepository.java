package com.thethirdlicense.repositories;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.thethirdlicense.models.StripeTopUp;

public interface StripeTopUpRepository extends JpaRepository<StripeTopUp, UUID> {
    Optional<StripeTopUp> findByStripeSessionId(String stripeSessionId);
}
