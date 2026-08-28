ALTER TABLE app_user
    ADD COLUMN identity_provider VARCHAR(50) NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN external_identity_id VARCHAR(200),
    ADD COLUMN email VARCHAR(255),
    ADD COLUMN first_name VARCHAR(100),
    ADD COLUMN last_name VARCHAR(100);

CREATE UNIQUE INDEX uk_app_user_identity_provider_external_id
    ON app_user(identity_provider, external_identity_id)
    WHERE external_identity_id IS NOT NULL;
