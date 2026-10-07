package com.thethirdlicense.services;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.thethirdlicense.models.Repository_;
import com.thethirdlicense.models.Contribution;
import com.thethirdlicense.models.ContributionStatus;
import com.thethirdlicense.models.User;
import com.thethirdlicense.models.AccessRequest;
import com.thethirdlicense.repositories.RepositoryRepository;
import com.thethirdlicense.repositories.AccessRequestRepository;
import com.thethirdlicense.repositories.ContributionRepository;
import com.thethirdlicense.repositories.RepositoryAccessRepository;
import com.thethirdlicense.repositories.UserRepository;
import com.thethirdlicense.security.UserPrincipal;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;


@Service
public class GitService {

    private final RepositoryRepository repositoryRepository;
    private final ContributionRepository contributionRepository;
    private final UserRepository userRepository;
    private final ShareService shareService;
    private final AccessRequestRepository accessRequestRepository;
    private final RepositoryAccessRepository repositoryAccessRepository;
    private final GitWorkspace workspace;

    @Autowired
    public GitService(RepositoryRepository repositoryRepository,
                      ContributionRepository contributionRepository,
                      UserRepository userRepository,
                      ShareService shareService,
                      AccessRequestRepository accessRequestRepository,
                      RepositoryAccessRepository repositoryAccessRepository,
                      GitWorkspace workspace) {
        this.repositoryRepository = repositoryRepository;
        this.contributionRepository = contributionRepository;
        this.userRepository = userRepository;
        this.shareService = shareService;
        this.accessRequestRepository = accessRequestRepository;
        this.repositoryAccessRepository = repositoryAccessRepository;
        this.workspace = workspace;
    }

    public String cloneRepository(UUID repoId, User user) throws GitAPIException {
        Repository_ repo = repositoryRepository.findById(repoId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found"));

        if (!hasAccess(repo, user)) {
            throw new AccessDeniedException("You don't have permission to clone this repository.");
        }

        File cloneDir = workspace.userClone(repo, user);
        if (new File(cloneDir, ".git").exists()) {
            return "Repository already cloned.";
        }

        Git.cloneRepository()
            .setURI(repo.getGitUrl())
            .setDirectory(cloneDir)
            .call()
            .close();

        return "Repository cloned successfully.";
    }

    public boolean isCompanyOwner(Repository_ repo, User user) {
        return repo.getCompany() != null
                && repo.getCompany().getOwner() != null
                && repo.getCompany().getOwner().getId().equals(user.getId());
    }

    /** Owner, approved access requester, or holder of a RepositoryAccess grant (e.g. share buyer). */
    public boolean hasAccess(Repository_ repo, User user) {
        return isCompanyOwner(repo, user)
                || accessRequestRepository.existsByRepositoryAndUserAndStatus(repo, user, AccessRequest.Status.APPROVED)
                || repositoryAccessRepository.findByUserAndRepository(user, repo).isPresent();
    }

    public void pushCode(UUID repoId, int codeSize) throws GitAPIException, IOException {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal)) {
            throw new AccessDeniedException("User not authenticated");
        }
        UserPrincipal userPrincipal = (UserPrincipal) principal;
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Repository_ repo = repositoryRepository.findById(repoId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found"));

        if (!hasAccess(repo, user)) {
            throw new AccessDeniedException("You don't have permission to push.");
        }

        try (Git git = Git.open(workspace.userClone(repo, user))) {
            Iterable<RevCommit> commits = git.log().setMaxCount(2).call();
            String newCommitHash = null, originalCommitHash = null;

            for (RevCommit commit : commits) {
                if (newCommitHash == null) newCommitHash = commit.getName();
                else if (originalCommitHash == null) originalCommitHash = commit.getName();
            }

            Contribution contribution = new Contribution();
            contribution.setRepository(repo);
            contribution.setUser(user);
            contribution.setCodeSize(codeSize);
            contribution.setApproved(false);
            contribution.setContributionDate(new Date());
            contribution.setOriginalCommitHash(originalCommitHash);
            contribution.setNewCommitHash(newCommitHash);
            contribution.setStatus(ContributionStatus.PENDING);

            contributionRepository.save(contribution);
        }
    }

    public List<String> listPushes(UUID repoId, User user) throws GitAPIException, IOException {
        Repository_ repo = repositoryRepository.findById(repoId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found"));

        if (!hasAccess(repo, user)) {
            throw new AccessDeniedException("You don't have access.");
        }

        List<String> commits = new ArrayList<>();
        try (Git git = Git.open(workspace.userClone(repo, user))) {
            Iterable<RevCommit> log = git.log().call();
            for (RevCommit commit : log) {
                commits.add(commit.getFullMessage());
            }
        }

        return commits;
    }


}
