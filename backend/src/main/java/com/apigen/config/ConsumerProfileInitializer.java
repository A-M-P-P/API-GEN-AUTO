package com.apigen.config;
import com.apigen.repository.OrganizationRepository;
import com.apigen.service.ConsumerAuthService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
@Component
@Order(org.springframework.core.Ordered.LOWEST_PRECEDENCE)
public class ConsumerProfileInitializer implements ApplicationRunner {
    private final OrganizationRepository organizations;
    private final ConsumerAuthService service;
    public ConsumerProfileInitializer(OrganizationRepository organizations, ConsumerAuthService service) {
        this.organizations = organizations; this.service = service;
    }
    public void run(ApplicationArguments args) {
        for (var org : organizations.findAll()) service.ensureDefault(org.getId());
    }
}
