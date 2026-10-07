package com.thethirdlicense.repositories;

import com.thethirdlicense.models.Company;
import com.thethirdlicense.models.Share;
import com.thethirdlicense.models.User;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShareRepository extends JpaRepository<Share, UUID> {
	List<Share> findByCompanyId(UUID  companyId);

	List<Share> findByUserAndCompany(User user, Company company);

	Optional<Share> findById(UUID  shareId);

	 List<Share> findByUser(User user);
	 
	 List<Share> findByIsForSaleTrueAndUserIdNot(UUID userId); //  works with isForSale field
	 List<Share> findByIsForSaleTrue();


	List<Share> findByisForSaleTrue();

	List<Share> findByCompany(Company company);

	List<Share> findByUserId(UUID id);

	/** Row-locked lookup used when transferring ownership. Requires a transaction. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM Share s WHERE s.id = :id")
	Optional<Share> findForUpdateById(@Param("id") UUID id);
}
