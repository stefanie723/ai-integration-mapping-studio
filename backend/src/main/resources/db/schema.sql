-- AI Integration Mapping Studio - Phase 1 Database Schema
-- Compatible with MySQL 8+ / H2 (MODE=MySQL)

CREATE TABLE IF NOT EXISTS customer (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(64)  NOT NULL UNIQUE,
    name        VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS integration_scenario (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(64)  NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    source_api      VARCHAR(256),
    target_form_id  VARCHAR(128)
);

CREATE TABLE IF NOT EXISTS mapping_configuration (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id     BIGINT       NOT NULL,
    scenario_code   VARCHAR(64)  NOT NULL,
    source_api      VARCHAR(256) NOT NULL,
    target_form_id  VARCHAR(128) NOT NULL,
    UNIQUE KEY uk_customer_scenario (customer_id, scenario_code)
);

CREATE TABLE IF NOT EXISTS field_mapping (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    configuration_id    BIGINT       NOT NULL,
    target_field        VARCHAR(256) NOT NULL,
    target_field_name   VARCHAR(128),
    mapping_type        VARCHAR(32)  NOT NULL,
    source_field        VARCHAR(256),
    fixed_value         VARCHAR(512),
    default_value       VARCHAR(512),
    expression          VARCHAR(1024),
    dictionary          TEXT,
    confidence          DOUBLE,
    ai_reason           VARCHAR(1024),
    confirmed           BOOLEAN      NOT NULL DEFAULT FALSE,
    target_required     BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_field_mapping_config FOREIGN KEY (configuration_id)
        REFERENCES mapping_configuration (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS mapping_history (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_field        VARCHAR(256) NOT NULL,
    source_field_name   VARCHAR(128),
    target_form_id      VARCHAR(128) NOT NULL,
    target_field        VARCHAR(256) NOT NULL,
    target_field_name   VARCHAR(128),
    usage_count         INT          NOT NULL DEFAULT 1,
    INDEX idx_history_source_target (source_field, target_form_id, target_field)
);
