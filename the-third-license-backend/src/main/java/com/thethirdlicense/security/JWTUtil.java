package com.thethirdlicense.security;

import io.jsonwebtoken.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.thethirdlicense.models.User;
import com.thethirdlicense.repositories.UserRepository;

import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Component
public class JWTUtil {

    private final String SECRET_KEY = "OLjRSTbPVxhtiY9XQw542835XaYmpvv5jWmlmwoNcxM=";
    private final UserRepository userRepository;

    @Autowired
    public JWTUtil(UserRepository userRepository) {  // Inject UserRepository
        this.userRepository = userRepository;
    }
    public String generateToken(User user) {
        Claims claims = Jwts.claims();
        claims.setSubject(user.getId().toString()); // still store UUID in `sub`
        claims.put("username", user.getUsername()); //  Add username
        claims.put("email", user.getEmail());       // optional: add more user info if needed

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 7)) // 7 days
                .signWith(SignatureAlgorithm.HS256, SECRET_KEY.getBytes())
                .compact();
    }


    public UUID getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(SECRET_KEY.getBytes())
                .parseClaimsJws(token)
                .getBody();

        return UUID.fromString(claims.getSubject()); // Directly return UUID from token
    }
    public String generateRefreshToken(User user) { // Accept User
        return Jwts.builder()
                .setSubject(user.getId().toString()) //  Store UUID, not username
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 7)) // 7 days
                .signWith(SignatureAlgorithm.HS256, SECRET_KEY.getBytes()) 
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = Jwts.parser()
                .setSigningKey(SECRET_KEY)
                .parseClaimsJws(token)
                .getBody();
        return claimsResolver.apply(claims);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().setSigningKey(SECRET_KEY.getBytes()).parseClaimsJws(token); // Use getBytes()
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
}
