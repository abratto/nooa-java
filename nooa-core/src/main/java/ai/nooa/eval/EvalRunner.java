package ai.nooa.eval;

import ai.nooa.Agent;
import ai.nooa.AgentFactory;
import ai.nooa.llm.RecordingLLMClient;
import ai.nooa.llm.ReplayLLMClient;
import ai.nooa.llm.UnifiedLLM;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Runs an {@link EvalDataset} against an agent class and produces an
 * {@link EvalReport}. Phase 0 supports {@link Mode#LIVE}; record/replay modes
 * arrive in Phase 1.
 */
public final class EvalRunner implements AutoCloseable {

    private final Class<? extends Agent> agentClass;
    private final UnifiedLLM llm;
    private final Object[] extraArgs;
    private final Mode mode;
    private final int trials;
    private final Rubric rubric;
    private final GoalVerifier goalVerifier;
    private final EnvironmentSnapshot environment;
    private final ModelPricing pricing;
    private final Path recordingPath;
    private final boolean fallThroughOnMiss;

    private EvalRunner(Builder builder) {
        this.agentClass = builder.agentClass;
        this.llm = builder.llm;
        this.extraArgs = builder.extraArgs.clone();
        this.mode = builder.mode;
        this.trials = Math.max(1, builder.trials);
        this.rubric = builder.rubric;
        this.goalVerifier = builder.goalVerifier;
        this.environment = builder.environment;
        this.pricing = builder.pricing;
        this.recordingPath = builder.recordingPath;
        this.fallThroughOnMiss = builder.fallThroughOnMiss;
    }

    public static Builder builder(Class<? extends Agent> agentClass, UnifiedLLM llm) {
        return new Builder(agentClass, llm);
    }

    public EvalReport run(EvalDataset dataset) {
        UnifiedLLM effectiveLlm = llm;
        if (mode == Mode.RECORD) {
            effectiveLlm = new RecordingLLMClient(llm, recordingPath);
        } else if (mode == Mode.REPLAY) {
            ReplayLLMClient replay = new ReplayLLMClient(recordingPath);
            if (fallThroughOnMiss) {
                replay.fallThroughOnMiss(llm);
            }
            effectiveLlm = replay;
        }

        List<EvalReport.CaseReport> caseReports = new ArrayList<>();
        List<CompletionMetrics> completionPerCase = new ArrayList<>();
        Map<String, Double> scorerSums = new LinkedHashMap<>();
        Map<String, Integer> scorerCounts = new LinkedHashMap<>();
        double weightedSum = 0;

        for (EvalCase evalCase : dataset.cases()) {
            List<RunTrace> traces = new ArrayList<>();
            List<GoalVerifier.Completion> goals = new ArrayList<>();
            List<Boolean> trialSuccess = new ArrayList<>();
            int caseSteps = 0;

            for (int trial = 0; trial < trials; trial++) {
                Agent agent = AgentFactory.create(agentClass, effectiveLlm, extraArgs);
                RunRecorder recorder = RunRecorder.attach(agent, pricing);
                long start = System.nanoTime();
                Object output = null;
                boolean present = false;
                String failure = null;
                try {
                    output = unwrap(invoke(agent, evalCase));
                    present = true;
                } catch (Throwable e) {
                    failure = e.getClass().getSimpleName() + ": " + e.getMessage();
                }
                long durationMs = (System.nanoTime() - start) / 1_000_000;
                recorder.output(output, present).durationMs(durationMs);
                if (failure != null) {
                    recorder.terminatedCleanly(false);
                }
                PostState postState = new ReflectivePostState(agent, environment);
                RunTrace trace = recorder.trace(evalCase.id(), trial)
                    .withSignal("postState", postState.fields());
                if (failure != null) {
                    trace = trace.withSignal("failure", failure);
                }
                GoalVerifier.Completion goal = goalVerifier.verify(evalCase, trace, postState);

                traces.add(trace);
                goals.add(goal);
                trialSuccess.add(goal.achieved() && present);
                caseSteps = Math.max(caseSteps, trace.stepCount());
                closeQuietly(agent);
            }

            List<Score> scores = scoreAcrossTrials(evalCase, traces);
            double weighted = rubric.aggregate(scores);
            weightedSum += weighted;
            for (Score score : scores) {
                if (!score.applicable()) {
                    continue;
                }
                scorerSums.merge(score.scorer(), score.value(), Double::sum);
                scorerCounts.merge(score.scorer(), 1, Integer::sum);
            }
            CompletionMetrics completion = CompletionMetrics.fromTrials(trialSuccess);
            completionPerCase.add(completion);

            caseReports.add(new EvalReport.CaseReport(
                evalCase.id(),
                evalCase.tags(),
                scores,
                weighted,
                goals.isEmpty() ? GoalVerifier.Completion.unscored() : goals.get(0),
                completion,
                trialSuccess,
                traces.isEmpty() || traces.get(0).output() == null
                    ? null : String.valueOf(traces.get(0).output()),
                caseSteps));
        }

        Map<String, Double> scorerMeans = new LinkedHashMap<>();
        scorerSums.forEach((name, sum) ->
            scorerMeans.put(name, sum / scorerCounts.get(name)));
        double weightedMean = caseReports.isEmpty() ? 0 : weightedSum / caseReports.size();

        EvalReport.Aggregate aggregate = new EvalReport.Aggregate(
            scorerMeans, weightedMean, CompletionMetrics.aggregate(completionPerCase));

        return new EvalReport(
            Instant.now().toString(),
            agentClass.getName(),
            dataset.id(),
            mode.name(),
            trials,
            caseReports,
            aggregate);
    }

    /** Score each trace with every rubric scorer, then average across trials. */
    private List<Score> scoreAcrossTrials(EvalCase evalCase, List<RunTrace> traces) {
        List<Score> averaged = new ArrayList<>();
        for (Rubric.WeightedScorer weighted : rubric.scorers()) {
            Scorer scorer = weighted.scorer();
            double sum = 0;
            boolean allPassed = true;
            String detail = "no trials";
            for (RunTrace trace : traces) {
                Score score = scorer.score(evalCase, trace);
                sum += score.value();
                allPassed &= score.passed();
                detail = score.detail();
            }
            double mean = traces.isEmpty() ? 0 : sum / traces.size();
            averaged.add(Score.of(scorer.name(), mean, allPassed, detail));
        }
        return averaged;
    }

    private Object invoke(Agent agent, EvalCase evalCase) throws Exception {
        Method method = resolve(agent.getClass(), evalCase.targetMethod(),
            evalCase.input().size());
        if (method == null) {
            throw new NoSuchMethodException("No method '" + evalCase.targetMethod()
                + "' with " + evalCase.input().size() + " parameters on "
                + agent.getClass().getName());
        }
        Object[] args = evalCase.input().values().toArray();
        method.setAccessible(true);
        try {
            return method.invoke(agent, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new RuntimeException(cause);
        }
    }

    private static Method resolve(Class<?> cls, String name, int arity) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == arity) {
                    return m;
                }
            }
        }
        for (Method m : cls.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == arity) {
                return m;
            }
        }
        return null;
    }

    private static Object unwrap(Object result) {
        if (result instanceof CompletableFuture<?> future) {
            try {
                return future.join();
            } catch (CompletionException e) {
                Throwable cause = e.getCause();
                throw cause instanceof RuntimeException runtime ? runtime
                    : new RuntimeException(cause);
            }
        }
        return result;
    }

    private static void closeQuietly(Agent agent) {
        try {
            agent.close();
        } catch (Exception _) {
            // Best-effort cleanup between trials.
        }
    }

    @Override
    public void close() {
        // Runner holds no resources in Phase 0; retained for symmetry.
    }

    /** Fluent builder for {@link EvalRunner}. */
    public static final class Builder {
        private final Class<? extends Agent> agentClass;
        private final UnifiedLLM llm;
        private Object[] extraArgs = new Object[0];
        private Mode mode = Mode.LIVE;
        private int trials = 1;
        private Rubric rubric = Rubric.defaults();
        private GoalVerifier goalVerifier = new DefaultGoalVerifier();
        private EnvironmentSnapshot environment;
        private ModelPricing pricing = ModelPricing.fromEnv();
        private Path recordingPath = Path.of("nooa-eval-recording.jsonl");
        private boolean fallThroughOnMiss;

        private Builder(Class<? extends Agent> agentClass, UnifiedLLM llm) {
            this.agentClass = java.util.Objects.requireNonNull(agentClass, "agentClass");
            this.llm = java.util.Objects.requireNonNull(llm, "llm");
        }

        public Builder extraArgs(Object... args) {
            this.extraArgs = args == null ? new Object[0] : args.clone();
            return this;
        }

        public Builder mode(Mode value) {
            this.mode = value;
            return this;
        }

        public Builder trials(int count) {
            this.trials = count;
            return this;
        }

        public Builder rubric(Rubric value) {
            this.rubric = value;
            return this;
        }

        public Builder goalVerifier(GoalVerifier value) {
            this.goalVerifier = value;
            return this;
        }

        public Builder environment(EnvironmentSnapshot value) {
            this.environment = value;
            return this;
        }

        public Builder pricing(ModelPricing value) {
            this.pricing = value;
            return this;
        }

        /** Recording file for {@link Mode#RECORD} and {@link Mode#REPLAY}. */
        public Builder recordingPath(Path value) {
            this.recordingPath = value;
            return this;
        }

        /** On replay miss, fall through to the live client instead of failing. */
        public Builder fallThroughOnMiss(boolean value) {
            this.fallThroughOnMiss = value;
            return this;
        }

        public EvalRunner build() {
            return new EvalRunner(this);
        }
    }
}
