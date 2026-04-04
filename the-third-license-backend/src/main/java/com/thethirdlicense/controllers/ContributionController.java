package com.thethirdlicense.controllers;

import com.thethirdlicense.models.*;
import com.thethirdlicense.models.MergeRequest;
import org.eclipse.jgit.api.ResetCommand;

import com.thethirdlicense.repositories.RepositoryRepository;
import com.thethirdlicense.security.UserPrincipal;
import com.thethirdlicense.services.ContributionService;
import com.thethirdlicense.services.ShareService;
import com.thethirdlicense.services.TokenService;
import com.thethirdlicense.services.UserService;
import com.thethirdlicense.exceptions.UnauthorizedException;
import com.thethirdlicense.exceptions.ResourceNotFoundException;

import com.thethirdlicense.Util.ApplicationProperties;
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
import com.thethirdlicense.repositories.MergeRequestRepository;
import com.thethirdlicense.repositories.RepositoryAccessRepository;

import org.springframework.transaction.annotation.Transactional;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.api.MergeCommand;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.lib.Ref;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;



import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import java.io.IOException;
import java.math.BigDecimal;
@RestController
@RequestMapping("/api/contributions")
public class ContributionController {

    private ContributionService contributionService;
    private final RepositoryRepository repositoryRepository;
    private final ContributionRepository contributionRepository;
    private final ShareService shareService;
    private AccessRequestRepository accessRequestRepository;
    @Autowired
    private final CompanyRepository companyRepository;
    @Autowired
    private TokenService currencyService;
    
    private final ApplicationProperties applicationProperties;
    @Autowired
    private MergeRequestRepository mergeRequestRepository;

    @Autowired
    private RepositoryAccessRepository repositoryAccessRepository;
    @Autowired
    public ContributionController(RepositoryAccessRepository repositoryAccessRepository, MergeRequestRepository mergeRequestRepository,ApplicationProperties applicationProperties,CompanyRepository companyRepository,RepositoryRepository repositoryRepository, AccessRequestRepository accessRequestRepository,ContributionRepository contributionRepository, ShareService shareService,ContributionService contributionService ) {
        this.contributionService = contributionService;
        this.contributionRepository = contributionRepository;
        this.shareService = shareService;
		this.accessRequestRepository = accessRequestRepository;
		this.repositoryRepository = repositoryRepository;
        this.accessRequestRepository = accessRequestRepository;
        this.companyRepository = companyRepository;
        this.applicationProperties= applicationProperties; 
        this.mergeRequestRepository = mergeRequestRepository;
        this.repositoryAccessRepository = repositoryAccessRepository;
    }

    @Autowired
    private UserService userService;

//    @PostMapping("/{id}/approve")
//    public ResponseEntity<String> approveContribution(@PathVariable("id") UUID id) {
//        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
//        if (!(principal instanceof UserPrincipal)) {
//            throw new UnauthorizedException("Unauthorized");
//        }
//
//        UserPrincipal userPrincipal = (UserPrincipal) principal;
//        User owner = userService.findById(userPrincipal.getId());
//        if (owner == null) throw new ResourceNotFoundException("User not found");
//
//        contributionService.approveContribution(id, owner);
//        return ResponseEntity.ok("Contribution approved.");
//    }

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
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
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
    @PostMapping("/merge-branch")
    public ResponseEntity<?> mergeBranch(@RequestBody MergeResolveRequest request) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal)) {
            return ResponseEntity.status(403).body("Unauthorized");
        }

        UserPrincipal userPrincipal = (UserPrincipal) principal;
        User user = userService.findById(userPrincipal.getId());

        try {
            Repository_ repo = repositoryRepository.findById(request.getRepositoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));

            List<MergeResolveRequest.FileMergeItem> files = request.getFiles();
            if (files == null || files.isEmpty()) {
                return ResponseEntity.badRequest().body("No files provided for merging.");
            }

            String mergeType = request.getMergeType();

            if ("PULL_CONFLICT".equalsIgnoreCase(mergeType)) {
                String userClonePath = "C:\\repos\\cloned-repos\\" + repo.getName() + "-" + user.getUsername();
                File userRepoDir = new File(userClonePath);

                if (!new File(userRepoDir, ".git").exists()) {
                    return ResponseEntity.status(400).body("Repository is not cloned.");
                }

                try (Git git = Git.open(userRepoDir)) {
                    git.checkout().setName("main").call();

                    for (MergeResolveRequest.FileMergeItem item : files) {
                        File file = new File(userRepoDir, item.getFilePath());
                        Files.writeString(file.toPath(), item.getMergedContent());
                        git.add().addFilepattern(item.getFilePath()).call();
                    }

                    RevCommit commit = git.commit()
                            .setMessage("Resolved pull conflict locally for " + files.size() + " file(s)")
                            .setAuthor(user.getUsername(), user.getEmail())
                            .call();

                    return ResponseEntity.ok("Local merge applied: " + commit.getName());
                }
            }

            // Owner resolving merge remotely
            Company company = repo.getCompany();
            if (!company.getOwner().getId().equals(user.getId())) {
                return ResponseEntity.status(403).body("Only the company owner can merge branches.");
            }

            String ownerClonePath = "C:\\repos\\cloned-repos\\" + repo.getName() + "-owner";
            File cloneDir = new File(ownerClonePath);

            if (!new File(cloneDir, ".git").exists()) {
                Git.cloneRepository()
                        .setURI(repo.getLocalPath())
                        .setDirectory(cloneDir)
                        .setBare(false)
                        .setCloneAllBranches(true)
                        .setRemote("origin")
                        .call()
                        .close();
            }

            try (Git git = Git.open(cloneDir)) {
                git.fetch().setRemote("origin").call();
                git.checkout().setName("main").call();
                git.pull().call();

                for (MergeResolveRequest.FileMergeItem item : files) {
                    if (item.getFilePath() == null || item.getMergedContent() == null) {
                        return ResponseEntity.badRequest().body("filePath and mergedContent are required for all files.");
                    }

                    File file = new File(cloneDir, item.getFilePath());
                    Files.writeString(file.toPath(), item.getMergedContent());
                    git.add().addFilepattern(item.getFilePath()).call();
                }

                RevCommit commit = git.commit()
                        .setMessage("Manually merged " + files.size() + " file(s)")
                        .setAuthor(user.getUsername(), user.getEmail())
                        .call();

                git.push().setRemote("origin").add("main").call();

                //  Approve the matching pending contribution
                Optional<Contribution> contributionOpt = contributionRepository
                        .findByRepositoryIdAndBranchAndStatus(repo.getId(), request.getBranch(), ContributionStatus.PENDING)
                        .stream()
                        .findFirst();

                contributionOpt.ifPresent(c -> {
                    c.setNewCommitHash(commit.getName()); //  Set the new commit hash
                    contributionRepository.save(c);       //  Persist update before approval
                    contributionService.approveContribution(c.getId(), user);
                });

                //  Resolve the matching pending merge request
                Optional<MergeRequest> mergeRequestOpt = mergeRequestRepository
                        .findByRepositoryIdAndBranchAndStatus(repo.getId(), request.getBranch(), MergeRequestStatus.PENDING)
                        .stream()
                        .findFirst();

                mergeRequestOpt.ifPresent(mr -> {
                    mr.setStatus(MergeRequestStatus.RESOLVED);
                    mergeRequestRepository.save(mr);
                });

                return ResponseEntity.ok("Remote merge applied and pushed to main: " + commit.getName());
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Merge failed: " + e.getMessage());
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
                    return ""; // File does not exist at this commit
                }

                ObjectId objectId = treeWalk.getObjectId(0);
                ObjectLoader loader = repository.open(objectId);
                return new String(loader.getBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    
    public String getCurrentBranch(UUID repositoryId, String username) throws IOException {
        Repository_ repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));

        String clonePath = "C:\\repos\\cloned-repos\\" + repo.getName() + "-" + username;
        File repoDir = new File(clonePath);

        try (Git git = Git.open(repoDir)) {
            return git.getRepository().getBranch();  // returns the current checked-out branch
        }
    }

	

    @GetMapping("/merge/diff")
    public ResponseEntity<?> getFileDiff(
            @RequestParam UUID repositoryId,
            @RequestParam String branch,
            @RequestParam List<String> filePath) {

        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal)) {
            return ResponseEntity.status(403).body("Unauthorized");
        }

        UserPrincipal userPrincipal = (UserPrincipal) principal;
        User user = userService.findById(userPrincipal.getId());

        try {
            Repository_ repo = repositoryRepository.findById(repositoryId)
                    .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));

            String clonePath = "C:\\repos\\cloned-repos\\" + repo.getName() + "-" + user.getUsername();
            File cloneDir = new File(clonePath);

            if (!cloneDir.exists()) {
                System.out.println("Cloning repository to: " + clonePath);
                Git.cloneRepository()
                        .setURI(repo.getLocalPath())
                        .setDirectory(cloneDir)
                        .call()
                        .close();
            }

            try (Git git = Git.open(cloneDir)) {
                System.out.println("Fetching from remote...");
                git.fetch().setRemote("origin").call();

                Ref remoteRef = git.getRepository().findRef("refs/remotes/origin/" + branch);
                if (remoteRef == null) {
                    return ResponseEntity.status(404).body("Branch '" + branch + "' not found on remote.");
                }

                boolean branchExistsLocally = git.getRepository().findRef("refs/heads/" + branch) != null;
                if (!branchExistsLocally) {
                    git.checkout()
                            .setCreateBranch(true)
                            .setName(branch)
                            .setStartPoint("origin/" + branch)
                            .call();
                } else {
                    git.checkout().setName(branch).call();
                }

                ObjectId baseCommitId = git.getRepository().resolve("refs/heads/main^{commit}");
                ObjectId branchCommitId = git.getRepository().resolve("refs/heads/" + branch + "^{commit}");

                if (baseCommitId == null || branchCommitId == null) {
                    return ResponseEntity.status(404).body("One of the branches does not have any commits.");
                }

                List<Map<String, Object>> result = new ArrayList<>();

                for (String path : filePath) {
                    try {
                        String baseContent = "";
                        boolean fileInBaseExists = true;

                        try {
                            baseContent = readFileContent(git.getRepository(), baseCommitId, path);
                        } catch (Exception e) {
                            System.out.println("File not in base (main): " + path);
                            fileInBaseExists = false;
                        }

                        String branchContent;
                        try {
                            branchContent = readFileContent(git.getRepository(), branchCommitId, path);
                        } catch (Exception e) {
                            return ResponseEntity.status(404).body("File not found in branch: " + path);
                        }

                        Map<String, Object> fileData = new HashMap<>();
                        fileData.put("filePath", path);
                        fileData.put("base", fileInBaseExists ? baseContent : "");
                        fileData.put("branch", branchContent);
                        fileData.put("type", fileInBaseExists ? "modify" : "new");

                        result.add(fileData);

                    } catch (Exception e) {
                        System.err.println("Error processing file " + path + ": " + e.getMessage());
                        return ResponseEntity.status(500).body("Failed to load diff for " + path);
                    }
                }

                return ResponseEntity.ok(Map.of("conflicts", result));
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error while generating diff: " + e.getMessage());
        }
    }
    @GetMapping("/merge/files")
    public ResponseEntity<?> getChangedFiles(
            @RequestParam UUID repositoryId,
            @RequestParam String branch) {

        System.out.println(">>> [merge/files] Request received");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth.getPrincipal();

        if (!(principal instanceof UserPrincipal)) {
            return ResponseEntity.status(403).body("Unauthorized");
        }

        UserPrincipal userPrincipal = (UserPrincipal) principal;
        User user = userService.findById(userPrincipal.getId());

        try {
            Repository_ repo = repositoryRepository.findById(repositoryId)
                    .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));

            Company company = repo.getCompany();
            if (!company.getOwner().getId().equals(user.getId())) {
                return ResponseEntity.status(403).body("Only the company owner can merge.");
            }

            String repoFolder = "C:\\repos\\cloned-repos\\" + repo.getName() + "-" + user.getUsername();
            System.out.println(">>> Repository path: " + repoFolder);

            File repoDir = new File(repoFolder);
            File gitDir = new File(repoDir, ".git");

            if (!repoDir.exists() || !gitDir.exists()) {
                System.out.println(">>> Cloning repository...");
                Git.cloneRepository()
                        .setURI(repo.getLocalPath())
                        .setDirectory(repoDir)
                        .call()
                        .close();
            }

            try (Repository jgitRepo = new FileRepositoryBuilder()
                    .setGitDir(gitDir)
                    .build();
                 Git git = new Git(jgitRepo)) {

                System.out.println(">>> Fetching latest changes...");
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
                        .filter(path -> !path.equals("/dev/null"))
                        .collect(Collectors.toList());

                System.out.println(">>> Changed files: " + filePaths);
                return ResponseEntity.ok(filePaths);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error while listing changed files: " + e.getMessage());
        }
    }

    @PostMapping("/merge/resolve")
    public ResponseEntity<?> applyResolvedMerge(@RequestBody MergeResolveRequest request) {

        try {
            Repository_ repo = repositoryRepository.findById(request.getRepositoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));
            Company company = repo.getCompany();

            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (!(principal instanceof UserPrincipal)) {
                return ResponseEntity.status(403).body("Unauthorized");
            }
            UserPrincipal userPrincipal = (UserPrincipal) principal;
            User user = userService.findById(userPrincipal.getId());

            if (!company.getOwner().getId().equals(user.getId())) {
                return ResponseEntity.status(403).body("Only the owner can resolve merges.");
            }

            String clonePath = "C:\\repos\\cloned-repos\\" + repo.getName() + "-owner";
            File repoDir = new File(clonePath);

            try (Git git = Git.open(repoDir)) {
                // Checkout main branch
                git.checkout().setName("main").call();

                // Loop through each file and write + stage it
                for (MergeResolveRequest.FileMergeItem item : request.getFiles()) {
                    File file = new File(repoDir, item.getFilePath());
                    Files.writeString(file.toPath(), item.getMergedContent());
                    git.add().addFilepattern(item.getFilePath()).call();
                }

                RevCommit commit = git.commit()
                        .setMessage("Manual merge for " + request.getFiles().size() + " file(s)")
                        .setAuthor(user.getUsername(), user.getEmail())
                        .call();

                return ResponseEntity.ok("Merged and committed to main: " + commit.getName());
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Failed to apply merge: " + e.getMessage());
        }
    }
    @GetMapping("/pull-file")
    public ResponseEntity<?> pullMultipleFiles(
            @RequestParam UUID repositoryId,
            @RequestParam String branch,
            @RequestParam List<String> filePath,
            @RequestParam(defaultValue = "pull") String mode) {

        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            return ResponseEntity.status(403).body("Unauthorized");
        }

        User user = userService.findById(userPrincipal.getId());
        Repository_ repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));

        String clonePath = "C:\\repos\\cloned-repos\\" + repo.getName() + "-" + user.getUsername();
        File cloneDir = new File(clonePath);

        if (!new File(cloneDir, ".git").exists()) {
            return ResponseEntity.status(403).body("You must clone the repository first before pulling files.");
        }

        try (Git git = Git.open(cloneDir)) {
            git.fetch().setRemote("origin").call();

            // Switch to main branch
            git.checkout().setName("main").call();

            // Ensure local main is up-to-date with remote
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef("origin/main").call();

            ObjectId mainCommitId = git.getRepository().resolve("refs/heads/main^{commit}");
            ObjectId branchCommitId = git.getRepository().resolve("refs/remotes/origin/" + branch + "^{commit}");

            if (mainCommitId == null || branchCommitId == null) {
                return ResponseEntity.status(404).body("Branch not found.");
            }

            List<Map<String, String>> pulledFiles = new ArrayList<>();
            List<String> conflictedFiles = new ArrayList<>();

            for (String path : filePath) {
                String mainContent = readFileContent(git.getRepository(), mainCommitId, path);
                String branchContent = readFileContent(git.getRepository(), branchCommitId, path);

                if (!Objects.equals(mainContent, branchContent)) {
                    conflictedFiles.add(path);
                    continue;
                }

                File file = new File(cloneDir, path);
                // If the file is missing locally, create it from main branch content
                if (!file.exists()) {
                    Files.createDirectories(file.getParentFile().toPath());
                    Files.writeString(file.toPath(), mainContent, StandardCharsets.UTF_8);
                }

                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                pulledFiles.add(Map.of(
                        "filePath", path,
                        "branch", branch,
                        "content", content
                ));
            }

            if (!conflictedFiles.isEmpty()) {
                return ResponseEntity.status(409).body("Merge conflict detected in files:\n" + String.join(", ", conflictedFiles));
            }

            return ResponseEntity.ok(Map.of(
                    "pulledFiles", pulledFiles,
                    "mode", "pull"
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error pulling files: " + e.getMessage());
        }
    }



    @PostMapping("/push-files")
    public ResponseEntity<?> pushFiles(
            @RequestParam UUID repositoryId,
            @RequestParam String branch,
            @RequestParam("files") List<MultipartFile> files,
            Authentication auth) {

        if (!(auth.getPrincipal() instanceof UserPrincipal userPrincipal)) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        User user = userService.findById(userPrincipal.getId());
        Repository_ repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));

        String repoPath = "C:\\repos\\cloned-repos\\" + repo.getName() + "-" + user.getUsername();
        File repoDir = new File(repoPath);

        try (Git git = Git.open(repoDir)) {
            Repository jgitRepo = git.getRepository();

            // If repo is in MERGING state, deny push
            if (jgitRepo.getRepositoryState() == RepositoryState.MERGING) {
                return ResponseEntity.status(409).body("Repository is in merge conflict. Resolve it before pushing.");
            }

            try {
                git.checkout().setName(branch).call();
            } catch (RefNotFoundException e) {
                git.checkout()
                    .setCreateBranch(true)
                    .setName(branch)
                    .setStartPoint("origin/main") // Change to origin/master if needed
                    .call();
            }


            for (MultipartFile file : files) {
                File target = new File(repoDir, file.getOriginalFilename());
                Files.write(target.toPath(), file.getBytes());
                git.add().addFilepattern(file.getOriginalFilename()).call();
            }

            git.commit().setMessage("User push: " + user.getUsername()).call();
            git.push().setRemote("origin").call();

            contributionService.trackContribution(user.getEmail(), repo.getName(), branch);

            return ResponseEntity.ok("Push successful");

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error during push: " + e.getMessage());
        }
    }

  
}
