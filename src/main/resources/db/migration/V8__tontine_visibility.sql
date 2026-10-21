-- Visibilite d'une tontine : PRIVATE (defaut, rejoignable uniquement via code d'invitation ou
-- ajout direct) ou PUBLIC (en plus decouvrable via /api/tontines/discover). Toutes les
-- tontines existantes deviennent PRIVATE par defaut - comportement inchange pour elles tant
-- que leur createur ne les rend pas publiques explicitement.
ALTER TABLE tontine ADD COLUMN visibility VARCHAR(20);
UPDATE tontine SET visibility = 'PRIVATE' WHERE visibility IS NULL;
ALTER TABLE tontine ALTER COLUMN visibility SET NOT NULL;
