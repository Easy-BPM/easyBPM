-- Tabela de variáveis de processo
CREATE TABLE process_variable(
    id BIGSERIAL PRIMARY KEY,
    process_instance_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    value TEXT
);