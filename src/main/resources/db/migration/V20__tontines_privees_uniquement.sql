-- Les tontines publiques sont supprimées pour éviter les arnaques : toutes deviennent privées
-- (accessibles uniquement par code d'invitation ou ajout direct par le créateur).
UPDATE tontine SET visibility = 'PRIVATE' WHERE visibility <> 'PRIVATE';
