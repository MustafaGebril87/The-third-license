package com.thethirdlicense.repositories;

import com.thethirdlicense.models.UserCurrency;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserCurrencyRepository extends JpaRepository<UserCurrency, UUID> {

    Optional<UserCurrency> findByUser_Id(UUID userId);
}
