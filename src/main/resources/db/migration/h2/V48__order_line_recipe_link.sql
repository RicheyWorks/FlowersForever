-- Stem demand board: an order line can be counted as a saved floral recipe
-- ("Centerpiece x 12" -> stems per design x 12 + waste). The link is optional;
-- lines sold in stems are counted directly and other lines are listed as not counted.
-- Deleting a recipe clears the link instead of deleting order history.

ALTER TABLE order_lines ADD COLUMN recipe_id BIGINT;

ALTER TABLE order_lines ADD CONSTRAINT fk_order_line_recipe
    FOREIGN KEY (recipe_id) REFERENCES floral_recipes(id) ON DELETE SET NULL;

CREATE INDEX idx_order_line_recipe ON order_lines(recipe_id);
