CREATE TABLE IF NOT EXISTS agent_workspace_store (
  store_key CHAR(64) NOT NULL,
  namespace_hash CHAR(64) NOT NULL,
  namespace_key VARCHAR(1000) NOT NULL,
  item_key VARCHAR(1000) NOT NULL,
  value_json LONGTEXT NOT NULL,
  version BIGINT NOT NULL DEFAULT 1,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (store_key),
  INDEX idx_agent_workspace_namespace_hash (namespace_hash)
);
