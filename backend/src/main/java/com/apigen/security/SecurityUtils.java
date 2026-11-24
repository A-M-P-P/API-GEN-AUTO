package com.apigen.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SecurityUtils {

    public String currentUsername() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(Authentication::getName)
                .filter(name -> name != null && !name.isBlank() && !"anonymousUser".equals(name))
                .orElse("system");
    }

    public boolean isAdmin() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(Authentication::getAuthorities)
                .map(authorities -> authorities.stream()
                        .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")))
                .orElse(false);
    }
}
