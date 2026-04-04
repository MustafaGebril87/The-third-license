package com.thethirdlicense.repositories;

import com.thethirdlicense.models.AccessRequest;
import com.thethirdlicense.models.AccessRequest.Status;
import com.thethirdlicense.models.Repository_;
import com.thethirdlicense.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccessRequestRepository extends JpaRepository<AccessRequest, UUID> {
	@Query("SELECT ar FROM AccessRequest ar " +
		       "WHERE ar.status = 'PENDING' " +
		       "AND ar.repository.company.owner = :owner")
		List<AccessRequest> findPendingByCompanyOwner(@Param("owner") User owner);

    Optional<AccessRequest> findByUserAndRepository(User user, Repository_ repository);
    
    boolean existsByRepositoryAndUserAndStatus(Repository_ repository, User user, AccessRequest.Status status);

    boolean existsByRepositoryIdAndUserIdAndStatus(UUID repositoryId, UUID userId, AccessRequest.Status status);

	Optional<User> findByUserIdAndRepositoryId(UUID id, UUID repoId);

	boolean existsByUserIdAndRepositoryIdAndStatus(UUID id, UUID id2, Status approved);



}
