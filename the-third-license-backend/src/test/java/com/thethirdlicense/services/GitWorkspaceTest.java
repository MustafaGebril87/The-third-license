package com.thethirdlicense.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitWorkspaceTest {

    @TempDir
    File root;

    @Test
    void resolveInside_normalRelativePath_staysInRoot() {
        File f = GitWorkspace.resolveInside(root, "src/main/App.java");
        assertThat(f.toPath().startsWith(root.toPath())).isTrue();
    }

    @Test
    void resolveInside_parentTraversal_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> GitWorkspace.resolveInside(root, "../../etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> GitWorkspace.resolveInside(root, "src/../../outside.txt"));
        assertThrows(IllegalArgumentException.class, () -> GitWorkspace.resolveInside(root, "..\\..\\windows\\win.ini"));
    }

    @Test
    void resolveInside_absolutePath_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> GitWorkspace.resolveInside(root, "/etc/passwd"));
        assertThrows(IllegalArgumentException.class,
                () -> GitWorkspace.resolveInside(root, new File(root.getParentFile(), "x.txt").getAbsolutePath()));
    }

    @Test
    void resolveInside_gitDirectory_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> GitWorkspace.resolveInside(root, ".git/hooks/pre-commit"));
        assertThrows(IllegalArgumentException.class, () -> GitWorkspace.resolveInside(root, "sub/.GIT/config"));
    }

    @Test
    void resolveInside_blankOrRoot_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> GitWorkspace.resolveInside(root, ""));
        assertThrows(IllegalArgumentException.class, () -> GitWorkspace.resolveInside(root, "."));
    }

    @Test
    void requirePushableBranch_protectedBranches_areRejected() {
        assertThrows(IllegalArgumentException.class, () -> GitWorkspace.requirePushableBranch("main"));
        assertThrows(IllegalArgumentException.class, () -> GitWorkspace.requirePushableBranch("master"));
    }

    @Test
    void requirePushableBranch_invalidNames_areRejected() {
        assertThrows(IllegalArgumentException.class, () -> GitWorkspace.requirePushableBranch("../main"));
        assertThrows(IllegalArgumentException.class, () -> GitWorkspace.requirePushableBranch("feature..x"));
        assertThrows(IllegalArgumentException.class, () -> GitWorkspace.requirePushableBranch("feat ure"));
        assertThrows(IllegalArgumentException.class, () -> GitWorkspace.requirePushableBranch(null));
    }

    @Test
    void requirePushableBranch_featureBranch_isAllowed() {
        assertThat(GitWorkspace.requirePushableBranch("feature/login-page")).isEqualTo("feature/login-page");
    }

    @Test
    void requireSafeName_rejectsPathCharacters() {
        assertThrows(IllegalArgumentException.class, () -> GitWorkspace.requireSafeName("../evil"));
        assertThrows(IllegalArgumentException.class, () -> GitWorkspace.requireSafeName("a/b"));
        assertThrows(IllegalArgumentException.class, () -> GitWorkspace.requireSafeName("a..b"));
        assertThat(GitWorkspace.requireSafeName("Acme_Corp-2")).isEqualTo("Acme_Corp-2");
    }

    @Test
    void userCloneAndOwnerClone_neverCollide() {
        GitWorkspace ws = new GitWorkspace(root.getAbsolutePath());
        com.thethirdlicense.models.Repository_ repo = new com.thethirdlicense.models.Repository_();
        repo.setName("Acme-repo");
        com.thethirdlicense.models.User user = new com.thethirdlicense.models.User();
        user.setUsername("owner");
        assertThat(ws.userClone(repo, user)).isNotEqualTo(ws.ownerClone(repo));
    }
}
