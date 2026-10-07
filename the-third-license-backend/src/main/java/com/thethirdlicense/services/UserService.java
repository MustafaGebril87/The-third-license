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

    // Update profile fields only — roles/balance are never taken from client input
    public Optional<User> updateProfile(UUID id, String username, String email) {
        return userRepository.findById(id).map(user -> {
            if (username != null && !username.isBlank() && !username.equals(user.getUsername())) {
                if (!username.matches("^[A-Za-z0-9][A-Za-z0-9._-]{2,49}$") || username.contains("..")) {
                    throw new IllegalArgumentException("Username may only contain letters, digits, '.', '_' and '-' (3-50 characters).");
                }
                if (userRepository.findByUsername(username).isPresent()) {
                    throw new IllegalStateException("Username is not available.");
                }
                user.setUsername(username);
            }
            if (email != null && !email.isBlank() && !email.equalsIgnoreCase(user.getEmail())) {
                if (!email.contains("@")) {
                    throw new IllegalArgumentException("Invalid email address.");
                }
                if (userRepository.findByEmail(email).isPresent()) {
                    throw new IllegalStateException("Email is not available.");
                }
                user.setEmail(email);
            }
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
