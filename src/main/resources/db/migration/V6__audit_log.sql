CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    BIGINT NOT NULL REFERENCES app_user(id),
    action      VARCHAR(40) NOT NULL,
    target_type VARCHAR(60) NOT NULL,
    target_id   BIGINT NOT NULL,
    details     VARCHAR(1000),
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_log_created_at ON audit_log(id DESC);
