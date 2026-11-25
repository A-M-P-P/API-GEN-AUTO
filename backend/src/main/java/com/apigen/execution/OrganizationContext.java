package com.apigen.execution;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Contexte de configuration d'une organisation, injecté dans le contexte d'exécution
 * des pipelines sous la racine {@code org}.
 *
 * <p>La structure JSON stockée dans {@code organizations.context_config} est :</p>
 * <pre>
 * {
 *   "variables": { "region": "centre" },
 *   "secrets": { "apiToken": "..." },
 *   "overrides": {
 *     "rest": { "baseUrl": "https://host:8443/api" },
 *     "files": { "baseDir": "./data/exchanges/dgi" }
 *   }
 * }
 * </pre>
 *
 * <p>Les secrets sont déchiffrés à la lecture et ne sont jamais renvoyés par l'API
 * d'administration (valeurs masquées).</p>
 */
public record OrganizationContext(
        String code,
        Map<String, Object> variables,
        Map<String, Object> secrets,
        Map<String, Object> overrides
) {

    public static OrganizationContext empty(String code) {
        return new OrganizationContext(code, Map.of(), Map.of(), Map.of());
    }

    /** Valeur racine exposée aux templates : org.code, org.variables.x, org.secrets.x, org.overrides.x. */
    public Map<String, Object> asRoot() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("code", code);
        root.put("variables", variables == null ? Map.of() : variables);
        root.put("secrets", secrets == null ? Map.of() : secrets);
        root.put("overrides", overrides == null ? Map.of() : overrides);
        return root;
    }
}
