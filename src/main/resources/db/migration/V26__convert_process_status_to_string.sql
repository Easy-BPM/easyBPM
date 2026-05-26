-- Convert ProcessStatus enum values from ordinal integers to string names
-- Maps: 0->ACTIVE, 1->WAITING, 2->COMPLETED, 3->FAILED, 4->CANCELLED, 5->SUSPENDED, 6->ERROR

UPDATE process_instance
SET status = CASE 
    WHEN status = '0' THEN 'ACTIVE'
    WHEN status = '1' THEN 'WAITING'
    WHEN status = '2' THEN 'COMPLETED'
    WHEN status = '3' THEN 'FAILED'
    WHEN status = '4' THEN 'CANCELLED'
    WHEN status = '5' THEN 'SUSPENDED'
    WHEN status = '6' THEN 'ERROR'
    ELSE status
END
WHERE status IN ('0', '1', '2', '3', '4', '5', '6');
