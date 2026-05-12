CREATE TABLE task_candidate_user (
    task_id BIGINT NOT NULL,
    username VARCHAR(100) NOT NULL,
    PRIMARY KEY (task_id, username),
    CONSTRAINT fk_task_candidate_user_task FOREIGN KEY (task_id) REFERENCES task(id) ON DELETE CASCADE
);

CREATE TABLE task_candidate_group (
    task_id BIGINT NOT NULL,
    group_code VARCHAR(100) NOT NULL,
    PRIMARY KEY (task_id, group_code),
    CONSTRAINT fk_task_candidate_group_task FOREIGN KEY (task_id) REFERENCES task(id) ON DELETE CASCADE
);

