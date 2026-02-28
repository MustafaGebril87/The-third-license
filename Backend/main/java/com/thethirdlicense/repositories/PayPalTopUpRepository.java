package com.thethirdlicense.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thethirdlicense.models.PayPalTopUp;

public interface PayPalTopUpRepository extends JpaRepository<PayPalTopUp, UUID> {
    Optional<PayPalTopUp> findByPaypalOrderId(String paypalOrderId);
}
