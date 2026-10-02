package ai.nooa.examples.incident;

/**
 * Human-in-the-loop approval for a remediation plan. The agent calls this
 * before applying a plan; the application decides how approval is obtained
 * (console prompt, ticketing system, on-call pager).
 */
@FunctionalInterface
public interface ApprovalGate {

    /** @return {@code true} to approve the plan and proceed to mitigation. */
    boolean approve(RemediationPlan plan);

    /** Reviewer identity recorded on the approved state. */
    default String reviewer() {
        return "operator";
    }
}
