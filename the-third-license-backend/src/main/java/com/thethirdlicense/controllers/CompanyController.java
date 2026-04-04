package com.thethirdlicense.controllers;

import com.thethirdlicense.models.AccessRequest;
import com.thethirdlicense.models.Company;
import com.thethirdlicense.models.Repository_;
import com.thethirdlicense.models.User;
import com.thethirdlicense.models.Contribution;
import com.thethirdlicense.models.RepositoryAccess;
import com.thethirdlicense.repositories.AccessRequestRepository;
import com.thethirdlicense.repositories.CompanyRepository;
import com.thethirdlicense.repositories.ContributionRepository;
import com.thethirdlicense.repositories.RepositoryAccessRepository;
import com.thethirdlicense.repositories.UserRepository;
import com.thethirdlicense.repositories.RepositoryRepository;
import com.thethirdlicense.security.UserPrincipal;
import com.thethirdlicense.services.CompanyService;
import com.thethirdlicense.services.GitService;
import com.thethirdlicense.services.UserService;
import com.thethirdlicense.controllers.RepositoryDto;
import com.thethirdlicense.exceptions.ResourceNotFoundException;
import com.thethirdlicense.exceptions.UnauthorizedException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.CrudRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;
    private final UserRepository userRepository;
    private final RepositoryRepository repositoryRepository;
    private final ContributionRepository contributionRepository;
    private final CompanyRepository companyRepository;
    private final GitService gitService;
	private final UserService  userService;
	private final RepositoryAccessRepository repositoryAccessRepository;
	private final AccessRequestRepository  accessRequestRepository;

    @Autowired
    public CompanyController(UserService  userService,
    		RepositoryAccessRepository repositoryAccessRepository,
    		AccessRequestRepository  accessRequestRepository,
    						CompanyRepository companyRepository,
                             RepositoryRepository repositoryRepository,
                             GitService gitService,
                             UserRepository userRepository,
                             CompanyService companyService,
                             ContributionRepository contributionRepository) {
        this.gitService = gitService;
        this.userRepository = userRepository;
        this.companyService = companyService;
        this.repositoryRepository = repositoryRepository;
        this.companyRepository = companyRepository;
        this.contributionRepository = contributionRepository;
        this.repositoryAccessRepository = repositoryAccessRepository;
        this.userService =userService;
        this.accessRequestRepository = accessRequestRepository;
    }

    @PostMapping("/open")
    public ResponseEntity<?> openCompany(@RequestBody Company company) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal)) {
                throw new UnauthorizedException("Access denied: User not authenticated");
            }

            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            User owner = userRepository.findByUsername(userPrincipal.getUsername())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            Company createdCompany = companyService.openCompany(company, owner);
            Repository_ repository = repositoryRepository.findByCompany(createdCompany).orElse(null);

            return ResponseEntity.ok("Company created! Repository URL: " + (repository != null ? repository.getGitUrl() : "No repo created"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/{repoId}/clone")
    public ResponseEntity<?> cloneRepository(@PathVariable("repoId") UUID repoId) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal)) {
                throw new UnauthorizedException("Access denied: User not authenticated");
            }

            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            User user = userRepository.findByUsername(userPrincipal.getUsername())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            String message = gitService.cloneRepository(repoId, user);

            // Set cloned = true in Repository_
            Repository_ repo = repositoryRepository.findById(repoId)
                    .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));
            repo.setCloned(true);
            repositoryRepository.save(repo);

            return ResponseEntity.ok(message);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/{companyId}/repositories")
    public ResponseEntity<List<RepositoryDto>> getCompanyRepositories(@PathVariable("companyId") UUID companyId) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal)) {
            return ResponseEntity.status(403).build();
        }

        UserPrincipal userPrincipal = (UserPrincipal) principal;
        User currentUser = userService.findById(userPrincipal.getId());

        List<Repository_> entities = repositoryRepository.findByCompanyId(companyId);

        List<RepositoryDto> dtos = entities.stream().map(repo -> {
            RepositoryDto dto = new RepositoryDto();
            dto.setId(repo.getId());
            dto.setName(repo.getName());
            dto.setGitUrl(repo.getGitUrl());
            dto.setOwnerId(repo.getOwner().getId());
            dto.setCompanyId(repo.getCompany().getId());
            dto.setCompanyOwnerId(repo.getCompany().getOwner().getId());
            dto.setCloned(repo.isCloned());

            // Check if user has access via RepositoryAccess
            Optional<RepositoryAccess> access = repositoryAccessRepository
                    .findByUserIdAndRepositoryId(currentUser.getId(), repo.getId());

            if (access.isPresent()) {
                dto.setAccessStatus("APPROVED");
            } else {
                // Check if access request is pending
                Optional<AccessRequest> optRequest = accessRequestRepository.findByUserAndRepository(currentUser, repo);
                boolean hasPending = optRequest.isPresent() &&
                                     optRequest.get().getApproved() == AccessRequest.Status.PENDING;

                dto.setAccessStatus(hasPending ? "PENDING" : "NONE");
            }

            return dto;
        }).toList();

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{repositoryId}/contributions")
    public ResponseEntity<List<Contribution>> listContributions(@PathVariable UUID repositoryId) {
        List<Contribution> contributions = contributionRepository.findByRepositoryId(repositoryId);
        return ResponseEntity.ok(contributions);
    }
    
    @GetMapping("/all")
    public ResponseEntity<?> getAllCompanies() {
        try {
            List<Company> companies = companyRepository.findAll();

            List<Map<String, Object>> result = companies.stream().map(company -> {
                Map<String, Object> data = new HashMap<>();
                data.put("id", company.getId());
                data.put("name", company.getName());
                data.put("owner", company.getOwner().getUsername());
                return data;
            }).toList();

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
    @GetMapping("/my-companies")
    public ResponseEntity<?> getMyCompanies() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal)) {
                throw new UnauthorizedException("Unauthorized");
            }

            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            User user = userRepository.findByUsername(userPrincipal.getUsername())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            List<Company> ownedCompanies = companyRepository.findByOwner(user);
            List<Company> contributedCompanies = companyRepository.findByUsers(user);
            List<Company> accessedCompanies = companyRepository.findByRepositoryAccessUser(user);  //  added

            Set<Company> uniqueCompanies = new HashSet<>();
            uniqueCompanies.addAll(ownedCompanies);
            uniqueCompanies.addAll(contributedCompanies);
            uniqueCompanies.addAll(accessedCompanies);  // added

            List<Map<String, Object>> companyList = uniqueCompanies.stream().map(company -> {
                Map<String, Object> companyData = new HashMap<>();
                companyData.put("id", company.getId());
                companyData.put("name", company.getName());
                companyData.put("owner", company.getOwner().getUsername());
                return companyData;
            }).toList();

            return ResponseEntity.ok(Map.of("companies", companyList));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

}
