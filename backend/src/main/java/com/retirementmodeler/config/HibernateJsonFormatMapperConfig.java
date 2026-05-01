package com.retirementmodeler.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * Wires Spring's auto-configured Jackson {@link ObjectMapper} into Hibernate's JSON format mapper.
 *
 * <p>Without this, Hibernate constructs a private ObjectMapper that lacks the JavaTimeModule, so
 * any column annotated {@code @JdbcTypeCode(SqlTypes.JSON)} containing JSR-310 types
 * (LocalDate/Instant/etc.) round-trips incorrectly. The H2 test path silently tolerates this
 * because of differing JSON SQL-type handling; PostgreSQL's native JSONB does not.
 */
@Configuration
public class HibernateJsonFormatMapperConfig implements HibernatePropertiesCustomizer {

  private final ObjectMapper objectMapper;

  public HibernateJsonFormatMapperConfig(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void customize(Map<String, Object> hibernateProperties) {
    hibernateProperties.put(
        "hibernate.type.json_format_mapper", new JacksonJsonFormatMapper(objectMapper));
  }
}
