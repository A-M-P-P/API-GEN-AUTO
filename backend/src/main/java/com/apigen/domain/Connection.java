package com.apigen.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "connections")
public class Connection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ConnectionType type;

    @Column(columnDefinition = "CLOB")
    private String description;

    /** JSON non sensible : baseUrl, jdbcUrl, host, port, basePath, authType... */
    @Column(name = "base_config", columnDefinition = "CLOB")
    private String baseConfig;

    /** JSON chiffré : username, password, token, apiKey... */
    @Column(columnDefinition = "CLOB")
    private String credentials;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ConnectionType getType() { return type; }
    public void setType(ConnectionType type) { this.type = type; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBaseConfig() { return baseConfig; }
    public void setBaseConfig(String baseConfig) { this.baseConfig = baseConfig; }
    public String getCredentials() { return credentials; }
    public void setCredentials(String credentials) { this.credentials = credentials; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
