package ai.nooa.clad.runtime.engine;

/**
 * Base class for sync agents. A sync is a declarative coordination rule:
 * when concept X produces outcome Y, create invocation Z for concept W.
 *
 * <p>Subclasses implement:
 * <ul>
 *   <li>{@link #trigger()} — which concept/action/outcome activates this sync</li>
 *   <li>{@link #thenBindings()} — the new invocation to write</li>
 * </ul>
 *
 * <p>Syncs are stateless and contain no branching domain logic.</p>
 */
public abstract class SyncAgent {

    /** Which concept's action with what outcome activates this sync. */
    public abstract SyncTrigger trigger();

    /**
     * Returns the new invocation to create when this sync fires.
     * The bindings map should include at minimum:
     * <ul>
     *   <li>{@code "concept"} — the target concept IRI</li>
     *   <li>{@code "action"} — the action name to invoke</li>
     *   <li>any input parameters the target concept needs</li>
     * </ul>
     */
    public abstract ActionRecord thenInvocation(ActionRecord source);

    /** Unique camelCase ID used for dedup guard. */
    public abstract String syncName();
}
