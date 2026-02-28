package com.thethirdlicense.repositories;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.thethirdlicense.models.ExternalClient;
@Repository
public interface ExternalClientRepository extends JpaRepository<ExternalClient, Long> {
    Optional<ExternalClient> findByApiKey(String apiKey);
}
