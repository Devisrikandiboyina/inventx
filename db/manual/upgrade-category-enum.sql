-- Upgrade for an EXISTING InventX MySQL database whose ideas.category is still the old ENUM.
-- Run by hand, in order, after taking a backup. Flyway does not run this file.

-- 1. Free the column from the old ENUM so legacy values can be rewritten.
ALTER TABLE ideas MODIFY category VARCHAR(40) NOT NULL;

-- 2. List legacy values still in use, then map each to a new code, for example:
--    SELECT category, COUNT(*) FROM ideas GROUP BY category;
--    UPDATE ideas SET category = 'ROBOTICS' WHERE category = '<legacy value>';

-- 3. Only when step 2 leaves no other value, switch to the new 12-code ENUM.
ALTER TABLE ideas MODIFY category ENUM(
  'AI_DATA_ANALYTICS','AR_VR','CUSTOMER_BUSINESS_SOLUTIONS','DIGITAL_MANUFACTURING','DIGITAL_TWINS',
  'ENERGY_SUSTAINABILITY','INDUSTRIAL_AUTOMATION','MODELLING_SIMULATION','PRODUCT_PROCESS_INNOVATION',
  'QUALITY_RELIABILITY','ROBOTICS','SAFETY_ERGONOMICS') NOT NULL;

-- 4. Add the newer tables/columns by comparing with src/main/resources/db/migration/V1__baseline.sql:
--    announcements.state/publish_at, stage_configs.start_date/end_date/updated_by, idea_tags,
--    audit_logs, whatsapp_delivery_attempts.
--    Then start the app with SPRING_FLYWAY_BASELINE_ON_MIGRATE=true and SPRING_FLYWAY_BASELINE_VERSION=1.