package ai.nooa.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite-backed history of evaluation runs. Stores each {@link EvalReport} as
 * JSON alongside denormalized headline metrics so trends can be queried
 * without deserializing the full report.
 *
 * <p>This is the optional Phase 3 history layer; JSON and Markdown reports plus
 * {@link EvalBaseline} remain sufficient for single-run comparisons.</p>
 */
public final class EvalHistoryStore implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EvalHistoryStore.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String dbPath;

    public EvalHistoryStore(String dbPath) {
        this.dbPath = dbPath;
        initSchema();
    }

    private Connection connect() {
        try {
            return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot open eval history store: " + dbPath, e);
        }
    }

    private void initSchema() {
        try (var conn = connect(); var statement = conn.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS eval_runs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    run_at TEXT NOT NULL,
                    dataset_id TEXT NOT NULL,
                    agent_class TEXT NOT NULL,
                    mode TEXT NOT NULL,
                    trials INTEGER NOT NULL,
                    weighted_mean REAL NOT NULL,
                    pass_at1 REAL NOT NULL,
                    pass_at_k REAL NOT NULL,
                    pass_hat_k REAL NOT NULL,
                    report_json TEXT NOT NULL
                )
            """);
            statement.execute(
                "CREATE INDEX IF NOT EXISTS idx_eval_runs_dataset ON eval_runs(dataset_id)");
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot initialize eval history schema", e);
        }
    }

    /** Persist one run; returns the assigned row id. */
    public long record(EvalReport report) {
        String sql = """
            INSERT INTO eval_runs
            (run_at, dataset_id, agent_class, mode, trials, weighted_mean,
             pass_at1, pass_at_k, pass_hat_k, report_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        CompletionMetrics c = report.aggregate().completion();
        try (var conn = connect(); var ps = conn.prepareStatement(sql,
                java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, report.runAt() == null ? Instant.now().toString() : report.runAt());
            ps.setString(2, report.datasetId());
            ps.setString(3, report.agentClass());
            ps.setString(4, report.mode());
            ps.setInt(5, report.trials());
            ps.setDouble(6, report.aggregate().weightedMean());
            ps.setDouble(7, c.passAt1());
            ps.setDouble(8, c.passAtK());
            ps.setDouble(9, c.passHatK());
            ps.setString(10, report.toJson());
            ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to record eval run", e);
        }
    }

    /** Most recent runs for a dataset, newest first. */
    public List<RunSummary> recent(String datasetId, int limit) {
        String sql = """
            SELECT id, run_at, dataset_id, agent_class, mode, trials,
                   weighted_mean, pass_at1, pass_at_k, pass_hat_k
            FROM eval_runs WHERE dataset_id = ? ORDER BY id DESC LIMIT ?
        """;
        List<RunSummary> summaries = new ArrayList<>();
        try (var conn = connect(); var ps = conn.prepareStatement(sql)) {
            ps.setString(1, datasetId);
            ps.setInt(2, Math.max(1, limit));
            var rs = ps.executeQuery();
            while (rs.next()) {
                summaries.add(new RunSummary(
                    rs.getLong("id"),
                    rs.getString("run_at"),
                    rs.getString("dataset_id"),
                    rs.getString("agent_class"),
                    rs.getString("mode"),
                    rs.getInt("trials"),
                    rs.getDouble("weighted_mean"),
                    rs.getDouble("pass_at1"),
                    rs.getDouble("pass_at_k"),
                    rs.getDouble("pass_hat_k")));
            }
        } catch (SQLException e) {
            log.debug("Eval history query failed", e);
        }
        return summaries;
    }

    /** Load a previously recorded report by row id. */
    public Optional<EvalReport> load(long id) {
        try (var conn = connect();
             var ps = conn.prepareStatement("SELECT report_json FROM eval_runs WHERE id = ?")) {
            ps.setLong(1, id);
            var rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(JSON.readValue(rs.getString("report_json"), EvalReport.class));
            }
        } catch (SQLException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.debug("Eval history load failed for id {}", id, e);
        }
        return Optional.empty();
    }

    public record RunSummary(
        long id, String runAt, String datasetId, String agentClass, String mode,
        int trials, double weightedMean, double passAt1, double passAtK, double passHatK) {}

    @Override
    public void close() {
        // Connections are short-lived; nothing persistent to release.
    }
}
