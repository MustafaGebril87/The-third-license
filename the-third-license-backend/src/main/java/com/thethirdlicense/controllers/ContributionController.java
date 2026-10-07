package com.thethirdlicense.controllers;

import com.thethirdlicense.models.*;
import com.thethirdlicense.repositories.RepositoryRepository;
import com.thethirdlicense.security.UserPrincipal;
import com.thethirdlicense.services.ContributionService;
import com.thethirdlicense.services.GitService;
import com.thethirdlicense.services.GitWorkspace;
import com.thethirdlicense.services.ShareService;
import com.thethirdlicense.services.UserService;
import com.thethirdlicense.exceptions.UnauthorizedException;
import com.thethirdlicense.exceptions.ResourceNotFoundException;
import com.thethirdlicense.repositories.AccessRequestRepository;
import com.thethirdlicense.repositories.CompanyRepository;
import com.thethirdlicense.repositories.ContributionRepository;
import com.thethirdlicense.repositories.MergeRequestRepository;
import com.thethirdlicense.repositories.RepositoryAccessRepository;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/contributions")
public class ContributionController {

    private static final Logger log = LoggerFactory.getLogger(ContributionController.class);

    private ContributionService contributionService;
    private final RepositoryRepository repositoryRepository;
    private final ContributionRepository contributionRepository;
    private final ShareService shareService;
    private AccessRequestRepository accessRequestRepository;
    private final CompanyRepository companyRepository;
    private MergeRequestRepository mergeRequestRepository;
    private RepositoryAccessRepository repositoryAccessRepository;
    private final GitService gitService;
    private final GitWorkspace workspace;

    @Autowired
    public ContributionController(RepositoryAccessRepository repositoryAccessRepository, MergeRequestRepository mergeRequestRepository, CompanyRepository companyRepository, RepositoryRepository repositoryRepository, AccessRequestRepository accessRequestRepository, ContributionRepository contributionRepository, ShareService shareService, ContributionService contributionService, GitService gitService, GitWorkspace workspace, UserService userService) {
        this.contributionService = contributionService;
        this.contributionRepository = contributionRepository;
        this.shareService = shareService;
        this.accessRequestRepository = accessRequestRepository;
        this.repositoryRepository = repositoryRepository;
        this.companyRepository = companyRepository;
        this.mergeRequestRepository = mergeRequestRepository;
        this.repositoryAccessRepository = repositoryAccessRepository;
        this.gitService = gitService;
        this.workspace = workspace;
        this.userService = userService;
    }

    private UserService userService;

    @PostMapping("/{id}/decline")
    public ResponseEntity<String> declineContribution(@PathVariable("id") UUID id) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserDetails)) {
            throw new UnauthorizedException("Unauthorized");
        }

        UserPrincipal userPrincipal = (UserPrincipal) principal;
        User owner = userService.findById(userPrincipal.getId());
        if (owner == null) throw new ResourceNotFoundException("User not found");

        contributionService.declineContribution(id, owner);
        return ResponseEntity.ok("Contribution declined.");
    }
    @PostMapping("/{repositoryId}/request-access")
    public ResponseEntity<?> requestAccess(@PathVariable("repositoryId") UUID repositoryId) {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (!(principal instanceof UserDetails)) {
                throw new UnauthorizedException("Unauthorized");
            }

            UserPrincipal userPrincipal = (UserPrincipal) principal;
            User user = userService.findById(userPrincipal.getId());
            if (user == null) throw new ResourceNotFoundException("User not found");

            boolean accessGranted = accessRequestRepository.existsByRepositoryIdAndUserIdAndStatus(
            	    repositoryId, user.getId(), AccessRequest.Status.APPROVED
            	);
            if (accessGranted) {
                return ResponseEntity.badRequest().body("Access already granted to this repository.");
            }

            String message = contributionService.requestAccess(repositoryId, user);
            return ResponseEntity.ok(Map.of("message", message));

        } catch (Exception e) {
            return errorResponse(e, "Access request");
        }
    }


    @GetMapping("/my")
    public ResponseEntity<?> getMyContributions() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal)) {
            throw new UnauthorizedException("Unauthorized");
        }

        UserPrincipal userPrincipal = (UserPrincipal) principal;
        User user = userService.findById(userPrincipal.getId());
        if (user == null) throw new ResourceNotFoundException("User not found");

        List<ContributionDto> dtos = contributionService.findByUser(user)
                .stream().map(ContributionDto::new)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/pending")
    public ResponseEntity<?> getPendingContributions() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal)) {
            throw new UnauthorizedException("Unauthorized");
        }

        UserPrincipal userPrincipal = (UserPrincipal) principal;
        User owner = userService.findById(userPrincipal.getId());
        if (owner == null) throw new ResourceNotFoundException("User not found");

        List<ContributionDto> dtos = contributionService.findPendingByCompanyOwner(owner);
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/pending_requests")
    public ResponseEntity<?> getPendingRequests() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserDetails)) {
            throw new UnauthorizedException("Unauthorized");
        }

        UserPrincipal userPrincipal = (UserPrincipal) principal;
        User owner = userService.findById(userPrincipal.getId());
        if (owner == null) throw new ResourceNotFoundException("User not found");

        List<AccessRequestDto> pendingRequests = contributionService.findPendingRequestsByOwner(owner);
        return ResponseEntity.ok(pendingRequests);
    }
    
    @Transactional
    @PostMapping("/requests/{id}/approve")
    public ResponseEntity<String> approveRequest(@PathVariable("id") UUID id) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserDetails)) {
            throw new UnauthorizedException("Unauthorized");
        }

        UserPrincipal userPrincipal = (UserPrincipal) principal;
        User owner = userService.findById(userPrincipal.getId());
        if (owner == null) throw new ResourceNotFoundException("User not found");

        AccessRequest accessRequest = accessRequestRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Access request not found"));

        Repository_ repository = accessRequest.getRepository();
        User requester = accessRequest.getRequester();

        // Confirm ownership
        if (!repository.getCompany().getOwner().getId().equals(owner.getId())) {
            throw new UnauthorizedException("Only the company owner can approve access.");
        }

        // Mark the request approved
        accessRequest.setApproved(AccessRequest.Status.APPROVED);
        accessRequestRepository.save(accessRequest);

        // Grant contributor access if not already granted
        Optional<RepositoryAccess> existing = repositoryAccessRepository.findByUserAndRepository(requester, repository);
        if (existing.isEmpty()) {
            RepositoryAccess access = new RepositoryAccess();
            access.setUser(requester);
            access.setRepository(repository);
            access.setAccessLevel(RepositoryAccess.AccessLevel.CONTRIBUTOR);
            access.setGrantedAt(LocalDateTime.now());
            repositoryAccessRepository.save(access);
        }

        return ResponseEntity.ok("Access request approved.");
    }


    @PostMapping("/requests/{id}/decline")
    public ResponseEntity<String> declineRequest(@PathVariable("id") UUID id) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserDetails)) {
            throw new UnauthorizedException("Unauthorized");
        }

        UserPrincipal userPrincipal = (UserPrincipal) principal;
        User owner = userService.findById(userPrincipal.getId());
        if (owner == null) throw new ResourceNotFoundException("User not found");

        contributionService.declineRequest(id, owner);
        return ResponseEntity.ok("Access request declined.");
    }

    // ── Git operations ────────────────────────────────────────────────────────
    // Every endpoint below: (1) checks the caller may touch this repository,
    // (2) validates branch names, (3) resolves every user-supplied file path with
    // GitWorkspace.resolveInside so nothing can be read or written outside the clone.

    private User currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            throw new UnauthorizedException("Unauthorized");
        }
        return userService.findById(userPrincipal.getId());
    }

    private Repository_ findRepo(UUID repositoryId) {
        return repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));
    }

    private void requireAccess(Repository_ repo, User user) {
        if (!gitService.hasAccess(repo, user)) {
            throw new AccessDeniedException("You don't have access to this repository.");
        }
    }

    private void requireOwner(Repository_ repo, User user) {
        if (!gitService.isCompanyOwner(repo, user)) {
            throw new AccessDeniedException("Only the company owner can do this.");
        }
    }

    /** Converts expected failures to clean 4xx responses; logs and hides details of anything else. */
    private ResponseEntity<String> errorResponse(Exception e, String action) {
        if (e instanceof IllegalArgumentException || e instanceof IllegalStateException) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
        if (e instanceof AccessDeniedException || e instanceof UnauthorizedException) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
        if (e instanceof ResourceNotFoundException) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
        log.error("{} failed", action, e);
        return ResponseEntity.status(500).body(action + " failed.");
    }

    /** Git pathspec (forward slashes, relative to the working copy) for a file already validated by resolveInside. */
    private static String gitPath(File root, File target) {
        return root.toPath().toAbsolutePath().normalize()
                .relativize(target.toPath().toAbsolutePath().normalize())
                .toString().replace('\\', '/');
    }

    private static void writeResolvedFiles(Git git, File root, List<MergeResolveRequest.FileMergeItem> files) throws Exception {
        for (MergeResolveRequest.FileMergeItem item : files) {
            if (item.getFilePath() == null || item.getMergedContent() == null) {
                throw new IllegalArgumentException("filePath and mergedContent are required for all files.");
            }
            File target = GitWorkspace.resolveInside(root, item.getFilePath());
            Files.createDirectories(target.getParentFile().toPath());
            Files.writeString(target.toPath(), item.getMergedContent());
            git.add().addFilepattern(gitPath(root, target)).call();
        }
    }

    private void ensureCloned(Repository_ repo, File cloneDir) throws Exception {
        if (!new File(cloneDir, ".git").exists()) {
            Git.cloneRepository()
                    .setURI(repo.getGitUrl())
                    .setDirectory(cloneDir)
                    .setCloneAllBranches(true)
                    .call()
                    .close();
        }
    }

    @PostMapping("/merge-branch")
    public ResponseEntity<?> mergeBranch(@RequestBody MergeResolveRequest request) {
        try {
            User user = currentUser();
            Repository_ repo = findRepo(request.getRepositoryId());

            List<MergeResolveRequest.FileMergeItem> files = request.getFiles();
            if (files == null || files.isEmpty()) {
                return ResponseEntity.badRequest().body("No files provided for merging.");
            }

            if ("PULL_CONFLICT".equalsIgnoreCase(request.getMergeType())) {
                requireAccess(repo, user);
                File userRepoDir = workspace.userClone(repo, user);
                if (!new File(userRepoDir, ".git").exists()) {
                    return ResponseEntity.status(400).body("Repository is not cloned.");
                }

                try (Git git = Git.open(userRepoDir)) {
                    git.checkout().setName("main").call();
                    writeResolvedFiles(git, userRepoDir, files);

                    RevCommit commit = git.commit()
                            .setMessage("Resolved pull conflict locally for " + files.size() + " file(s)")
                            .setAuthor(user.getUsername(), user.getEmail())
                            .call();

                    return ResponseEntity.ok("Local merge applied: " + commit.getName());
                }
            }

            // Owner resolving merge remotely
            requireOwner(repo, user);
            String branch = GitWorkspace.requireReadableBranch(request.getBranch());
            File cloneDir = workspace.ownerClone(repo);
            ensureCloned(repo, cloneDir);

            try (Git git = Git.open(cloneDir)) {
                git.fetch().setRemote("origin").call();
                git.checkout().setName("main").call();
                git.pull().call();

                // Commit main is at before the merge, so the contribution is measured as this change only
                ObjectId baseCommit = git.getRepository().resolve("HEAD");

                writeResolvedFiles(git, cloneDir, files);

                RevCommit commit = git.commit()
                        .setMessage("Manually merged " + files.size() + " file(s) from " + branch)
                        .setAuthor(user.getUsername(), user.getEmail())
                        .call();

                git.push().setRemote("origin").add("main").call();

                Optional<Contribution> contributionOpt = contributionRepository
                        .findByRepositoryIdAndBranchAndStatus(repo.getId(), branch, ContributionStatus.PENDING)
                        .stream()
                        .findFirst();

                contributionOpt.ifPresent(c -> {
                    if (baseCommit != null) c.setOriginalCommitHash(baseCommit.getName());
                    c.setNewCommitHash(commit.getName());
                    contributionRepository.save(c);
                    contributionService.approveContribution(c.getId(), user);
                });

                Optional<MergeRequest> mergeRequestOpt = mergeRequestRepository
                        .findByRepositoryIdAndBranchAndStatus(repo.getId(), branch, MergeRequestStatus.PENDING)
                        .stream()
                        .findFirst();

                mergeRequestOpt.ifPresent(mr -> {
                    mr.setStatus(MergeRequestStatus.RESOLVED);
                    mergeRequestRepository.save(mr);
                });

                return ResponseEntity.ok("Remote merge applied and pushed to main: " + commit.getName());
            }

        } catch (Exception e) {
            return errorResponse(e, "Merge");
        }
    }

    private String readFileContent(Repository repository, ObjectId commitId, String filePath) throws IOException {
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();

            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);
                treeWalk.setFilter(PathFilter.create(filePath));

                if (!treeWalk.next()) {
                    return null; // File does not exist at this commit
                }

                ObjectId objectId = treeWalk.getObjectId(0);
                ObjectLoader loader = repository.open(objectId);
                return new String(loader.getBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    @GetMapping("/merge/diff")
    public ResponseEntity<?> getFileDiff(
            @RequestParam UUID repositoryId,
            @RequestParam String branch,
            @RequestParam List<String> filePath) {

        try {
            User user = currentUser();
            Repository_ repo = findRepo(repositoryId);
            requireAccess(repo, user);
            GitWorkspace.requireReadableBranch(branch);

            File cloneDir = gitService.isCompanyOwner(repo, user)
                    ? workspace.ownerClone(repo)
                    : workspace.userClone(repo, user);
            ensureCloned(repo, cloneDir);

            try (Git git = Git.open(cloneDir)) {
                git.fetch().setRemote("origin").call();

                Repository jgitRepo = git.getRepository();
                ObjectId baseCommitId = jgitRepo.resolve("refs/remotes/origin/main^{commit}");
                ObjectId branchCommitId = jgitRepo.resolve("refs/remotes/origin/" + branch + "^{commit}");

                if (branchCommitId == null) {
                    return ResponseEntity.status(404).body("Branch '" + branch + "' not found on remote.");
                }
                if (baseCommitId == null) {
                    return ResponseEntity.status(404).body("Main branch has no commits.");
                }

                List<Map<String, Object>> result = new ArrayList<>();

                for (String path : filePath) {
                    // Paths here are only looked up inside git trees, but reject traversal anyway
                    GitWorkspace.resolveInside(cloneDir, path);

                    String baseContent = readFileContent(jgitRepo, baseCommitId, path);
                    String branchContent = readFileContent(jgitRepo, branchCommitId, path);
                    if (branchContent == null) {
                        return ResponseEntity.status(404).body("File not found in branch: " + path);
                    }

                    Map<String, Object> fileData = new HashMap<>();
                    fileData.put("filePath", path);
                    fileData.put("base", baseContent != null ? baseContent : "");
                    fileData.put("branch", branchContent);
                    fileData.put("type", baseContent != null ? "modify" : "new");
                    result.add(fileData);
                }

                return ResponseEntity.ok(Map.of("conflicts", result));
            }

        } catch (Exception e) {
            return errorResponse(e, "Generating diff");
        }
    }

    @GetMapping("/merge/files")
    public ResponseEntity<?> getChangedFiles(
            @RequestParam UUID repositoryId,
            @RequestParam String branch) {

        try {
            User user = currentUser();
            Repository_ repo = findRepo(repositoryId);
            requireOwner(repo, user);
            GitWorkspace.requireReadableBranch(branch);

            File repoDir = workspace.ownerClone(repo);
            ensureCloned(repo, repoDir);

            try (Git git = Git.open(repoDir)) {
                Repository jgitRepo = git.getRepository();
                git.fetch().setRemote("origin").call();

                ObjectId mainId = jgitRepo.resolve("refs/remotes/origin/main^{commit}");
                ObjectId branchId = jgitRepo.resolve("refs/remotes/origin/" + branch + "^{commit}");

                if (mainId == null || branchId == null) {
                    return ResponseEntity.status(404).body("Could not resolve both commits.");
                }

                List<DiffEntry> diffs;
                try (ObjectReader reader = jgitRepo.newObjectReader()) {
                    CanonicalTreeParser mainTree = new CanonicalTreeParser();
                    mainTree.reset(reader, jgitRepo.parseCommit(mainId).getTree());

                    CanonicalTreeParser branchTree = new CanonicalTreeParser();
                    branchTree.reset(reader, jgitRepo.parseCommit(branchId).getTree());

                    diffs = git.diff()
                            .setOldTree(mainTree)
                            .setNewTree(branchTree)
                            .call();
                }

                List<String> filePaths = diffs.stream()
                        .map(DiffEntry::getNewPath)
                        .filter(path -> !path.equals(DiffEntry.DEV_NULL))
                        .collect(Collectors.toList());

                return ResponseEntity.ok(filePaths);
            }

        } catch (Exception e) {
            return errorResponse(e, "Listing changed files");
        }
    }

    @PostMapping("/merge/resolve")
    public ResponseEntity<?> applyResolvedMerge(@RequestBody MergeResolveRequest request) {
        try {
            User user = currentUser();
            Repository_ repo = findRepo(request.getRepositoryId());
            requireOwner(repo, user);

            if (request.getFiles() == null || request.getFiles().isEmpty()) {
                return ResponseEntity.badRequest().body("No files provided for merging.");
            }

            File repoDir = workspace.ownerClone(repo);
            ensureCloned(repo, repoDir);

            try (Git git = Git.open(repoDir)) {
                git.checkout().setName("main").call();
                writeResolvedFiles(git, repoDir, request.getFiles());

                RevCommit commit = git.commit()
                        .setMessage("Manual merge for " + request.getFiles().size() + " file(s)")
                        .setAuthor(user.getUsername(), user.getEmail())
                        .call();

                return ResponseEntity.ok("Merged and committed to main: " + commit.getName());
            }

        } catch (Exception e) {
            return errorResponse(e, "Applying merge");
        }
    }

    @GetMapping("/pull-file")
    public ResponseEntity<?> pullMultipleFiles(
            @RequestParam UUID repositoryId,
            @RequestParam String branch,
            @RequestParam List<String> filePath,
            @RequestParam(defaultValue = "pull") String mode) {

        try {
            User user = currentUser();
            Repository_ repo = findRepo(repositoryId);
            requireAccess(repo, user);
            GitWorkspace.requireReadableBranch(branch);

            File cloneDir = workspace.userClone(repo, user);
            if (!new File(cloneDir, ".git").exists()) {
                return ResponseEntity.status(403).body("You must clone the repository first before pulling files.");
            }

            try (Git git = Git.open(cloneDir)) {
                git.fetch().setRemote("origin").call();
                git.checkout().setName("main").call();
                git.reset().setMode(ResetCommand.ResetType.HARD).setRef("origin/main").call();

                ObjectId mainCommitId = git.getRepository().resolve("refs/heads/main^{commit}");
                ObjectId branchCommitId = git.getRepository().resolve("refs/remotes/origin/" + branch + "^{commit}");

                if (mainCommitId == null || branchCommitId == null) {
                    return ResponseEntity.status(404).body("Branch not found.");
                }

                List<Map<String, String>> pulledFiles = new ArrayList<>();
                List<String> conflictedFiles = new ArrayList<>();

                for (String path : filePath) {
                    File file = GitWorkspace.resolveInside(cloneDir, path);

                    String mainContent = readFileContent(git.getRepository(), mainCommitId, path);
                    String branchContent = readFileContent(git.getRepository(), branchCommitId, path);

                    if (mainContent == null && branchContent == null) {
                        return ResponseEntity.status(404).body("File not found in repository: " + path);
                    }
                    if (!Objects.equals(mainContent, branchContent)) {
                        conflictedFiles.add(path);
                        continue;
                    }

                    // Content comes from the git tree, never from arbitrary files on disk
                    if (!file.exists()) {
                        Files.createDirectories(file.getParentFile().toPath());
                        Files.writeString(file.toPath(), mainContent, StandardCharsets.UTF_8);
                    }

                    pulledFiles.add(Map.of(
                            "filePath", path,
                            "branch", branch,
                            "content", mainContent
                    ));
                }

                if (!conflictedFiles.isEmpty()) {
                    return ResponseEntity.status(409).body("Merge conflict detected in files:\n" + String.join(", ", conflictedFiles));
                }

                return ResponseEntity.ok(Map.of(
                        "pulledFiles", pulledFiles,
                        "mode", "pull"
                ));
            }

        } catch (Exception e) {
            return errorResponse(e, "Pulling files");
        }
    }

    @PostMapping("/push-files")
    public ResponseEntity<?> pushFiles(
            @RequestParam UUID repositoryId,
            @RequestParam String branch,
            @RequestParam("files") List<MultipartFile> files,
            Authentication auth) {

        try {
            if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal userPrincipal)) {
                return ResponseEntity.status(401).body("Unauthorized");
            }

            User user = userService.findById(userPrincipal.getId());
            Repository_ repo = findRepo(repositoryId);
            requireAccess(repo, user);
            String targetBranch = GitWorkspace.requirePushableBranch(branch);

            if (files == null || files.isEmpty()) {
                return ResponseEntity.badRequest().body("No files provided.");
            }

            File repoDir = workspace.userClone(repo, user);
            if (!new File(repoDir, ".git").exists()) {
                return ResponseEntity.badRequest().body("Clone the repository before pushing.");
            }

            try (Git git = Git.open(repoDir)) {
                Repository jgitRepo = git.getRepository();

                if (jgitRepo.getRepositoryState() == RepositoryState.MERGING) {
                    return ResponseEntity.status(409).body("Repository is in merge conflict. Resolve it before pushing.");
                }

                git.fetch().setRemote("origin").call();
                try {
                    git.checkout().setName(targetBranch).call();
                } catch (RefNotFoundException e) {
                    Ref remoteBranch = jgitRepo.findRef("refs/remotes/origin/" + targetBranch);
                    git.checkout()
                        .setCreateBranch(true)
                        .setName(targetBranch)
                        .setStartPoint(remoteBranch != null ? "origin/" + targetBranch : "origin/main")
                        .call();
                }

                for (MultipartFile file : files) {
                    File target = GitWorkspace.resolveInside(repoDir, file.getOriginalFilename());
                    Files.createDirectories(target.getParentFile().toPath());
                    Files.write(target.toPath(), file.getBytes());
                    git.add().addFilepattern(gitPath(repoDir, target)).call();
                }

                git.commit()
                        .setMessage("User push: " + user.getUsername())
                        .setAuthor(user.getUsername(), user.getEmail())
                        .call();

                // Push only this branch — never main
                Iterable<PushResult> results = git.push()
                        .setRemote("origin")
                        .setRefSpecs(new RefSpec("refs/heads/" + targetBranch + ":refs/heads/" + targetBranch))
                        .call();
                for (PushResult result : results) {
                    for (RemoteRefUpdate update : result.getRemoteUpdates()) {
                        RemoteRefUpdate.Status status = update.getStatus();
                        if (status != RemoteRefUpdate.Status.OK && status != RemoteRefUpdate.Status.UP_TO_DATE) {
                            return ResponseEntity.status(409).body("Push rejected (" + status + "). Pull the latest changes and try again.");
                        }
                    }
                }

                contributionService.trackContribution(user, repo, targetBranch);

                return ResponseEntity.ok("Push successful");
            }

        } catch (Exception e) {
            return errorResponse(e, "Push");
        }
    }
}
