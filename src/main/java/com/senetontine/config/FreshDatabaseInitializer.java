package com.senetontine.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

/**
 * Les migrations V2..V19 supposent un schéma de départ (V1) créé autrefois par Hibernate.
 * Sur une base NEUVE (production), on crée donc d'abord le schéma complet actuel
 * (db/schema/schema-v19.sql, tiré de pg_dump), on marque la base « version 19 », puis Flyway
 * n'applique que les migrations suivantes. Une base existante suit le chemin normal.
 */
@Configuration
@Slf4j
public class FreshDatabaseInitializer {

    private static final String SCHEMA = "db/schema/schema-v19.sql";
    private static final String SCHEMA_VERSION = "19";

    @Bean
    public FlywayMigrationStrategy freshDatabaseStrategy() {
        return flyway -> {
            DataSource ds = flyway.getConfiguration().getDataSource();
            if (isEmpty(ds)) {
                log.info("Base vide : création du schéma complet (version {})", SCHEMA_VERSION);
                runSchema(ds);
                Flyway.configure()
                        .configuration(flyway.getConfiguration())
                        .baselineVersion(SCHEMA_VERSION)
                        .baselineDescription("schema complet initial")
                        .load()
                        .baseline();
            }
            flyway.migrate();
        };
    }

    private static boolean isEmpty(DataSource ds) {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "select count(*) from information_schema.tables where table_schema = 'public'")) {
            rs.next();
            return rs.getLong(1) == 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Impossible d'inspecter la base", e);
        }
    }

    private static void runSchema(DataSource ds) {
        String sql;
        try {
            sql = new ClassPathResource(SCHEMA).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(SCHEMA + " introuvable", e);
        }
        // pg_dump ajoute des méta-commandes psql (\restrict...) que JDBC ne comprend pas.
        sql = sql.lines().filter(l -> !l.startsWith("\\")).collect(Collectors.joining("\n"));
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
            st.execute("SET search_path TO public");
        } catch (SQLException e) {
            throw new IllegalStateException("Échec de création du schéma initial", e);
        }
    }
}
