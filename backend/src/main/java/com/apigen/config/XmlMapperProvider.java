package com.apigen.config;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.stereotype.Component;

/**
 * Fournit un XmlMapper interne aux services, **sans l'exposer comme bean ObjectMapper**.
 *
 * <p>Un bean de type ObjectMapper (XmlMapper en est un) serait repris par Spring Boot comme
 * convertisseur JSON par défaut : les corps JSON seraient alors analysés comme du XML et les
 * erreurs sérialisées en XML. Ce composant évite cette ambiguïté.</p>
 */
@Component
public class XmlMapperProvider {

    private final XmlMapper mapper = new XmlMapper().registerModule(new JavaTimeModule());

    public XmlMapper mapper() {
        return mapper;
    }
}
