package com.thethirdlicense.services;
import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.thethirdlicense.Util.Utils;
import com.thethirdlicense.controllers.AccessRequestDto;
import com.thethirdlicense.controllers.ContributionDto;
import com.thethirdlicense.models.AccessRequest;
import com.thethirdlicense.models.Company;
import com.thethirdlicense.models.Contribution;
import com.thethirdlicense.models.ContributionStatus;
import com.thethirdlicense.models.Repository_;
import com.thethirdlicense.models.User;
import com.thethirdlicense.repositories.AccessRequestRepository;
import com.thethirdlicense.repositories.CompanyRepository;
import com.thethirdlicense.repositories.ContributionRepository;
import com.thethirdlicense.repositories.RepositoryRepository;
import com.thethirdlicense.repositories.UserRepository;

import org.springframework.transaction.annotation.Transactional;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.api.errors.GitAPIException;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class ContributionService {

    private final ContributionRepository contributionRepository;
    private final ShareService shareService;
    private AccessRequestRepository accessRequestRepository;
    private final RepositoryRepository repositoryRepository;
    @Autowired
    private final CompanyRepository companyRepository;
	private final UserRepository userRepository;
    @Autowired
    public ContributionService(UserRepository userRepository, CompanyRepository companyRepository, RepositoryRepository repositoryRepository, AccessRequestRepository accessRequestRepository, ContributionRepository contributionRepository, ShareService shareService) {
        this.contributionRepository = contributionRepository;
        this.shareService = shareService;
		this.accessRequestRepository = accessRequestRepository;
		this.repositoryRepository = repositoryRepository;
        this.accessRequestRepository = accessRequestRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
    }
    @Transactional
    public void approveContribution(UUID contributionId, User owner) {
        Contribution contribution = contributionRepository.findById(contributionId)
                .orElseThrow(() -> new IllegalArgumentException("Contribution not found"));

        if (!contribution.getRepository().getCompany().getOwner().equals(owner)) {
            throw new AccessDeniedException("Only the company owner can approve contributions.");
        }

        //  Use origin repo path
        String rawPath = contribution.getRepository().getLocalPath();
        String repoPath = rawPath.replace("file:\\", "").replace("file:/", "");
        System.out.println(">>> Sanitized repo path: " + repoPath);

        int modifiedLines = 0;
        System.out.println(">>> Repo path: " + repoPath);

        try (Git git = Git.open(new File(repoPath))) {
            AbstractTreeIterator oldTreeParser;
            if (contribution.getOriginalCommitHash() == null || contribution.getOriginalCommitHash().isEmpty()) {
                System.out.println("Using EmptyTreeIterator as old commit.");
                oldTreeParser = new EmptyTreeIterator();
            } else {
                oldTreeParser = Utils.prepareTreeParser(git.getRepository(), contribution.getOriginalCommitHash());
            }

            if (contribution.getNewCommitHash() == null || contribution.getNewCommitHash().isEmpty()) {
                throw new IllegalStateException("New commit hash cannot be null when approving a contribution");
            }

            AbstractTreeIterator newTreeParser = Utils.prepareTreeParser(git.getRepository(), contribution.getNewCommitHash());

            List<DiffEntry> diffs = git.diff()
                    .setOldTree(oldTreeParser)
                    .setNewTree(newTreeParser)
                    .call();

            System.out.println(">>> Diffing commits: " + contribution.getOriginalCommitHash() + " -> " + contribution.getNewCommitHash());
            System.out.println(">>> Found " + diffs.size() + " diff entries");

            try (DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                formatter.setRepository(git.getRepository());
                formatter.setDiffComparator(RawTextComparator.DEFAULT);
                formatter.setDetectRenames(true);

                for (DiffEntry entry : diffs) {
                    FileHeader fileHeader = formatter.toFileHeader(entry);

                    for (HunkHeader hunk : fileHeader.getHunks()) {
                        for (Edit edit : hunk.toEditList()) {
                            switch (edit.getType()) {
                                case INSERT:
                                case DELETE:
                                case REPLACE:
                                    modifiedLines += Math.max(edit.getLengthA(), edit.getLengthB());
                                    break;
                                default:
                                    break;
                            }
                        }
                    }
                }
            }

            System.out.println(">>> Modified lines: " + modifiedLines);

        } catch (Exception e) {
            throw new RuntimeException("Git diff failed", e);
        }

        contribution.setApproved(true);
        contribution.setModifiedCodeSize(modifiedLines);
        contributionRepository.save(contribution);

        shareService.recalculateShares(contribution.getRepository().getCompany());

    }

    public void declineContribution(UUID contributionId, User owner) {
        Contribution contribution = contributionRepository.findById(contributionId)
                .orElseThrow(() -> new IllegalArgumentException("Contribution not found"));

        if (!contribution.getRepository().getCompany().getOwner().equals(owner)) {
            throw new AccessDeniedException("Only the company owner can decline contributions.");
        }

        contributionRepository.delete(contribution);
    }

    public String requestAccess(UUID repositoryId, User user) {
        // Check if repository exists
    	Repository_ repository = repositoryRepository.findById(repositoryId)
    	        .orElseThrow(() -> new RuntimeException("Repository not found"));

        // Check if user already requested access
        Optional<AccessRequest> existingRequest = accessRequestRepository.findByUserAndRepository(user, repository);
        if (existingRequest.isPresent()) {
            return "Access request already exists";
        }

        // Create a new access request
        AccessRequest accessRequest = new AccessRequest();
        accessRequest.setUser(user);
        accessRequest.setRepository(repository);
        accessRequest.setStatus(AccessRequest.Status.PENDING);

        accessRequestRepository.save(accessRequest);
        return "Access request sent";
    }

    public List<Contribution> findByUser(User user) {
        return contributionRepository.findByUser(user);
    }
    public List<ContributionDto> findPendingByCompanyOwner(User owner) {
        return contributionRepository.findPendingByCompanyOwner(owner)
                .stream()
                .map(ContributionDto::new)
                .collect(Collectors.toList());
    }

    public List<AccessRequestDto> findPendingRequestsByOwner(User owner) {
        List<AccessRequest> requests = accessRequestRepository.findPendingByCompanyOwner(owner);
        return requests.stream().map(request -> new AccessRequestDto(
                request.getId(),
                request.getStatus(),
                request.getRepository().getId()  // Assumes repository is not null
        )).collect(Collectors.toList());
    }
    @Transactional
    public void approveRequest(UUID requestId, User owner) {
        AccessRequest request = accessRequestRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Access request not found"));

        Company company = request.getRepository().getCompany();
        User requester = request.getUser();

        // Optional: validate the owner is actually the owner of the company
        if (!company.getOwner().getId().equals(owner.getId())) {
            throw new AccessDeniedException("You are not allowed to approve this request.");
        }

        //  Add user to company members if not already
        if (!company.getUsers().contains(requester)) {
            company.getUsers().add(requester);
        }

        request.setStatus(AccessRequest.Status.APPROVED);
        accessRequestRepository.save(request);
        companyRepository.save(company); //  Make sure this is saved
    }


    @Transactional
    public void declineRequest(UUID requestId, User owner) {
        AccessRequest request = accessRequestRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Access request not found"));

        if (!request.getRepository().getCompany().getOwner().getId().equals(owner.getId())) {
            throw new AccessDeniedException("You are not the owner of this company.");
        }

        request.setStatus(AccessRequest.Status.DECLINED);
        accessRequestRepository.save(request);
    }
    @Transactional
    public UUID pushContribution(UUID repositoryId, User user, int codeSize) {
        Repository_ repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found"));

        // Ensure the user is allowed to push (approved access request)
        boolean hasAccess = accessRequestRepository
                .findByUserAndRepository(user, repo)
                .map(req -> req.getStatus() == AccessRequest.Status.APPROVED)
                .orElse(false);

        if (!hasAccess) {
            throw new AccessDeniedException("User does not have access to push to this repository.");
        }

        String repoPath = "C:\\repos\\cloned-repos\\" + repo.getName() + "-" + user.getUsername();
        String newCommitHash = null;
        String originalCommitHash = null;

        try (Git git = Git.open(new File(repoPath))) {
            Iterable<RevCommit> commits = git.log().setMaxCount(2).call();

            for (RevCommit commit : commits) {
                if (newCommitHash == null) {
                    newCommitHash = commit.getName();
                } else if (originalCommitHash == null) {
                    originalCommitHash = commit.getName();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve commit information", e);
        }

        Contribution contribution = new Contribution();
        contribution.setId(UUID.randomUUID());
        contribution.setUser(user);
        contribution.setRepository(repo);
        contribution.setContributionDate(new Date());
        contribution.setCodeSize(codeSize);
        contribution.setApproved(false); // wait for owner approval
        contribution.setStatus(ContributionStatus.PENDING);
        contribution.setOriginalCommitHash(originalCommitHash);
        contribution.setNewCommitHash(newCommitHash);

        contributionRepository.save(contribution);
        return contribution.getId();
    }

    public Contribution saveContribution(Contribution contribution) {
        return contributionRepository.save(contribution);
    }
	
    public void trackContribution(String email, String repoName, String branch) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Repository_ repo = repositoryRepository.findByName(repoName)
                .orElseThrow(() -> new RuntimeException("Repository not found"));

        Contribution contribution = new Contribution();
        contribution.setUser(user);
        contribution.setRepository(repo);
        contribution.setStatus(ContributionStatus.PENDING);
        contribution.setCreatedAt(new Date());
        contribution.setBranch(branch); //  Add this line

        contributionRepository.save(contribution);
    }
}
