-- 1. Abonnement createur (Google Play Billing). Essai d'un mois compte depuis l'inscription
-- (app_user.created_at) ; au-dela, creer une nouvelle tontine exige un abonnement actif.
ALTER TABLE app_user ADD COLUMN subscription_expires_at TIMESTAMP;
ALTER TABLE app_user ADD COLUMN subscription_product_id VARCHAR(100);
ALTER TABLE app_user ADD COLUMN subscription_purchase_token VARCHAR(500);

-- 2. Epargne individuelle : objectif personnel, suivi des versements (aucun argent dans l'app).
CREATE TABLE savings_goal (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT NOT NULL REFERENCES app_user(id),
    name               VARCHAR(80) NOT NULL,
    emoji              VARCHAR(16) NOT NULL DEFAULT '🎯',
    target_amount      NUMERIC NOT NULL,
    periodic_amount    NUMERIC,
    cycle_days         INTEGER,
    next_due_date      DATE,
    completed_at       TIMESTAMP,
    created_at         TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_savings_goal_user ON savings_goal(user_id);

CREATE TABLE savings_entry (
    id          BIGSERIAL PRIMARY KEY,
    goal_id     BIGINT NOT NULL REFERENCES savings_goal(id) ON DELETE CASCADE,
    type        VARCHAR(20) NOT NULL,
    amount      NUMERIC NOT NULL,
    note        VARCHAR(120),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_savings_entry_goal ON savings_entry(goal_id);
