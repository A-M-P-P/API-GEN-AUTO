package com.apigen.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "endpoints")
public class Endpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "api_id", nullable = false)
    private ApiDefinition api;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(nullable = false, length = 300)
    private String path;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Direction direction = Direction.INBOUND;

    @Column(columnDefinition = "CLOB")
    private String description;

    /** Format de la réponse renvoyée par la passerelle (json par défaut). */
    @Enumerated(EnumType.STRING)
    @Column(name = "response_format", nullable = false, length = 20)
    private ResponseFormat responseFormat = ResponseFormat.JSON;

    /** Nom du fichier téléchargé (FILE/BINARY), template simple {runId} accepté. */
    @Column(name = "response_file_name", length = 200)
    private String responseFileName;

    /** Type MIME explicite pour TEXT/XML/FILE/BINARY ; sinon déduit du format. */
    @Column(name = "response_content_type", length = 100)
    private String responseContentType;

    /** URL de redirection (REDIRECT), template simple {runId} accepté. */
    @Column(name = "redirect_location", length = 500)
    private String redirectLocation;

    /** JSON : tableau ordonné d'étapes d'exécution. */
    @Column(columnDefinition = "CLOB")
    private String pipeline;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @OneToOne(mappedBy = "endpoint", cascade = CascadeType.ALL, orphanRemoval = true)
    private Schedule schedule;

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ApiDefinition getApi() { return api; }
    public void setApi(ApiDefinition api) { this.api = api; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public Direction getDirection() { return direction; }
    public void setDirection(Direction direction) { this.direction = direction; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ResponseFormat getResponseFormat() { return responseFormat; }
    public void setResponseFormat(ResponseFormat responseFormat) { this.responseFormat = responseFormat; }
    public String getResponseFileName() { return responseFileName; }
    public void setResponseFileName(String responseFileName) { this.responseFileName = responseFileName; }
    public String getResponseContentType() { return responseContentType; }
    public void setResponseContentType(String responseContentType) { this.responseContentType = responseContentType; }
    public String getRedirectLocation() { return redirectLocation; }
    public void setRedirectLocation(String redirectLocation) { this.redirectLocation = redirectLocation; }
    public String getPipeline() { return pipeline; }
    public void setPipeline(String pipeline) { this.pipeline = pipeline; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Schedule getSchedule() { return schedule; }
    public void setSchedule(Schedule schedule) { this.schedule = schedule; }
}
