CREATE TABLE app_permission (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE app_group (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE app_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE TABLE app_user_group (
    user_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, group_id),
    CONSTRAINT fk_app_user_group_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_app_user_group_group FOREIGN KEY (group_id) REFERENCES app_group(id) ON DELETE CASCADE
);

CREATE TABLE app_group_permission (
    group_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (group_id, permission_id),
    CONSTRAINT fk_app_group_permission_group FOREIGN KEY (group_id) REFERENCES app_group(id) ON DELETE CASCADE,
    CONSTRAINT fk_app_group_permission_permission FOREIGN KEY (permission_id) REFERENCES app_permission(id) ON DELETE CASCADE
);

CREATE TABLE app_user_permission (
    user_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, permission_id),
    CONSTRAINT fk_app_user_permission_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_app_user_permission_permission FOREIGN KEY (permission_id) REFERENCES app_permission(id) ON DELETE CASCADE
);

