ALTER TABLE form
ADD COLUMN form_key VARCHAR(255);

WITH distinct_names AS (
    SELECT
        name,
        CASE
            WHEN name ~ '^[A-Za-z][A-Za-z0-9_-]*$' THEN name
            ELSE CONCAT(
                COALESCE(NULLIF(regexp_replace(lower(name), '[^a-z0-9]+', '_', 'g'), ''), 'form'),
                '_',
                substring(md5(name) from 1 for 8)
            )
        END AS generated_key
    FROM form
    GROUP BY name
)
UPDATE form
SET form_key = distinct_names.generated_key
FROM distinct_names
WHERE form.name = distinct_names.name;

ALTER TABLE form
ALTER COLUMN form_key SET NOT NULL;

CREATE UNIQUE INDEX uk_form_form_key_version ON form(form_key, version);
