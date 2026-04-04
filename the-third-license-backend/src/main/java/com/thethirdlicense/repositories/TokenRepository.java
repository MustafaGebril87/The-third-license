package com.thethirdlicense.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.thethirdlicense.models.Token;
import com.thethirdlicense.models.User;

@Repository
public interface TokenRepository extends JpaRepository<Token, UUID> {

    Optional<Token> findByTokenValue(String tokenValue);

    List<Token> findByOwner(User owner);

    List<Token> findByOwnerAndRevokedFalseAndUsedFalse(User owner);
}
