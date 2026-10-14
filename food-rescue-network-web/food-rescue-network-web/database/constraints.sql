-- ============================================================================
-- FOOD RESCUE NETWORK - CONSTRAINTS AND INTEGRITY VERIFICATION SCRIPT
-- ============================================================================

USE food_rescue;

-- Ensure foreign keys and integrity constraints are explicitly verified
-- 1. Email uniqueness on USERS
-- 2. Role constraints on USERS (Provider vs Requester)
-- 3. Quantity invariants on FOOD_DONATIONS
-- 4. Quantity request invariants on PICKUP_REQUESTS

-- Check table status and storage engines (Must be InnoDB for transaction safety)
SELECT table_name, engine, table_collation, table_rows
FROM information_schema.tables
WHERE table_schema = 'food_rescue';

-- Check Foreign Key Constraints
SELECT 
    table_name AS 'Table',
    constraint_name AS 'Constraint',
    referenced_table_name AS 'Referenced Table'
FROM information_schema.key_column_usage
WHERE table_schema = 'food_rescue' AND referenced_table_name IS NOT NULL;

-- Check Check Constraints
SELECT 
    constraint_name, 
    check_clause 
FROM information_schema.check_constraints 
WHERE constraint_schema = 'food_rescue';
