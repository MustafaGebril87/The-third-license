package com.thethirdlicense.services;
import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.thethirdlicense.models.CurrencyTransaction;
import com.thethirdlicense.models.ExternalClient;
import com.thethirdlicense.models.User;
import com.thethirdlicense.repositories.CurrencyTransactionRepository;
import com.thethirdlicense.repositories.ExternalClientRepository;
import com.thethirdlicense.repositories.UserRepository;

@Service
public class CurrencyTransactionService {

    private final CurrencyTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final ExternalClientRepository externalClientRepository;

    @Autowired
    public CurrencyTransactionService(CurrencyTransactionRepository transactionRepository, UserRepository userRepository, ExternalClientRepository externalClientRepository) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.externalClientRepository = externalClientRepository;
    }

    public void logUserTransaction(UUID userId, BigDecimal amount, String description) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        CurrencyTransaction transaction = new CurrencyTransaction();
        transaction.setUser(user);
        transaction.setAmount(amount);
        transaction.setDescription(description);

        transactionRepository.save(transaction);
    }

    public void logExternalTransaction(Long clientId, BigDecimal amount, String description) {
        ExternalClient client = externalClientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));

        CurrencyTransaction transaction = new CurrencyTransaction();
        transaction.setExternalClient(client);
        transaction.setAmount(amount);
        transaction.setDescription(description);

        transactionRepository.save(transaction);
    }
}
