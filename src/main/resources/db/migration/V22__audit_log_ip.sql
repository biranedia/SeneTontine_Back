-- Adresse IP de l'acteur au moment de l'action (IPv4 ou IPv6, max 45 car.).
-- NULL pour les actions anterieures a cette migration.
ALTER TABLE audit_log ADD COLUMN actor_ip VARCHAR(45);
