package com.thethirdlicense.services;

import com.thethirdlicense.models.Transaction;
import com.thethirdlicense.models.User;
import com.thethirdlicense.models.Share;
import com.thethirdlicense.repositories.TransactionRepository;
import com.thethirdlicense.repositories.UserRepository;
import com.thethirdlicense.repositories.ShareRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransactionService {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShareRepository shareRepository;

    /**
     * Create a new transaction (buying or selling shares).
     */
    public Transaction createTransaction(UUID buyerId, UUID sellerId, UUID shareId, int quantity, double pricePerShare) {
        Optional<User> buyerOpt = userRepository.findById(buyerId);
        Optional<User> sellerOpt = userRepository.findById(sellerId);
        Optional<Share> shareOpt = shareRepository.findById(shareId);

        if (buyerOpt.isPresent() && sellerOpt.isPresent() && shareOpt.isPresent()) {
            User buyer = buyerOpt.get();
            User seller = sellerOpt.get();
            Share share = shareOpt.get();

            Transaction transaction = new Transaction();
            transaction.setBuyer(buyer);
            transaction.setSeller(seller);
            transaction.setShare(share);
            transaction.setQuantity(quantity);
            transaction.setPricePerShare(pricePerShare);
            transaction.setTotalAmount(quantity * pricePerShare);

            return transactionRepository.save(transaction);
        }
        throw new IllegalArgumentException("Invalid buyer, seller, or share");
    }

    /**
     * Get all transactions.
     */
    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAll();
    }

    /**
     * Get transactions of a specific user.
     */
    public List<Transaction> getTransactionsByUser(UUID  userId) {
        return transactionRepository.findByBuyerIdOrSellerId(userId, userId);
    }

    /**
     * Get transaction by ID.
     */
    public Optional<Transaction> getTransactionById(UUID id) {
        return transactionRepository.findById(id);
    }
}
