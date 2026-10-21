-- Permet a un admin de suspendre un compte (fraude suspectee, litige...) sans avoir a le
-- supprimer. NOT NULL DEFAULT TRUE : tous les comptes existants restent actifs par defaut,
-- rien ne change pour personne tant qu'un admin n'a pas explicitement bloque un compte.
ALTER TABLE app_user ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
