package com.apigen.web;

import com.apigen.dto.ApiDtos;
import com.apigen.dto.RunDtos;
import com.apigen.service.EndpointService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EndpointController {

    private final EndpointService service;

    public EndpointController(EndpointService service) {
        this.service = service;
    }

    @PostMapping("/api/apis/{apiId}/endpoints")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ResponseEntity<ApiDtos.EndpointResponse> create(@PathVariable Long apiId,
                                                            @Valid @RequestBody ApiDtos.EndpointRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(apiId, request));
    }

    @GetMapping("/api/endpoints/{id}")
    public ApiDtos.EndpointResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/api/endpoints/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ApiDtos.EndpointResponse update(@PathVariable Long id,
                                           @Valid @RequestBody ApiDtos.EndpointRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/api/endpoints/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/endpoints/{id}/active")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ApiDtos.EndpointResponse setActive(@PathVariable Long id, @RequestParam boolean active) {
        return service.setActive(id, active);
    }

    @PutMapping("/api/endpoints/{id}/schedule")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ApiDtos.EndpointResponse upsertSchedule(@PathVariable Long id,
                                                    @Valid @RequestBody ApiDtos.ScheduleRequest request) {
        return service.upsertSchedule(id, request);
    }

    @DeleteMapping("/api/endpoints/{id}/schedule")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ResponseEntity<Void> deleteSchedule(@PathVariable Long id) {
        service.deleteSchedule(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/endpoints/{id}/run")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public RunDtos.RunResponse runNow(@PathVariable Long id,
                                      @RequestBody(required = false) RunDtos.RunTriggerRequest request) {
        return service.runNow(id, request == null ? null : request.payload());
    }
}
