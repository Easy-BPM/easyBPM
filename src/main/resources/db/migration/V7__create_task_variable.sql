-- Tabela de variáveis de tarefa
CREATE TABLE task_variable (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    value TEXT
);