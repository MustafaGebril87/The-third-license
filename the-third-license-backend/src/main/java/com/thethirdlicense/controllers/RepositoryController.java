package com.thethirdlicense.controllers;

import com.thethirdlicense.models.AccessRequest;
import com.thethirdlicense.models.Company;
import com.thethirdlicense.models.Repository_;
import com.thethirdlicense.models.User;
import com.thethirdlicense.repositories.AccessRequestRepository;
import com.thethirdlicense.repositories.RepositoryAccessRepository;
import com.thethirdlicense.repositories.RepositoryRepository;
import com.thethirdlicense.security.UserPrincipal;
import com.thethirdlicense.services.UserService;
import com.thethirdlicense.exceptions.ResourceNotFoundException;
import com.thethirdlicense.controllers.RepositoryDto;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/repositories")
public class RepositoryController {

    private final UserService userService;
    private final RepositoryRepository repositoryRepository;
    private final AccessRequestRepository accessRequestRepository;
    private final RepositoryAccessRepository repositoryAccessRepository;
    public RepositoryController(RepositoryAccessRepository repositoryAccessRepository, AccessRequestRepository accessRequestRepository,UserService userService, RepositoryRepository repositoryRepository) {
        this.userService = userService;
        this.repositoryRepository = repositoryRepository;
        this.accessRequestRepository = accessRequestRepository;
        this.repositoryAccessRepository = repositoryAccessRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<RepositoryDto> getRepositoryById(@PathVariable("id") UUID id) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal)) {
            return ResponseEntity.status(403).build();
        }

        UserPrincipal userPrincipal = (UserPrincipal) principal;
        User user = userService.findById(userPrincipal.getId());

        Repository_ repo = repositoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));

        RepositoryDto dto = new RepositoryDto();
        dto.setId(repo.getId());
        dto.setName(repo.getName());
        dto.setGitUrl(repo.getGitUrl());
        dto.setOwnerId(repo.getOwner().getId());
        dto.setCompanyId(repo.getCompany().getId());
        dto.setCloned(repo.isCloned());

        dto.setContributors(
                repositoryAccessRepository.findByRepository(repo).stream()
                    .map(access -> access.getUser().getId())
                    .collect(Collectors.toList())
            );

        return ResponseEntity.ok(dto);
    }


}
