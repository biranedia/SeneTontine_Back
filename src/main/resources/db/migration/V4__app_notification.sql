CREATE TABLE app_notification (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES app_user(id),
    type        VARCHAR(40) NOT NULL,
    title       VARCHAR(255) NOT NULL,
    body        VARCHAR(500) NOT NULL,
    read        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_app_notification_user ON app_notification(user_id, id);
