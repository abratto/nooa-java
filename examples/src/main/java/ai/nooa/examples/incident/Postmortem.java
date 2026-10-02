package ai.nooa.examples.incident;

import java.util.List;

/** The post-incident writeup produced once remediation is verified. */
public record Postmortem(String summary, List<String> followUps) {
    public Postmortem {
        followUps = followUps == null ? List.of() : List.copyOf(followUps);
    }
}
