package ai.nooa.eval;

import java.util.Map;

/**
 * User-supplied provider of external post-run state (filesystem, database,
 * remote services) that cannot be read by reflecting over the agent.
 *
 * <p>Values returned here are merged over reflected agent fields, so an
 * explicit snapshot wins on key collisions.</p>
 */
public interface EnvironmentSnapshot {

    /** Capture the environment state relevant to goal verification. */
    Map<String, Object> snapshot();
}
