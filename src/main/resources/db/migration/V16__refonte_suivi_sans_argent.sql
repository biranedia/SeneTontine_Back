-- Refonte "suivi sans argent" : l'application ne fait plus transiter aucun argent. Les membres
-- paient directement le createur via Wave / Orange Money, declarent leur paiement dans l'app,
-- et le createur le confirme. Le KYC, le chat interne, les superviseurs de retraits et tout le
-- circuit de portefeuilles/retraits/commission sont retires.

-- 1. Circuit d'argent interne, KYC et chat : tables devenues inutiles.
DROP TABLE IF EXISTS wallet_transaction CASCADE;
DROP TABLE IF EXISTS wallet CASCADE;
DROP TABLE IF EXISTS withdrawal_request CASCADE;
DROP TABLE IF EXISTS kyc_verification CASCADE;
DROP TABLE IF EXISTS chat_message CASCADE;

ALTER TABLE app_user DROP COLUMN IF EXISTS kyc_status;
ALTER TABLE tontine_member DROP COLUMN IF EXISTS supervisor;

-- 2. Numeros sur lesquels les membres envoient leur cotisation (au moins un des deux est
-- exige a la creation d'une tontine, voir TontineService).
ALTER TABLE tontine ADD COLUMN wave_phone VARCHAR(30);
ALTER TABLE tontine ADD COLUMN orange_money_phone VARCHAR(30);

-- 3. Cycle declaration -> confirmation d'une cotisation.
ALTER TABLE payment ADD COLUMN declared_at TIMESTAMP;
ALTER TABLE payment ADD COLUMN confirmed_at TIMESTAMP;
ALTER TABLE payment ADD COLUMN reference VARCHAR(120);

-- 4. Versement du pot au beneficiaire d'un tour, fait par le createur hors de l'app :
-- trace "envoye" (createur) puis "recu" (beneficiaire), pour la transparence du groupe.
CREATE TABLE pot_payout (
    id              BIGSERIAL PRIMARY KEY,
    tontine_id      BIGINT NOT NULL REFERENCES tontine(id),
    round_number    INTEGER NOT NULL,
    beneficiary_id  BIGINT NOT NULL REFERENCES app_user(id),
    amount          NUMERIC NOT NULL,
    sent_at         TIMESTAMP,
    received_at     TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_pot_payout_round UNIQUE (tontine_id, round_number)
);
CREATE INDEX idx_pot_payout_beneficiary ON pot_payout(beneficiary_id);
