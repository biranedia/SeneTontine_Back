-- Permet de repondre a un message precis du chat de groupe (comme un "reply" WhatsApp),
-- pour cibler clairement a qui/quoi on repond dans une conversation de groupe qui peut
-- melanger plusieurs sujets en meme temps.
ALTER TABLE chat_message ADD COLUMN reply_to_id BIGINT NULL;

ALTER TABLE chat_message
    ADD CONSTRAINT fk_chat_message_reply_to
    FOREIGN KEY (reply_to_id) REFERENCES chat_message(id) ON DELETE SET NULL;
