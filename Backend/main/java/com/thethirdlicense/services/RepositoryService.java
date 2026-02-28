package com.thethirdlicense.services;

import com.thethirdlicense.models.Repository_;
import com.thethirdlicense.models.Company;
import com.thethirdlicense.repositories.RepositoryRepository;
import com.thethirdlicense.repositories.CompanyRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RepositoryService {

    
    private final  RepositoryRepository repositoryRepository;

    
    private final  CompanyRepository companyRepository;


    @Autowired
    public RepositoryService(RepositoryRepository repositoryRepository, CompanyRepository companyRepository) {
        this.repositoryRepository = repositoryRepository;
        this.companyRepository = companyRepository;
    }

    /**
     * Create a new repository for a company.
     */
    public Repository_ createRepository(UUID companyId, Repository_ repository) {
        Optional<Company> companyOpt = companyRepository.findById(companyId);
        if (companyOpt.isPresent()) {
            Company company = companyOpt.get();
            repository.setCompany(company);

            if (repository.getId() == null) {
                repository.setId(UUID.randomUUID()); // Ensure ID is not null
            }

            return repositoryRepository.save(repository);
        }
        throw new IllegalArgumentException("Company not found");
    }


    /**
     * Get all repositories of a company.
     */
    public List<Repository_> getRepositoriesByCompany(UUID companyId) {
        return repositoryRepository.findByCompanyId(companyId);
    }

    /**
     * Get a specific repository by ID.
     */
    public Optional<Repository_> getRepositoryById(UUID id) {
        return repositoryRepository.findById(id);
    }

    /**
     * Delete a repository by ID.
     */
    public void deleteRepository(UUID id) {
        repositoryRepository.deleteById(id);
    }
}
