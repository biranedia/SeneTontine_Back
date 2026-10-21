-- Ajoute le suivi d'activite necessaire a la session glissante (voir TokenAuthFilter /
-- AuthService cote code) : chaque requete authentifiee met a jour last_used_at et repousse
-- expires_at, dans la limite d'une duree de vie absolue de 30 jours depuis created_at.
ALTER TABLE auth_token ADD COLUMN last_used_at TIMESTAMP;
UPDATE auth_token SET last_used_at = created_at WHERE last_used_at IS NULL;
ALTER TABLE auth_token ALTER COLUMN last_used_at SET NOT NULL;
