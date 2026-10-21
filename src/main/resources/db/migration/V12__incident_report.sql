-- Signalement d'incident cote membre (fraude suspectee, litige de paiement, comportement
-- abusif, bug technique...), traite ensuite par un admin plateforme. target_type/target_id
-- sont facultatifs et non contraints par cle etrangere : la cible peut etre une tontine, un
-- utilisateur, un paiement, ou rien de precis (probleme general) - une FK unique vers une
-- seule table n'aurait pas pu couvrir tous les cas, et le lien reste de toute facon
-- informatif uniquement (l'admin revoit le contexte manuellement, jamais d'action automatique
-- basee dessus).
CREATE TABLE incident_report (
    id BIGSERIAL PRIMARY KEY,
    reporter_id BIGINT NOT NULL REFERENCES app_user(id),
    category VARCHAR(50) NOT NULL,
    target_type VARCHAR(50),
    target_id BIGINT,
    description VARCHAR(2000) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    admin_note VARCHAR(1000),
    resolved_by_id BIGINT REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ
);

-- Acces le plus frequent : la file des signalements ouverts pour un admin.
CREATE INDEX idx_incident_report_status ON incident_report(status);
-- "Mes signalements" cote membre.
CREATE INDEX idx_incident_report_reporter ON incident_report(reporter_id);
