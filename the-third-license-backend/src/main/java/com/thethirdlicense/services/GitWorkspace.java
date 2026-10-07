package com.thethirdlicense.services;

import com.thethirdlicense.models.Repository_;
import com.thethirdlicense.models.User;
import org.eclipse.jgit.lib.Repository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Single source of truth for where Git data lives on disk, plus the path checks
 * that keep user-supplied names and file paths inside those directories.
 *
 * Layout under ${git.repos.base-path}:
 *   origin/<company>.git         bare repositories
 *   cloned/<repo>-<username>     per-user working copies
 *   owner-clones/<repo>          company owner's merge working copy
 *   temp/<company>-init          scratch dir used while creating a repo
 */
@Component
public class GitWorkspace {

    /** Names that become directory names: letters, digits, '.', '_' and '-', must start alphanumeric. */
    public static final Pattern SAFE_NAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{1,63}$");

    private static final Pattern SAFE_BRANCH = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._/-]{0,99}$");

    /** Branches contributors may never push to directly — changes reach these only via owner merge. */
    private static final java.util.Set<String> PROTECTED_BRANCHES = java.util.Set.of("main", "master", "HEAD");

    private final File baseDir;

    public GitWorkspace(@Value("${git.repos.base-path}") String basePath) {
        this.baseDir = new File(basePath).getAbsoluteFile();
    }

    public File originRepo(String companyName) {
        return resolveInside(new File(baseDir, "origin"), requireSafeName(companyName) + ".git");
    }

    public File tempInitDir(String companyName) {
        return resolveInside(new File(baseDir, "temp"), requireSafeName(companyName) + "-init");
    }

    public File userClone(Repository_ repo, User user) {
        return resolveInside(new File(baseDir, "cloned"),
                requireSafeName(repo.getName()) + "-" + requireSafeName(user.getUsername()));
    }

    public File ownerClone(Repository_ repo) {
        return resolveInside(new File(baseDir, "owner-clones"), requireSafeName(repo.getName()));
    }

    /**
     * Resolves a user-supplied relative path against a working-copy root and rejects anything
     * that is absolute, escapes the root, or touches the .git directory.
     */
    public static File resolveInside(File root, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("File path is required.");
        }
        Path rel = Path.of(relativePath);
        if (rel.isAbsolute() || relativePath.startsWith("/") || relativePath.startsWith("\\")) {
            throw new IllegalArgumentException("Invalid file path: " + relativePath);
        }
        Path rootPath = root.toPath().toAbsolutePath().normalize();
        Path target = rootPath.resolve(rel).normalize();
        if (!target.startsWith(rootPath) || target.equals(rootPath)) {
            throw new IllegalArgumentException("Invalid file path: " + relativePath);
        }
        for (Path part : rootPath.relativize(target)) {
            if (part.toString().equalsIgnoreCase(".git")) {
                throw new IllegalArgumentException("Invalid file path: " + relativePath);
            }
        }
        return target.toFile();
    }

    /** Validates a branch a contributor wants to push to. */
    public static String requirePushableBranch(String branch) {
        if (branch == null || !SAFE_BRANCH.matcher(branch).matches()
                || branch.contains("..") || branch.endsWith("/") || branch.endsWith(".lock")
                || !Repository.isValidRefName("refs/heads/" + branch)) {
            throw new IllegalArgumentException("Invalid branch name.");
        }
        if (PROTECTED_BRANCHES.contains(branch)) {
            throw new IllegalArgumentException("You cannot push directly to '" + branch + "'. Push to a feature branch and the owner will merge it.");
        }
        return branch;
    }

    /** Validates a branch name used only for reading/merging (protected branches allowed). */
    public static String requireReadableBranch(String branch) {
        if (branch == null || !SAFE_BRANCH.matcher(branch).matches()
                || branch.contains("..") || !Repository.isValidRefName("refs/heads/" + branch)) {
            throw new IllegalArgumentException("Invalid branch name.");
        }
        return branch;
    }

    public static String requireSafeName(String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches() || name.contains("..")) {
            throw new IllegalArgumentException("Invalid name: only letters, digits, '.', '_' and '-' are allowed.");
        }
        return name;
    }
}
