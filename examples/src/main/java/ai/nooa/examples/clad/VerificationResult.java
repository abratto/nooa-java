package ai.nooa.examples.clad;

import java.util.List;

public record VerificationResult(boolean passed, List<String> checks, List<String> failures) {
    public static VerificationResult pass(List<String> checks) {
        return new VerificationResult(true, checks, List.of());
    }
    public static VerificationResult fail(List<String> checks, List<String> failures) {
        return new VerificationResult(false, checks, failures);
    }
}
