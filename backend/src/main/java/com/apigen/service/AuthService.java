package com.apigen.service;

import com.apigen.domain.AppUser;
import com.apigen.domain.Role;
import com.apigen.dto.UserDtos;
import com.apigen.repository.UserRepository;
import com.apigen.security.JwtService;
import com.apigen.security.SecurityUtils;
import com.apigen.web.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       SecurityUtils securityUtils,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.securityUtils = securityUtils;
        this.auditService = auditService;
    }

    public UserDtos.LoginResponse login(UserDtos.LoginRequest request) {
        AppUser user = userRepository.findByUsernameIgnoreCase(request.username())
                .orElseThrow(() -> ApiException.unauthorized("Identifiants invalides"));
        if (!user.isEnabled()) {
            throw ApiException.unauthorized("Compte désactivé");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Identifiants invalides");
        }
        String token = jwtService.generateToken(user.getUsername(), user.getRole().name());
        auditService.record(user.getUsername(), "LOGIN", "AppUser", String.valueOf(user.getId()), "Connexion de " + user.getUsername());
        return new UserDtos.LoginResponse(token, "Bearer", jwtService.expirationSeconds(), toResponse(user));
    }

    @Transactional(readOnly = true)
    public UserDtos.UserResponse me() {
        return userRepository.findByUsernameIgnoreCase(securityUtils.currentUsername())
                .map(this::toResponse)
                .orElseThrow(() -> ApiException.unauthorized("Session invalide"));
    }

    @Transactional
    public UserDtos.UserResponse changePassword(UserDtos.ChangePasswordRequest request) {
        AppUser user = userRepository.findByUsernameIgnoreCase(securityUtils.currentUsername())
                .orElseThrow(() -> ApiException.unauthorized("Session invalide"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Mot de passe actuel incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        auditService.record("PASSWORD_CHANGE", "AppUser", String.valueOf(user.getId()), "Changement de mot de passe");
        return toResponse(user);
    }

    public UserDtos.UserResponse toResponse(AppUser user) {
        return new UserDtos.UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole() == null ? Role.VIEWER : user.getRole(),
                user.isEnabled(),
                user.getCreatedAt()
        );
    }
}
