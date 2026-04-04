package com.thethirdlicense.repositories;

import com.thethirdlicense.models.RepositoryAccess;
import com.thethirdlicense.models.Repository_;
import com.thethirdlicense.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositoryAccessRepository extends JpaRepository<RepositoryAccess, UUID> {
    Optional<RepositoryAccess> findByUserAndRepository(User user, Repository_ repository);
    List<RepositoryAccess> findByRepository(Repository_ repository);
    List<RepositoryAccess> findByUser(User user);
    Optional<RepositoryAccess> findByUserIdAndRepositoryId(UUID userId, UUID repositoryId);

}
