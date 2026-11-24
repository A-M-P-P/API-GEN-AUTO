package com.apigen.dto;

import com.apigen.domain.ConnectionType;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** DTO de la découverte de structure d'une base cible. */
public final class MetadataDtos {

    private MetadataDtos() {
    }

    /**
     * Requête de découverte. On fournit soit {@code connectionId} (connexion enregistrée,
     * identifiants puisés dans le coffre chiffré), soit la configuration inline (formulaire
     * non encore enregistré).
     */
    public record DiscoveryRequest(
            Long connectionId,
            ConnectionType type,
            JsonNode baseConfig,
            JsonNode credentials
    ) {
    }

    public record DiscoveryResponse(
            String product,
            String version,
            List<SchemaSummary> schemas
    ) {
    }

    public record SchemaSummary(String name, List<TableSummary> tables) {
    }

    public record TableSummary(String name, String type) {
    }

    public record ColumnsRequest(
            Long connectionId,
            ConnectionType type,
            JsonNode baseConfig,
            JsonNode credentials,
            String schema,
            String table
    ) {
    }

    public record ColumnsResponse(String schema, String table, List<ColumnInfo> columns) {
    }

    public record ColumnInfo(String name, String type, boolean nullable) {
    }
}
