package com.retirementmodeler.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  private final JwtUtil jwtUtil;
  private final UserDetailsService userDetailsService;

  public JwtAuthFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
    this.jwtUtil = jwtUtil;
    this.userDetailsService = userDetailsService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    if (SecurityContextHolder.getContext().getAuthentication() != null) {
      chain.doFilter(request, response);
      return;
    }

    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);
      if (jwtUtil.validateToken(token)) {
        UUID userId = jwtUtil.getUserIdFromToken(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(userId.toString());
        var auth =
            new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
      }
    }

    chain.doFilter(request, response);
  }
}
