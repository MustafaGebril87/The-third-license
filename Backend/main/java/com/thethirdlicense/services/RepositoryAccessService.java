package com.thethirdlicense.services;

import com.thethirdlicense.models.Repository_;
import com.thethirdlicense.models.RepositoryAccess;
import com.thethirdlicense.models.User;
import com.thethirdlicense.repositories.RepositoryAccessRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class RepositoryAccessService {

    @Autowired
    private RepositoryAccessRepository accessRepository;

    public void grantAccess(User user, Repository_ repository) {
        // Check if access already exists
        boolean alreadyExists = repository.getAccesses().stream()
            .anyMatch(a -> a.getUser().getId().equals(user.getId()));
        if (alreadyExists) return;

        RepositoryAccess access = new RepositoryAccess();
        access.setRepository(repository);
        access.setUser(user);
        access.setAccessLevel(RepositoryAccess.AccessLevel.CONTRIBUTOR);
        access.setGrantedAt(LocalDateTime.now());

        accessRepository.save(access);  //  Now this will work
    }
}
