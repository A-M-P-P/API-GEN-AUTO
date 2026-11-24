package com.apigen.web;

import com.apigen.dto.ApiDtos;
import com.apigen.service.ApiDefinitionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/apis")
public class ApiDefinitionController {

    private final ApiDefinitionService service;

    public ApiDefinitionController(ApiDefinitionService service) {
        this.service = service;
    }

    @GetMapping
    public List<ApiDtos.ApiSummary> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ApiDtos.ApiDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ResponseEntity<ApiDtos.ApiDetail> create(@Valid @RequestBody ApiDtos.ApiCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ApiDtos.ApiDetail update(@PathVariable Long id, @Valid @RequestBody ApiDtos.ApiUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/api-key")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ApiDtos.ApiKeyResponse generateApiKey(@PathVariable Long id) {
        return service.generateApiKey(id);
    }

    @DeleteMapping("/{id}/api-key")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ResponseEntity<Void> revokeApiKey(@PathVariable Long id) {
        service.revokeApiKey(id);
        return ResponseEntity.noContent().build();
    }
}
