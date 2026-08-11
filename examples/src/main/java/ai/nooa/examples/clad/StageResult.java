package ai.nooa.examples.clad;

import java.util.List;

public record StageResult(String stageId, boolean success, String summary, List<String> producedFiles) {}
