package com.apigen.web;

import com.apigen.domain.AuditEvent;
import com.apigen.dto.CommonDtos;
import com.apigen.dto.RunDtos;
import com.apigen.service.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    @GetMapping
    public RunDtos.PageResponse<CommonDtos.AuditResponse> audit(@RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "50") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        Page<AuditEvent> result = service.recent(PageRequest.of(safePage, safeSize));
        List<CommonDtos.AuditResponse> content = result.getContent().stream()
                .map(event -> new CommonDtos.AuditResponse(
                        event.getId(),
                        event.getActor(),
                        event.getAction(),
                        event.getEntityType(),
                        event.getEntityId(),
                        event.getDetails(),
                        event.getCreatedAt()
                ))
                .toList();
        return RunDtos.PageResponse.of(content, safePage, safeSize, result.getTotalElements(), result.getTotalPages());
    }
}
