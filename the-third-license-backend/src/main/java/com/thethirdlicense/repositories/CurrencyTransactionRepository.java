package com.thethirdlicense.repositories;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.thethirdlicense.models.CurrencyTransaction;
import com.thethirdlicense.models.ExternalClient;
import com.thethirdlicense.models.User;

@Repository
public interface CurrencyTransactionRepository extends JpaRepository<CurrencyTransaction, Long> {
    List<CurrencyTransaction> findByUser(User user);
    List<CurrencyTransaction> findByExternalClient(ExternalClient client);
}
