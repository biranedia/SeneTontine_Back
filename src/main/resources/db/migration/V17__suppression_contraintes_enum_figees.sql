-- Les premieres tables ont ete creees par Hibernate (ddl-auto) avant l'adoption de Flyway.
-- Hibernate y a ajoute des contraintes CHECK figeant la liste des valeurs de chaque enum
-- (ex: payment.status IN ('DUE','PAID','LATE')). Toute nouvelle valeur ajoutee cote Java
-- (DECLARED, COMPLETED, nouveaux types de notification...) etait alors rejetee par PostgreSQL.
-- La validite des valeurs est deja garantie par les enums Java : on retire ces contraintes.
DO $$
DECLARE r record;
BEGIN
    FOR r IN
        SELECT conrelid::regclass AS tbl, conname
        FROM pg_constraint
        WHERE contype = 'c'
          AND connamespace = 'public'::regnamespace
          AND pg_get_constraintdef(oid) LIKE '%ARRAY[%'
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.tbl, r.conname);
    END LOOP;
END $$;
