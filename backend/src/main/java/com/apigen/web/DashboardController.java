package com.apigen.web;

import com.apigen.dto.CommonDtos;
import com.apigen.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public CommonDtos.DashboardResponse dashboard() {
        return service.dashboard();
    }
}
