package com.apigen.web;

import com.apigen.domain.RunStatus;
import com.apigen.dto.RunDtos;
import com.apigen.service.RunService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runs")
public class RunController {

    private final RunService service;

    public RunController(RunService service) {
        this.service = service;
    }

    @GetMapping
    public RunDtos.PageResponse<RunDtos.RunResponse> list(
            @RequestParam(required = false) Long apiId,
            @RequestParam(required = false) Long endpointId,
            @RequestParam(required = false) RunStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(apiId, endpointId, status, page, size);
    }

    @GetMapping("/{id}")
    public RunDtos.RunResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
