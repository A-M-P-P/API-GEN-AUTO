package com.apigen.web;

import com.apigen.dto.ConfigDtos;
import com.apigen.service.AuditService;
import com.apigen.service.ConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ConfigService service;
    private final AuditService auditService;

    public ConfigController(ConfigService service, AuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ConfigDtos.ConfigView view() {
        return service.view();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ConfigDtos.ConfigUpdateResponse update(@RequestBody ConfigDtos.ConfigUpdateRequest request) {
        ConfigDtos.ConfigUpdateResponse response = service.update(request);
        if (!response.changed().isEmpty()) {
            auditService.record("CONFIG_UPDATE", "Configuration", "runtime",
                    "Propriétés modifiées : " + String.join(", ", response.changed()));
        }
        return response;
    }
}
