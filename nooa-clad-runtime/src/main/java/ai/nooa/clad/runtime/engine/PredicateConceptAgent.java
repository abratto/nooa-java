package ai.nooa.clad.runtime.engine;

import java.util.*;

/**
 * Concept agent with predicate enforcement. Before writing a completion,
 * evaluates which syncs match the outcome. If no sync matches and the
 * action is not a terminal action (Web/respond), the completion is rejected
 * with a SyncEvaluationException.
 *
 * <p>This enforces the rule that every outcome must have a known downstream
 * consumer — no silent dead-ends.</p>
 */
public abstract class PredicateConceptAgent extends ConceptAgent {

    private final PredicateSyncDispatcher dispatcher;

    protected PredicateConceptAgent(ConceptContext context, PredicateSyncDispatcher dispatcher) {
        super(context);
        this.dispatcher = dispatcher;
    }

    /** Test mode — no predicate enforcement. */
    protected PredicateConceptAgent(ConceptContext context) {
        super(context);
        this.dispatcher = null;
    }

    /**
     * Write a completion with predicate evaluation.
     * Rejects outcomes that no sync handles.
     */
    protected void writeCompletion(ActionRecord invocation, Map<String, String> outputs) {
        String outcome = outputs.get("outcome");
        if (outcome == null) {
            throw new SyncEvaluationException(
                "writeCompletion called without an 'outcome' field for "
                + invocation.conceptIri() + "/" + invocation.actionName());
        }

        boolean isTerminal = "Web".equals(invocation.conceptIri())
            && "respond".equals(invocation.actionName());

        if (dispatcher != null && !isTerminal) {
            List<SyncAgent> matching = dispatcher.evaluateSyncs(
                invocation.conceptIri(), invocation.actionName(), outcome);

            if (matching.isEmpty()) {
                throw new SyncEvaluationException(
                    "No sync matches outcome '" + outcome
                    + "' for " + invocation.conceptIri()
                    + "/" + invocation.actionName()
                    + ". Add a sync rule to handle this outcome.");
            }

            // Write completion atomically with downstream invocations
            complete(invocation, outputs);
            for (var sync : matching) {
                context().writeCompletion(sync.thenInvocation(invocation), Map.of());
            }
        } else {
            // Test mode or terminal action — just write the completion
            complete(invocation, outputs);
        }
    }
}
