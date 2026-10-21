-- Remplacement de l'abonnement mensuel Google Play (jamais active) par un paiement par
-- tontine via NabooPay : la 1re tontine d'un createur est offerte, chaque creation
-- supplementaire et chaque renouvellement coute un "droit" achete une fois.
ALTER TABLE app_user DROP COLUMN IF EXISTS subscription_expires_at;
ALTER TABLE app_user DROP COLUMN IF EXISTS subscription_product_id;
ALTER TABLE app_user DROP COLUMN IF EXISTS subscription_purchase_token;

CREATE TABLE creator_payment (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES app_user(id),
    purpose       VARCHAR(20) NOT NULL,          -- CREATE | RENEW
    tontine_id    BIGINT REFERENCES tontine(id), -- tontine a renouveler (RENEW)
    amount        NUMERIC NOT NULL,
    order_id      VARCHAR(100) UNIQUE,           -- identifiant NabooPay
    checkout_url  VARCHAR(1000),
    status        VARCHAR(20) NOT NULL,          -- PENDING | PAID | CANCELLED
    consumed_at   TIMESTAMP,                     -- droit utilise (tontine creee/renouvelee)
    paid_at       TIMESTAMP,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_creator_payment_user ON creator_payment(user_id);

-- Une tontine terminee ne peut etre renouvelee qu'une seule fois.
ALTER TABLE tontine ADD COLUMN renewed_from_id BIGINT UNIQUE REFERENCES tontine(id);
