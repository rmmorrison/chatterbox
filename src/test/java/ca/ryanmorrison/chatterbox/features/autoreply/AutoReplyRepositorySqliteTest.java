package ca.ryanmorrison.chatterbox.features.autoreply;

import ca.ryanmorrison.chatterbox.db.SqliteRepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static ca.ryanmorrison.chatterbox.db.generated.Tables.AUTO_REPLIES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the SQLite migration is wire-compatible with the generated jOOQ classes. */
class AutoReplyRepositorySqliteTest extends SqliteRepositoryTestBase {

    private static final long CHANNEL = 1L;
    private static final long AUTHOR  = 7777L;
    private static final long EDITOR  = 9999L;

    private AutoReplyRepository repo;

    @Override
    protected String migrationLocation() {
        return "classpath:db/migration/autoreply/sqlite";
    }

    @BeforeEach
    void createRepositories() {
        repo = new AutoReplyRepository(dsl);
    }


    @Test
    void insertAndFetchAgainstSqlite() {
        Optional<Long> id = repo.insert(CHANNEL, "(?i)hello", "Hi!", "Greets hello", AUTHOR);
        assertTrue(id.isPresent());
        assertEquals("Hi!", repo.findById(id.get()).orElseThrow().response());
    }

    @Test
    void duplicatePatternIgnoredAgainstSqlite() {
        repo.insert(CHANNEL, "(?i)hi", "Hi!", "first",  AUTHOR);
        Optional<Long> dup = repo.insert(CHANNEL, "(?i)hi", "Yo!", "second", AUTHOR);
        assertTrue(dup.isEmpty());
        assertEquals(1, dsl.fetchCount(AUTO_REPLIES));
    }

    @Test
    void updateStampsEditedFieldsAgainstSqlite() {
        long id = repo.insert(CHANNEL, "(?i)hi", "Hi!", "first", AUTHOR).orElseThrow();
        repo.update(id, "(?i)hello", "Hello!", "updated", EDITOR);
        AutoReplyRule rule = repo.findById(id).orElseThrow();
        assertTrue(rule.edit().isPresent());
        assertEquals(EDITOR, rule.edit().orElseThrow().editedBy());
    }
}
