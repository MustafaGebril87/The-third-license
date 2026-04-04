package com.thethirdlicense.repositories;

import com.thethirdlicense.models.Company;
import com.thethirdlicense.models.Contribution;
import com.thethirdlicense.models.ContributionStatus;
import com.thethirdlicense.models.Repository_;
import com.thethirdlicense.models.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ContributionRepository extends JpaRepository<Contribution, UUID> {

    // Find all contributions for a given repository
    List<Contribution> findByRepository(Repository_ repository);

    // Find all contributions for a specific user
    List<Contribution> findByUser(User user);

    // Find all approved contributions for a repository
    @Query("SELECT c FROM Contribution c WHERE c.repository = :repository AND c.approved = true")
    List<Contribution> findApprovedContributionsByRepository(@Param("repository") Repository_ repository);

    // Find all pending (not approved) contributions for a repository
    @Query("SELECT c FROM Contribution c WHERE c.repository = :repository AND c.approved = false")
    List<Contribution> findPendingContributionsByRepository(@Param("repository") Repository_ repository);

	List<Contribution> findAllByRepository_Company(Company company);

	List<Contribution> findByRepositoryId(UUID repositoryId);
	@Transactional(readOnly = true)
	@Query("SELECT c FROM Contribution c WHERE c.approved = false AND c.repository.company.owner = :owner")
	List<Contribution> findPendingByCompanyOwner(@Param("owner") User owner);

	Optional<Contribution>  findByRepositoryAndBranch(Repository_ repo, String branchNameToMerge);

	Optional<Contribution> findTopByRepositoryAndBranchOrderByContributionDateDesc(Repository_ repo, String branch);

	List<Contribution> findByRepositoryIdAndBranchAndStatus(UUID id, String branch, ContributionStatus pending);

	
}

