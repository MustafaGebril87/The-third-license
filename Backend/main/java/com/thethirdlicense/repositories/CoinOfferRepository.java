package com.thethirdlicense.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thethirdlicense.models.CoinOffer;

public interface CoinOfferRepository extends JpaRepository<CoinOffer, UUID> {
    List<CoinOffer> findByActiveTrue();
}
