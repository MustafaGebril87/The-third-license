package com.thethirdlicense.repositories;

import com.thethirdlicense.models.Company;
import com.thethirdlicense.models.Share;
import com.thethirdlicense.models.User;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
