DELETE FROM task_variable tv
USING task_variable newer
WHERE tv.task_id = newer.task_id
  AND tv.name = newer.name
  AND tv.id < newer.id;

ALTER TABLE task_variable
ADD CONSTRAINT uk_task_variable_task_name UNIQUE (task_id, name);
