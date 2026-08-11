package ai.nooa.examples.clad;

import java.util.List;

public record AutoVerifyResult(boolean passed, List<String> issues) {}
