package com.thethirdlicense.services;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.thethirdlicense.models.Repository_;
import com.thethirdlicense.models.RepositoryAccess;
import com.thethirdlicense.models.Contribution;
import com.thethirdlicense.models.ContributionStatus;
import com.thethirdlicense.models.Company;
import com.thethirdlicense.models.User;
import com.thethirdlicense.models.AccessRequest;
import com.thethirdlicense.repositories.RepositoryRepository;
import com.thethirdlicense.repositories.AccessRequestRepository;
import com.thethirdlicense.repositories.ContributionRepository;
import com.thethirdlicense.repositories.UserRepository;
import com.thethirdlicense.security.UserPrincipal;
import com.thethirdlicense.services.ShareService;

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

    @Autowired
    public GitService(RepositoryRepository repositoryRepository, 
                      ContributionRepository contributionRepository, 
                      UserRepository userRepository, 
                      ShareService shareService) {
        this.repositoryRepository = repositoryRepository;
        this.contributionRepository = contributionRepository;
        this.userRepository = userRepository;
        this.shareService = shareService;
    }
    
    

    public String cloneRepository(UUID repoId, User user) throws GitAPIException {
        Repository_ repo = repositoryRepository.findById(repoId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found"));

        // Updated access check: use AccessRequest instead of RepositoryAccess
        if (!hasAccess(repo, user)) {
            throw new AccessDeniedException("You don't have permission to clone this repository.");
        }

        String clonePath = "C:\\repos\\cloned-repos\\" + repo.getName() + "-" + user.getUsername();
        File cloneDir = new File(clonePath);

        String gitUsername = user.getGitUsername();
        String gitToken = user.getGitToken();

        CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(gitUsername, gitToken);

        Git.cloneRepository()
            .setURI(repo.getGitUrl())
            .setDirectory(cloneDir)
            .setCredentialsProvider(credentialsProvider)
            .call();

        return "Repository cloned successfully at: " + clonePath;
    }

    @Autowired
    private AccessRequestRepository accessRequestRepository;

    public boolean hasAccess(Repository_ repo, User user) {
        return accessRequestRepository.existsByRepositoryAndUserAndStatus(
                repo, user, AccessRequest.Status.APPROVED
        );
    }

    public void pushCode(UUID repoId, int codeSize) throws GitAPIException, IOException {
        // Get the authenticated user
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal)) {
            throw new AccessDeniedException("User not authenticated");
        }
        UserPrincipal userPrincipal = (UserPrincipal) principal;
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Repository_ repo = repositoryRepository.findById(repoId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found"));

        if (!accessRequestRepository.existsByRepositoryAndUserAndStatus(repo, user, AccessRequest.Status.APPROVED)) {
            throw new AccessDeniedException("You don't have permission to push.");
        }

        String repoPath = "C:\\repos\\cloned-repos\\" + repo.getName() + "-" + user.getUsername();
        try (Git git = Git.open(new File(repoPath))) {
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
        // Simulate push
        Contribution contribution = new Contribution();
        contribution.setRepository(repo);
        contribution.setUser(user);
        contribution.setCodeSize(codeSize);
        contribution.setApproved(false); // Awaiting owner approval
        contribution.setContributionDate(new Date());

        contributionRepository.save(contribution);
    }

    public List<String> listPushes(UUID repoId, User user) throws GitAPIException, IOException {
        Repository_ repo = repositoryRepository.findById(repoId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found"));

        if (!accessRequestRepository.existsByRepositoryAndUserAndStatus(repo, user, AccessRequest.Status.APPROVED)) {
            throw new AccessDeniedException("You don't have access.");
        }

        List<String> commits = new ArrayList<>();
        try (Git git = Git.open(new File("/tmp/" + repo.getName()))) {
            Iterable<RevCommit> log = git.log().call();
            for (RevCommit commit : log) {
                commits.add(commit.getFullMessage());
            }
        }

        return commits;
    }


    public void approveContribution(UUID contributionId, User owner) {
        Contribution contribution = contributionRepository.findById(contributionId)
                .orElseThrow(() -> new IllegalArgumentException("Contribution not found"));

        Repository_ repo = contribution.getRepository();  // Now this should work

        if (!repo.getOwner().equals(owner)) {
            throw new AccessDeniedException("Only the repository owner can approve contributions.");
        }

        contribution.setApproved(true);
        contributionRepository.save(contribution);

        shareService.recalculateShares(repo.getCompany());  // Ensure repository has a company field
    }




}
