package com.thethirdlicense.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thethirdlicense.controllers.ContributionDto;
import com.thethirdlicense.models.MergeRequest;
import com.thethirdlicense.models.MergeRequestStatus;

public interface MergeRequestRepository extends JpaRepository<MergeRequest, UUID> {
    
    List<MergeRequest> findByRepositoryIdAndStatus(UUID repositoryId, MergeRequestStatus status);

    Optional<MergeRequest> findByRepositoryIdAndBranchAndFilePathAndStatus(UUID repositoryId, String branch, String filePath, MergeRequestStatus status);

    boolean existsByRepositoryIdAndBranchAndFilePathAndStatus(UUID repositoryId, String branch, String filePath, MergeRequestStatus status);

	Collection<MergeRequest> findByRepositoryIdAndBranchAndStatus(UUID id, String branch,
			MergeRequestStatus pending);
}
