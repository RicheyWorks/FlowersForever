-- Keep the saved primary workspace and allow additional farm types on one installation.
-- Existing installations begin with only their currently selected workspace.
CREATE TABLE farm_operation_workspaces (
    settings_id BIGINT NOT NULL,
    display_order INTEGER NOT NULL,
    workspace_code VARCHAR(32) NOT NULL,
    PRIMARY KEY (settings_id, display_order),
    CONSTRAINT fk_farm_operation_workspace_settings FOREIGN KEY (settings_id)
        REFERENCES farm_operation_settings(id) ON DELETE CASCADE,
    CONSTRAINT ck_farm_operation_workspace_order CHECK (display_order >= 0),
    CONSTRAINT ck_farm_operation_enabled_workspace CHECK (
        workspace_code IN ('ROSE_AND_EVENTS', 'WILDFLOWER_FARM',
                           'FLOWER_SHOP', 'CANNABIS_FARM', 'POTATO_FARM',
                           'APPLE_ORCHARD', 'BERRY_FARM', 'NURSERY_FARM',
                           'CUSTOM_FARM'))
);

INSERT INTO farm_operation_workspaces(settings_id, display_order, workspace_code)
SELECT id, 0, workspace_code FROM farm_operation_settings;
