package com.thethirdlicense.repositories;

import com.thethirdlicense.models.Company;
import com.thethirdlicense.models.Repository_;
import com.thethirdlicense.models.User;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public interface RepositoryRepository extends JpaRepository<Repository_, UUID> {
	 List<Repository_> findByCompanyId(UUID companyId);

	Optional<Repository_> findByName(String string);

	Optional<Repository_> findById(UUID repoId);

	Optional<Repository_> findByCompany(Company createdCompany);



}
