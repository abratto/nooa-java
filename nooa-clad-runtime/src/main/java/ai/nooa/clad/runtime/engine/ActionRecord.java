package ai.nooa.clad.runtime.engine;

import java.util.Map;
import java.util.UUID;

/**
 * Immutable descriptor of a pending concept invocation.
 * Generated CLAD code receives these from the dispatcher loop.
 */
public record ActionRecord(
    String actionIri,
    UUID flowToken,
    String conceptIri,
    String actionName,
    Map<String, String> bindings
) {}
