package com.apigen.web;

import com.apigen.dto.ConnectionDtos;
import com.apigen.dto.MetadataDtos;
import com.apigen.service.ConnectionService;
import com.apigen.service.MetadataService;
import com.apigen.web.ApiException;
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
@RequestMapping("/api/connections")
public class ConnectionController {

    private final ConnectionService service;
    private final MetadataService metadataService;

    public ConnectionController(ConnectionService service, MetadataService metadataService) {
        this.service = service;
        this.metadataService = metadataService;
    }

    @GetMapping
    public List<ConnectionDtos.ConnectionResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ConnectionDtos.ConnectionResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ResponseEntity<ConnectionDtos.ConnectionResponse> create(
            @Valid @RequestBody ConnectionDtos.ConnectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ConnectionDtos.ConnectionResponse update(@PathVariable Long id,
                                                     @Valid @RequestBody ConnectionDtos.ConnectionRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ConnectionDtos.ConnectionTestResponse test(@RequestBody ConnectionDtos.ConnectionTestRequest request) {
        return service.test(request);
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ConnectionDtos.ConnectionTestResponse testExisting(@PathVariable Long id) {
        return service.test(id);
    }

    /** Liste les schémas et tables d'une base cible, enregistrée ou non. */
    @PostMapping("/discover")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public MetadataDtos.DiscoveryResponse discover(@RequestBody MetadataDtos.DiscoveryRequest request) {
        try {
            return metadataService.discover(request);
        } catch (Exception e) {
            throw ApiException.badRequest(rootMessage(e));
        }
    }

    /** Liste les colonnes d'une table donnée. */
    @PostMapping("/discover/columns")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public MetadataDtos.ColumnsResponse columns(@RequestBody MetadataDtos.ColumnsRequest request) {
        try {
            return metadataService.columns(request);
        } catch (Exception e) {
            throw ApiException.badRequest(rootMessage(e));
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
