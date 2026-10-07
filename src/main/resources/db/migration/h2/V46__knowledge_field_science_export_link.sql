-- A finding may point to the exact reviewed CSRBT export that informed it.
-- Existing lessons remain unlinked; deleting an export keeps the farm lesson.

ALTER TABLE knowledge_entries
    ADD COLUMN field_science_export_id BIGINT;

ALTER TABLE knowledge_entries
    ADD CONSTRAINT fk_knowledge_field_science_export
        FOREIGN KEY (field_science_export_id)
        REFERENCES field_science_exports(id) ON DELETE SET NULL;

CREATE INDEX idx_knowledge_field_science_export
    ON knowledge_entries(field_science_export_id);
