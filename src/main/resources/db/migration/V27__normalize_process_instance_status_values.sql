UPDATE process_instance
SET status = CASE status
    WHEN '0' THEN 'ACTIVE'
    WHEN '1' THEN 'WAITING'
    WHEN '2' THEN 'COMPLETED'
    WHEN '3' THEN 'FAILED'
    WHEN '4' THEN 'CANCELLED'
    ELSE status
END
WHERE status IN ('0', '1', '2', '3', '4');
