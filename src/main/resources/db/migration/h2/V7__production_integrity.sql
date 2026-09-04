-- Tighten production references and workflow-state integrity without removing records.

UPDATE production_tasks task
SET growing_template_id = NULL
WHERE growing_template_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM growing_templates template WHERE template.id = task.growing_template_id
  );

ALTER TABLE production_tasks ADD CONSTRAINT fk_production_task_template
    FOREIGN KEY (growing_template_id) REFERENCES growing_templates(id) ON DELETE SET NULL;
ALTER TABLE production_tasks ADD CONSTRAINT chk_production_task_status
    CHECK (status IN ('TODO', 'DONE', 'SKIPPED'));
ALTER TABLE production_tasks ADD CONSTRAINT chk_production_task_type
    CHECK (task_type IN ('SOIL_PREP', 'SOW', 'TRANSPLANT', 'SUPPORT',
                         'HARVEST_PREP', 'FIRST_HARVEST', 'CUSTOM'));

UPDATE integration_handoffs handoff
SET related_order_id = NULL
WHERE related_order_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM customer_orders customer_order
    WHERE customer_order.id = handoff.related_order_id
  );

UPDATE integration_handoffs handoff
SET related_recipe_id = NULL
WHERE related_recipe_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM floral_recipes recipe WHERE recipe.id = handoff.related_recipe_id
  );

UPDATE integration_handoffs
SET external_reference = NULL
WHERE TRIM(external_reference) = '';

ALTER TABLE integration_handoffs ALTER COLUMN external_reference DROP NOT NULL;
ALTER TABLE integration_handoffs ADD CONSTRAINT fk_handoff_order
    FOREIGN KEY (related_order_id) REFERENCES customer_orders(id) ON DELETE SET NULL;
ALTER TABLE integration_handoffs ADD CONSTRAINT fk_handoff_recipe
    FOREIGN KEY (related_recipe_id) REFERENCES floral_recipes(id) ON DELETE SET NULL;
ALTER TABLE integration_handoffs ADD CONSTRAINT chk_handoff_type
    CHECK (handoff_type IN ('CONTRACT', 'PAYMENT', 'MARKETPLACE'));
ALTER TABLE integration_handoffs ADD CONSTRAINT chk_handoff_status
    CHECK (status IN ('DRAFT', 'READY', 'SENT', 'ACCEPTED', 'PAID', 'FAILED', 'CANCELLED'));
ALTER TABLE integration_handoffs ADD CONSTRAINT chk_handoff_currency
    CHECK (CHAR_LENGTH(currency) = 3 AND currency = UPPER(currency));
ALTER TABLE integration_handoffs ADD CONSTRAINT uk_handoff_provider_reference
    UNIQUE (handoff_type, provider, external_reference);
