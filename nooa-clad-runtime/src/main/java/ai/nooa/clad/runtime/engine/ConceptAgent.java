package ai.nooa.clad.runtime.engine;

import java.util.Map;

/**
 * Base class for concept agents in a CLAD system.
 *
 * <p>Subclasses implement {@link #processInvocation(ActionRecord)} to handle
 * pending invocations addressed to this concept. The runtime framework
 * (SyncDispatcher + ActionLog) feeds invocations and processes completions.</p>
 *
 * <p>Hard rule R1: a concept agent does not import or call any sibling concept's
 * code. State lives in this concept's named graph; coordination happens only
 * through the action log.</p>
 *
 * <p>Subclasses use {@link #complete(ActionRecord, Map)} to write output bindings
 * and signal completion.</p>
 *
 * <pre>{@code
 * class UserConcept extends ConceptAgent {
 *     protected void processInvocation(ActionRecord inv) {
 *         if ("lookupByUsername".equals(inv.actionName())) {
 *             complete(inv, Map.of("status", "FOUND", "userId", "..."));
 *         }
 *     }
 * }
 * }</pre>
 */
public abstract class ConceptAgent {

    private final ConceptContext context;

    protected ConceptAgent(ConceptContext context) {
        this.context = context;
    }

    protected ConceptContext context() { return context; }

    /** Handle an invocation addressed to this concept. */
    protected abstract void processInvocation(ActionRecord invocation);

    /** Write output bindings and signal completion. */
    protected void complete(ActionRecord invocation, Map<String, String> outputs) {
        context.writeCompletion(invocation, outputs);
    }

    /** Signal refusal (the concept is not responsible for this invocation). */
    protected void refuse(ActionRecord invocation, String reason) {
        context.writeRefusal(invocation, reason);
    }

    /** Signal an error processing the invocation. */
    protected void error(ActionRecord invocation, String message) {
        context.writeError(invocation, message);
    }
}
