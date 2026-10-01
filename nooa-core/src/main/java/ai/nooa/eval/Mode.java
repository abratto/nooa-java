package ai.nooa.eval;

/**
 * Execution mode for an {@link EvalRunner}.
 *
 * <ul>
 *   <li>{@code LIVE} — call the configured model directly (default).</li>
 *   <li>{@code RECORD} — call the model and record request/response pairs for
 *       deterministic replay (Phase 1).</li>
 *   <li>{@code REPLAY} — serve responses from a recording without calling the
 *       model (Phase 1).</li>
 * </ul>
 */
public enum Mode {
    LIVE,
    RECORD,
    REPLAY
}
