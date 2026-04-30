import org.testcontainers.containers.PostgreSQLContainer
import org.flywaydb.core.Flyway
import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.Target
import org.jooq.meta.jaxb.Generate

/*
 * Spins up an ephemeral PostgreSQL via Testcontainers, applies all Flyway
 * migrations from src/main/resources/db/migration, then runs jOOQ codegen
 * against the resulting schema. Output lands in target/generated-sources/jooq.
 *
 * Invoked by groovy-maven-plugin during `mvn -Pcodegen generate-sources`.
 * Requires Docker on the host.
 */

def migrationsRoot = new File(project.basedir, "src/main/resources/db/migration")
if (!migrationsRoot.exists()) {
    log.warn("No migrations directory at ${migrationsRoot}; skipping jOOQ codegen.")
    return
}

// Mirrors Migrations.java: each module ships its migrations under
// db/migration/<module>/postgresql/ (and /sqlite/). For codegen we only run
// the postgresql variants.
def locations = []
migrationsRoot.eachDir { moduleDir ->
    def pgDir = new File(moduleDir, "postgresql")
    if (pgDir.isDirectory()) {
        locations << "filesystem:${pgDir.absolutePath}".toString()
    }
}
if (locations.isEmpty()) {
    log.warn("No postgresql migration directories under ${migrationsRoot}; nothing to generate.")
    return
}

def pg = new PostgreSQLContainer("postgres:17-alpine")
        .withDatabaseName("chatterbox")
        .withUsername("chatterbox")
        .withPassword("chatterbox")
pg.start()

try {
    log.info("Postgres container ready at ${pg.jdbcUrl}")

    Flyway.configure()
            .dataSource(pg.jdbcUrl, pg.username, pg.password)
            .locations(locations as String[])
            .load()
            .migrate()

    def configuration = new Configuration()
            .withJdbc(new Jdbc()
                    .withDriver("org.postgresql.Driver")
                    .withUrl(pg.jdbcUrl)
                    .withUser(pg.username)
                    .withPassword(pg.password))
            .withGenerator(new Generator()
                    .withDatabase(new Database()
                            .withName("org.jooq.meta.postgres.PostgresDatabase")
                            .withInputSchema("public")
                            .withExcludes("flyway_schema_history"))
                    .withGenerate(new Generate()
                            .withPojos(false)
                            .withDaos(false)
                            .withRecords(true)
                            .withFluentSetters(false))
                    .withTarget(new Target()
                            .withPackageName("ca.ryanmorrison.chatterbox.db.generated")
                            .withDirectory("${project.basedir}/src/generated/jooq".toString())
                            .withClean(true)))

    GenerationTool.generate(configuration)
    log.info("jOOQ codegen complete.")
} finally {
    pg.stop()
}
