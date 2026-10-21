-- Suit le dernier tour pour lequel un rappel de cotisation a deja ete envoye a une tontine,
-- pour ne jamais notifier deux fois les membres pour le meme tour (voir PaymentReminderService).
-- NULL = aucun rappel jamais envoye pour cette tontine.
ALTER TABLE tontine ADD COLUMN reminder_sent_round INTEGER;
