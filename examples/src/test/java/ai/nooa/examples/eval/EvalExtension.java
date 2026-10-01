package ai.nooa.examples.eval;

import ai.nooa.eval.EvalAssertions;
import ai.nooa.eval.EvalDataset;
import ai.nooa.eval.EvalReport;
import ai.nooa.eval.EvalRunner;
import ai.nooa.eval.cli.LlmFactory;
import ai.nooa.llm.UnifiedLLM;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.nio.file.Path;

/**
 * Reference JUnit 5 extension for running an {@link Eval} on a test method and
 * resolving the resulting {@link EvalReport} as a parameter.
 *
 * <p>The model comes from {@link EvalExtension#useLlm(UnifiedLLM)} when set
 * (useful with a scripted client), otherwise from environment configuration via
 * {@link LlmFactory#fromEnv()}.</p>
 */
public final class EvalExtension implements ParameterResolver {

    private static UnifiedLLM llmOverride;

    /** Override the model used by subsequently resolved {@link Eval} methods. */
    public static void useLlm(UnifiedLLM llm) {
        llmOverride = llm;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
                                     ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType().equals(EvalReport.class)
            && extensionContext.getTestMethod()
                .map(method -> method.isAnnotationPresent(Eval.class))
                .orElse(false);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext) {
        Eval annotation = extensionContext.getTestMethod()
            .orElseThrow()
            .getAnnotation(Eval.class);
        UnifiedLLM llm = llmOverride != null ? llmOverride : LlmFactory.fromEnv();
        EvalDataset dataset = EvalDataset.load(Path.of(annotation.dataset()));
        EvalReport report = EvalRunner.builder(annotation.agent(), llm)
            .trials(annotation.trials())
            .mode(annotation.mode())
            .build()
            .run(dataset);

        if (annotation.minWeighted() > 0) {
            EvalAssertions.assertPass(report, annotation.minWeighted());
        }
        if (!annotation.gate().isBlank()) {
            EvalAssertions.assertGate(report, annotation.gate(), annotation.gateMin());
        }
        return report;
    }
}
