-- Installation-wide farm specialization, independent of flower production records.

CREATE TABLE farm_operation_settings (
    id BIGINT PRIMARY KEY,
    state_code VARCHAR(2) NOT NULL,
    primary_enterprise VARCHAR(32) NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_farm_operation_singleton CHECK (id = 1),
    CONSTRAINT ck_farm_operation_state CHECK (state_code IN ('WA', 'OR', 'CA'))
);

CREATE TABLE farm_operation_enterprises (
    settings_id BIGINT NOT NULL,
    enterprise_code VARCHAR(32) NOT NULL,
    PRIMARY KEY (settings_id, enterprise_code),
    CONSTRAINT fk_farm_operation_enterprise_settings FOREIGN KEY (settings_id)
        REFERENCES farm_operation_settings(id) ON DELETE CASCADE
);

CREATE TABLE farm_operation_specialty_crops (
    settings_id BIGINT NOT NULL,
    crop_name VARCHAR(120) NOT NULL,
    PRIMARY KEY (settings_id, crop_name),
    CONSTRAINT fk_farm_operation_specialty_settings FOREIGN KEY (settings_id)
        REFERENCES farm_operation_settings(id) ON DELETE CASCADE
);

CREATE TABLE farm_operation_flower_offerings (
    settings_id BIGINT NOT NULL,
    offering_code VARCHAR(32) NOT NULL,
    PRIMARY KEY (settings_id, offering_code),
    CONSTRAINT fk_farm_operation_offering_settings FOREIGN KEY (settings_id)
        REFERENCES farm_operation_settings(id) ON DELETE CASCADE
);

INSERT INTO farm_operation_settings(id, state_code, primary_enterprise, updated_at)
VALUES (1, 'WA', 'CUT_FLOWERS', CURRENT_TIMESTAMP);

INSERT INTO farm_operation_enterprises(settings_id, enterprise_code)
VALUES (1, 'CUT_FLOWERS');

INSERT INTO farm_operation_flower_offerings(settings_id, offering_code)
VALUES (1, 'STEMS'), (1, 'BOUQUETS'), (1, 'WEDDINGS');
