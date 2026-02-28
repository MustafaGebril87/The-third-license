package com.thethirdlicense.services;

import com.thethirdlicense.models.Company;
import com.thethirdlicense.models.Repository_;
import com.thethirdlicense.models.User;
import com.thethirdlicense.repositories.CompanyRepository;
import com.thethirdlicense.repositories.RepositoryRepository;
import com.thethirdlicense.repositories.UserRepository;
import jakarta.persistence.EntityNotFoundException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.jgit.lib.BaseRepositoryBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.URIish;
//import org.eclipse.jgit.storage.file.RepositoryBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FS;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
	private final RepositoryRepository repositoryRepository;


    @Autowired
    public CompanyService(CompanyRepository companyRepository, RepositoryRepository repositoryRepository) {
        this.companyRepository = companyRepository;
        this.repositoryRepository = repositoryRepository;
    }
    public Company openCompany(Company company, User owner) throws GitAPIException, IOException, URISyntaxException {
        // 1. Set owner and persist company
        company.setOwner(owner);
        Company savedCompany = companyRepository.save(company);

        // 2. Define repository paths
        String repoPath = "C:\\repos\\origin\\" + company.getName() + ".git";
        File repoDir = new File(repoPath);

        if (!repoDir.exists()) {
            // 3. Initialize bare Git repository
            Git.init().setBare(true).setDirectory(repoDir).call();

            // 4. Create initial main branch in temp working repo
            File tempRepoDir = new File("C:\\repos\\temp\\" + company.getName() + "-init");
            tempRepoDir.mkdirs();

            try (Git tempGit = Git.init().setDirectory(tempRepoDir).call()) {
                // Create a README.md file
                File readme = new File(tempRepoDir, "README.md");
                Files.writeString(readme.toPath(), "# " + company.getName());

                tempGit.add().addFilepattern("README.md").call();
                tempGit.commit()
                        .setMessage("Initial commit on main")
                        .setAuthor(owner.getUsername(), owner.getEmail())
                        .call();

                // Rename 'master' to 'main'
                tempGit.branchRename().setOldName("master").setNewName("main").call();

                // Add bare repo as origin and push
                tempGit.remoteAdd()
                        .setName("origin")
                        .setUri(new org.eclipse.jgit.transport.URIish("file://" + repoPath))
                        .call();

                tempGit.push()
                        .setRemote("origin")
                        .setRefSpecs(new org.eclipse.jgit.transport.RefSpec("main:refs/heads/main"))
                        .call();
            }

            // 5. Set HEAD in the bare repository to point to main
            File headFile = new File(repoDir, "HEAD");
            Files.writeString(headFile.toPath(), "ref: refs/heads/main\n");

            // 6. Delete the temporary repo
            deleteDirectoryRecursively(tempRepoDir);

            // 7. Create a pre-receive hook to block all pushes, pulls, and merge commits
            File hooksDir = new File(repoDir, "hooks");
            File hookFile = new File(hooksDir, "pre-receive");

            if (!hookFile.exists()) {
                hooksDir.mkdirs();
                try (FileWriter writer = new FileWriter(hookFile)) {
                    writer.write("#!/bin/bash\n");
                    writer.write("while read oldrev newrev refname; do\n");
                    writer.write("    for commit in $(git rev-list $oldrev..$newrev); do\n");
                    writer.write("        parent_count=$(git rev-list --parents -n 1 $commit | wc -w)\n");
                    writer.write("        if [ \"$parent_count\" -gt 2 ]; then\n");
                    writer.write("            echo \"Merge commits are not allowed.\"\n");
                    writer.write("            exit 1\n");
                    writer.write("        fi\n");
                    writer.write("    done\n");
                    writer.write("done\n");
                    writer.write("echo \"Pushes and pulls are not allowed. Use the website platform API.\"\n");
                    writer.write("exit 1\n");
                }
                hookFile.setExecutable(true);
            }
        }

        // 8. Save the repository entity in the database
        Repository_ repository = new Repository_();
        repository.setId(UUID.randomUUID());
        repository.setName(company.getName() + "-repo");
        repository.setGitUrl("file://" + repoPath);
        repository.setCompany(savedCompany);
        repository.setOwner(owner);
        repositoryRepository.save(repository);

        return savedCompany;
    }

    private void deleteDirectoryRecursively(File file) {
        if (file.isDirectory()) {
            File[] contents = file.listFiles();
            if (contents != null) {
                for (File f : contents) {
                    deleteDirectoryRecursively(f);
                }
            }
        }
        file.delete();
    }

}


