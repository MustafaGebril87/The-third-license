package com.thethirdlicense.repositories;

import com.thethirdlicense.models.Company;
import com.thethirdlicense.models.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

	  /** Row-locked lookup so concurrent unit issuance can't lose updates to totalUnits. Requires a transaction. */
	  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
	  @Query("SELECT c FROM Company c WHERE c.id = :id")
	  java.util.Optional<Company> findForUpdateById(@org.springframework.data.repository.query.Param("id") UUID id);

	  List<Company> findByTotalUnits(long totalUnits);
	  Optional<Company> findByName(String name);
	  boolean existsByName(String name);
	  List<Company> findByOwner(User user);
	  List<Company> findByUsers(User user);
	  @Query("SELECT DISTINCT c FROM Company c JOIN c.repositories r JOIN RepositoryAccess ra ON ra.repository = r WHERE ra.user = :user")
	  List<Company> findByRepositoryAccessUser(@Param("user") User user);

	  List<Company> findByNameContainingIgnoreCase(String name);
	  boolean existsByNameIgnoreCase(String name);
}
