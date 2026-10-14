-- ============================================================================
-- FOOD RESCUE NETWORK - VERIFICATION AND DIAGNOSTIC SCRIPT
-- ============================================================================

USE food_rescue;

-- 1. Table Counts
SELECT 'users' AS entity, COUNT(*) AS total_records FROM users
UNION ALL
SELECT 'food_donations', COUNT(*) FROM food_donations
UNION ALL
SELECT 'pickup_requests', COUNT(*) FROM pickup_requests
UNION ALL
SELECT 'alerts', COUNT(*) FROM alerts;

-- 2. Quantity Consistency Audit
-- Ensures remaining_quantity <= quantity for all donations
SELECT 
    donation_id,
    food_name,
    quantity,
    remaining_quantity,
    status
FROM food_donations
WHERE remaining_quantity > quantity
   OR remaining_quantity < 0;

-- 3. Overbooking Audit
-- Sum of APPROVED and COMPLETED requests should never exceed total quantity
SELECT 
    d.donation_id,
    d.food_name,
    d.quantity AS total_published_quantity,
    d.remaining_quantity,
    COALESCE(SUM(r.requested_quantity), 0) AS total_approved_or_completed
FROM food_donations d
LEFT JOIN pickup_requests r ON d.donation_id = r.donation_id AND r.status IN ('APPROVED', 'COMPLETED')
GROUP BY d.donation_id, d.food_name, d.quantity, d.remaining_quantity
HAVING total_approved_or_completed > d.quantity;

-- 4. Expired Donation Audit
-- Any available or reserved donation past deadline should be flagged
SELECT 
    donation_id,
    food_name,
    pickup_deadline,
    NOW() AS current_time,
    status
FROM food_donations
WHERE pickup_deadline < NOW() AND status IN ('AVAILABLE', 'RESERVED');
