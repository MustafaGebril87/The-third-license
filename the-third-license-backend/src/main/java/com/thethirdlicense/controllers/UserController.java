package com.thethirdlicense.controllers;

import com.thethirdlicense.models.Role;
import com.thethirdlicense.models.User;
import com.thethirdlicense.repositories.UserRepository;
import com.thethirdlicense.security.UserPrincipal;
import com.thethirdlicense.services.UserService;
import com.thethirdlicense.controllers.UserDTO;
import com.thethirdlicense.exceptions.ResourceNotFoundException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@RestController
@RequestMapping("/api/users")
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    // 1. Register a new user
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody UserDTO userDTO) {
        User user = new User();
        user.setUsername(userDTO.getUsername());
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        user.setEmail(userDTO.getEmail());
        user.setFullName(userDTO.getFullName());
        userRepository.save(user);
        return ResponseEntity.ok("User registered successfully.");
    }

    // 2. Get user by ID
    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable UUID id) {
        Optional<User> user = userService.getUserById(id);
        return user.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    // 3. Get all users
    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // 4. Check if email exists
    @GetMapping("/exists")
    public ResponseEntity<Boolean> checkEmailExists(@RequestParam String email) {
        return ResponseEntity.ok(userService.existsByEmail(email));
    }

    // 5. Update user details
    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable UUID id, @RequestBody User updatedUser) {
        Optional<User> user = userService.updateUser(id, updatedUser);
        return user.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    // 6. Delete a user
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable UUID id) {
        boolean deleted = userService.deleteUser(id);
        return deleted ? ResponseEntity.ok("User deleted successfully") : ResponseEntity.notFound().build();
    }

    // 7. Assign role to user
    @PostMapping("/{id}/roles")
    public ResponseEntity<User> assignRole(@PathVariable UUID id, @RequestBody Role role) {
        Optional<User> user = userService.assignRole(id, role);
        return user.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/users/me")
    public ResponseEntity<?> getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof UserPrincipal)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }

        try {
            UserPrincipal userPrincipal = (UserPrincipal) principal;
            User user = userService.findById(userPrincipal.getId());

            // Optional: sanitize circular refs before returning (e.g., set password=null)
            user.setPassword(null);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            e.printStackTrace(); //  Log the actual error
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(" Internal error while loading user.");
        }
    }


    // 9. Remove role from user
    @DeleteMapping("/{id}/roles")
    public ResponseEntity<User> removeRole(@PathVariable UUID id, @RequestBody Role role) {
        Optional<User> user = userService.removeRole(id, role);
        return user.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
