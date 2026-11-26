package com.apigen.service;

import com.apigen.domain.Endpoint;
import com.apigen.domain.Schedule;
import com.apigen.dto.ApiDtos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class EndpointMapper {

    private final ObjectMapper objectMapper;

    public EndpointMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ApiDtos.EndpointSummary toSummary(Endpoint endpoint) {
        return new ApiDtos.EndpointSummary(
                endpoint.getId(),
                endpoint.getName(),
                endpoint.getMethod(),
                endpoint.getPath(),
                endpoint.getDirection(),
                endpoint.isActive(),
                toScheduleResponse(endpoint.getSchedule()),
                endpoint.getResponseFormat()
        );
    }

    public ApiDtos.EndpointResponse toResponse(Endpoint endpoint) {
        return new ApiDtos.EndpointResponse(
                endpoint.getId(),
                endpoint.getApi().getId(),
                endpoint.getName(),
                endpoint.getMethod(),
                endpoint.getPath(),
                endpoint.getDirection(),
                endpoint.getDescription(),
                readJson(endpoint.getPipeline()),
                endpoint.isActive(),
                endpoint.getCreatedAt(),
                endpoint.getUpdatedAt(),
                toScheduleResponse(endpoint.getSchedule()),
                endpoint.getResponseFormat(),
                endpoint.getResponseFileName(),
                endpoint.getResponseContentType(),
                endpoint.getRedirectLocation()
        );
    }

    public ApiDtos.ScheduleResponse toScheduleResponse(Schedule schedule) {
        if (schedule == null) {
            return null;
        }
        return new ApiDtos.ScheduleResponse(
                schedule.getId(),
                schedule.getCronExpression(),
                schedule.isEnabled(),
                readJson(schedule.getPayload()),
                schedule.getNextRunAt(),
                schedule.getLastRunAt()
        );
    }

    public JsonNode readJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException("JSON stocké illisible", e);
        }
    }
}
