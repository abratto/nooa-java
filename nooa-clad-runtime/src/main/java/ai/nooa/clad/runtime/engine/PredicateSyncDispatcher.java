package ai.nooa.clad.runtime.engine;

import java.util.*;

/**
 * Passive evaluator for the predicate engine. Answers "which syncs match
 * this outcome?" — no scheduling loop, no polling.
 *
 * <p>Build the index once at startup, then call {@link #evaluateSyncs}
 * for each completion.</p>
 */
public class PredicateSyncDispatcher {

    private final Map<String, List<SyncAgent>> triggerIndex = new HashMap<>();

    public PredicateSyncDispatcher(List<SyncAgent> syncs) {
        for (var sync : syncs) {
            String key = triggerKey(sync.trigger());
            triggerIndex.computeIfAbsent(key, k -> new ArrayList<>()).add(sync);
        }
    }

    /**
     * Find all syncs that match a completion. Called by PredicateConceptAgent
     * before writing the completion.
     *
     * @param conceptIri the concept producing the outcome
     * @param actionName the action that completed
     * @param outcome    the outcome value (e.g. "FOUND", "Ok", "NOT_FOUND")
     * @return matching syncs, may be empty
     */
    public List<SyncAgent> evaluateSyncs(String conceptIri, String actionName, String outcome) {
        String key = conceptIri + "::" + actionName;
        List<SyncAgent> candidates = triggerIndex.getOrDefault(key, List.of());

        List<SyncAgent> matched = new ArrayList<>();
        for (var sync : candidates) {
            SyncTrigger trigger = sync.trigger();
            // null outcome means "any outcome" (wildcard sync)
            if (trigger.outcome() == null || trigger.outcome().equals(outcome)) {
                matched.add(sync);
            }
        }
        return matched;
    }

    static String triggerKey(SyncTrigger trigger) {
        return trigger.conceptIri() + "::" + trigger.actionName();
    }
}
