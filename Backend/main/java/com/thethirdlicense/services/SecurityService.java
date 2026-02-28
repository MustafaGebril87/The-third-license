package com.thethirdlicense.services;

import com.thethirdlicense.models.User;
import com.thethirdlicense.models.RefreshToken;
import com.thethirdlicense.services.RefreshTokenService;
import com.thethirdlicense.repositories.UserRepository;
import com.thethirdlicense.security.JWTUtil;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SecurityService {

    private final AuthenticationManager authenticationManager;
    private final JWTUtil jwtUtil;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

    public SecurityService(AuthenticationManager authenticationManager, JWTUtil jwtUtil, 
                           UserRepository userRepository, RefreshTokenService refreshTokenService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
    }

    public Authentication authenticate(String username, String password) {
        return authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
        );
    }

    public String generateToken(User userDetails) {
        return jwtUtil.generateToken(userDetails);
    }

    public Optional<RefreshToken> createRefreshToken(String username) {
        return userRepository.findByEmail(username)
                .map(refreshTokenService::createRefreshToken);
    }
}
