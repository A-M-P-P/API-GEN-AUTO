package com.apigen.config;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Mapper XML partagé entre le parsing des corps entrants et la sérialisation des réponses. */
@Configuration
public class JacksonXmlConfig {

    @Bean
    public XmlMapper xmlMapper() {
        return new XmlMapper();
    }
}
