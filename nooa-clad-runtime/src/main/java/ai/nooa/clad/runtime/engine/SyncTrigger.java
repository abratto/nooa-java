package ai.nooa.clad.runtime.engine;

/**
 * Trigger condition for a sync: which concept's action with what outcome
 * activates this sync.
 */
public record SyncTrigger(String conceptIri, String actionName, String outcome) {}
