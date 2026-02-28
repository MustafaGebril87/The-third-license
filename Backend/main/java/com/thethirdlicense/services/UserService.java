package com.thethirdlicense.services;

import com.thethirdlicense.models.User;
import com.thethirdlicense.models.Role;
import com.thethirdlicense.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    // Create a new user
    public User createUser(User user) {
        return userRepository.save(user);
    }

    // Get a user by ID
    public Optional<User> getUserById(UUID id) {
        return userRepository.findById(id);
    }

    // Get all users
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // Check if a user exists by email
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    // Update user details
    public Optional<User> updateUser(UUID id, User userDetails) {
        return userRepository.findById(id).map(user -> {
            user.setUsername(userDetails.getUsername());
            user.setEmail(userDetails.getEmail());
            user.setRoles(userDetails.getRoles());
            return userRepository.save(user);
        });
    }

    // Delete a user
    public boolean deleteUser(UUID id) {
        if (userRepository.existsById(id)) {
            userRepository.deleteById(id);
            return true;
        }
        return false;
    }

    // Assign a role to a user
    public Optional<User> assignRole(UUID userId, Role role) {
        return userRepository.findById(userId).map(user -> {
            Set<Role> roles = user.getRoles();
            roles.add(role);
            user.setRoles(roles);
            return userRepository.save(user);
        });
    }
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
    // Remove a role from a user
    public Optional<User> removeRole(UUID userId, Role role) {
        return userRepository.findById(userId).map(user -> {
            Set<Role> roles = user.getRoles();
            roles.remove(role);
            user.setRoles(roles);
            return userRepository.save(user);
        });
    }


    public User findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));
    }


}
