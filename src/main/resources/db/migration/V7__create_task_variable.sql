-- Tabela de variáveis de tarefa
CREATE TABLE task_variable (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    value TEXT
);