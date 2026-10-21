-- Chat de groupe par tontine (voir ChatMessage.java / ChatService.java).
CREATE TABLE chat_message (
    id          BIGSERIAL PRIMARY KEY,
    tontine_id  BIGINT NOT NULL REFERENCES tontine(id),
    author_id   BIGINT NOT NULL REFERENCES app_user(id),
    text        VARCHAR(2000) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

-- Le pattern d'acces le plus frequent : tous les messages d'une tontine, dans l'ordre.
CREATE INDEX idx_chat_message_tontine ON chat_message(tontine_id, id);
