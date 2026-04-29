package com.retirementmodeler.security;

import com.retirementmodeler.model.User;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {

  private final SecretKey key;
  private final long expirationMs;

  public JwtUtil(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.expiration}") long expirationMs) {
    this.key = Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(secret));
    this.expirationMs = expirationMs;
  }

  public String generateToken(User user) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + expirationMs);
    return Jwts.builder()
        .subject(user.getId().toString())
        .claim("email", user.getEmail())
        .issuedAt(now)
        .expiration(expiry)
        .signWith(key)
        .compact();
  }

  public UUID getUserIdFromToken(String token) {
    String subject =
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
    return UUID.fromString(subject);
  }

  public boolean validateToken(String token) {
    try {
      Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }
}
