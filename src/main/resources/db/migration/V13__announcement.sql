CREATE TABLE announcement (
    id BIGSERIAL PRIMARY KEY,
    message VARCHAR(1000) NOT NULL,
    created_by BIGINT NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_announcement_created_at ON announcement (created_at DESC);
