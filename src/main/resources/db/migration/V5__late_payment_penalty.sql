-- Active reellement penaltyPerDay (jusqu'ici configure mais jamais applique) : voir
-- PaymentService.pay pour le calcul, PaymentService.advanceRoundIfComplete et
-- TontineService.join pour la gestion du cycle d'echeance (next_turn_date).
ALTER TABLE tontine ADD COLUMN cycle_duration_days INTEGER;
UPDATE tontine SET cycle_duration_days = 30 WHERE cycle_duration_days IS NULL;
ALTER TABLE tontine ALTER COLUMN cycle_duration_days SET NOT NULL;

ALTER TABLE payment ADD COLUMN penalty_amount NUMERIC;
UPDATE payment SET penalty_amount = 0 WHERE penalty_amount IS NULL;
ALTER TABLE payment ALTER COLUMN penalty_amount SET NOT NULL;
