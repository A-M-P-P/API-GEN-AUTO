package com.apigen.service;

import com.apigen.domain.AppUser;
import com.apigen.domain.Role;
import com.apigen.dto.UserDtos;
import com.apigen.repository.UserRepository;
import com.apigen.security.SecurityUtils;
import com.apigen.web.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    public UserService(UserRepository repository,
                       PasswordEncoder passwordEncoder,
                       SecurityUtils securityUtils,
                       AuditService auditService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.securityUtils = securityUtils;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<UserDtos.UserResponse> list() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(AppUser::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public UserDtos.UserResponse create(UserDtos.CreateUserRequest request) {
        if (repository.existsByUsernameIgnoreCase(request.username())) {
            throw ApiException.conflict("Ce nom d'utilisateur existe déjà");
        }
        AppUser user = new AppUser();
        user.setUsername(request.username().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setRole(request.role() == null ? Role.VIEWER : request.role());
        user.setEnabled(true);
        AppUser saved = repository.save(user);
        auditService.record("USER_CREATE", "AppUser", String.valueOf(saved.getId()), "Création de " + saved.getUsername());
        return toResponse(saved);
    }

    @Transactional
    public UserDtos.UserResponse update(Long id, UserDtos.UpdateUserRequest request) {
        AppUser user = requireEntity(id);
        if (request.fullName() != null) {
            user.setFullName(request.fullName());
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        user.setUpdatedAt(Instant.now());
        AppUser saved = repository.save(user);
        auditService.record("USER_UPDATE", "AppUser", String.valueOf(saved.getId()), "Mise à jour de " + saved.getUsername());
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        AppUser user = requireEntity(id);
        if (user.getUsername().equalsIgnoreCase(securityUtils.currentUsername())) {
            throw ApiException.badRequest("Impossible de supprimer son propre compte");
        }
        repository.delete(user);
        auditService.record("USER_DELETE", "AppUser", String.valueOf(id), "Suppression de " + user.getUsername());
    }

    private AppUser requireEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Utilisateur introuvable : " + id));
    }

    private UserDtos.UserResponse toResponse(AppUser user) {
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
