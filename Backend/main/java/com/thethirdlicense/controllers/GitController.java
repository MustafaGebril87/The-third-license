package com.thethirdlicense.controllers;

import com.thethirdlicense.models.User;
import com.thethirdlicense.services.GitService;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/git")
public class GitController {

    private final GitService gitService;

    @Autowired
    public GitController(GitService gitService) {
        this.gitService = gitService;
    }

    @PostMapping("/push/{repoId}")
    public ResponseEntity<?> pushCode(@PathVariable UUID repoId, @RequestParam int codeSize) throws GitAPIException, IOException {
        gitService.pushCode(repoId, codeSize);
        return ResponseEntity.ok("Push request submitted for approval.");
    }

}
