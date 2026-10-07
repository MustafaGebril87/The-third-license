package com.thethirdlicense.controllers;

import com.thethirdlicense.models.Role;
import com.thethirdlicense.models.User;
import com.thethirdlicense.responses.AuthResponse;
import com.thethirdlicense.security.UserPrincipal;
import com.thethirdlicense.services.UserService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Account endpoints. Regular users may only read/update their own account;
 * listing, deleting and role management are ADMIN-only. Responses never
 * expose entities (no password hash, balance, or relations).
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** Current session's user — used by the frontend to restore login state after a reload. */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        return ResponseEntity.ok(new AuthResponse(userService.findById(principal.getId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuthResponse> getUserById(@PathVariable UUID id,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        if (!isSelfOrAdmin(principal, id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return userService.getUserById(id)
                .map(u -> ResponseEntity.ok(new AuthResponse(u)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AuthResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers().stream().map(AuthResponse::new).toList());
    }

    /** Updates username/email only. Roles and balances can never be changed through this endpoint. */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable UUID id, @RequestBody UserDTO update,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        if (!isSelfOrAdmin(principal, id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return userService.updateProfile(id, update.getUsername(), update.getEmail())
                .<ResponseEntity<?>>map(u -> ResponseEntity.ok(new AuthResponse(u)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deleteUser(@PathVariable UUID id) {
        boolean deleted = userService.deleteUser(id);
        return deleted ? ResponseEntity.ok("User deleted successfully") : ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponse> assignRole(@PathVariable UUID id, @RequestBody Role role) {
        return userService.assignRole(id, role)
                .map(u -> ResponseEntity.ok(new AuthResponse(u)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponse> removeRole(@PathVariable UUID id, @RequestBody Role role) {
        return userService.removeRole(id, role)
                .map(u -> ResponseEntity.ok(new AuthResponse(u)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private boolean isSelfOrAdmin(UserPrincipal principal, UUID targetId) {
        if (principal == null) return false;
        if (principal.getId().equals(targetId)) return true;
        return principal.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
