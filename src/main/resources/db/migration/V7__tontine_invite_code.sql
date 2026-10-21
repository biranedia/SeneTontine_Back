-- Code court partageable pour rejoindre une tontine (invitation WhatsApp / SMS / ajout par
-- numero) sans exposer ni avoir besoin de connaitre l'ID technique. Voir Tontine.inviteCode et
-- TontineService.generateUniqueInviteCode / joinByCode / addMemberByPhone.
ALTER TABLE tontine ADD COLUMN invite_code VARCHAR(8);

-- Backfill des tontines existantes avec un code aleatoire base sur leur ID (unique par
-- construction puisque l'ID l'est). Format volontairement different du generateur applicatif
-- (qui evite 0/O/1/I) : ce n'est qu'un backfill unique, pas un chemin utilise en continu.
UPDATE tontine SET invite_code = upper(substr(md5(id::text || random()::text), 1, 8))
WHERE invite_code IS NULL;

ALTER TABLE tontine ALTER COLUMN invite_code SET NOT NULL;
ALTER TABLE tontine ADD CONSTRAINT uk_tontine_invite_code UNIQUE (invite_code);
