package ai.nooa.clad.runtime.engine;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Predicate Engine")
class PredicateEngineTest {

    private TestContext context;
    private PredicateSyncDispatcher dispatcher;
    private TestConcept concept;

    static class TestContext implements ConceptContext {
        final List<CompletionRecord> completions = new ArrayList<>();
        final List<RefusalRecord> refusals = new ArrayList<>();
        final List<ErrorRecord> errors = new ArrayList<>();

        record CompletionRecord(ActionRecord invocation, Map<String, String> outputs) {}
        record RefusalRecord(ActionRecord invocation, String reason) {}
        record ErrorRecord(ActionRecord invocation, String message) {}

        public void writeCompletion(ActionRecord inv, Map<String, String> outputs) {
            completions.add(new CompletionRecord(inv, outputs));
        }
        public void writeRefusal(ActionRecord inv, String reason) {
            refusals.add(new RefusalRecord(inv, reason));
        }
        public void writeError(ActionRecord inv, String message) {
            errors.add(new ErrorRecord(inv, message));
        }
    }

    static class LookupSync extends SyncAgent {
        public SyncTrigger trigger() {
            return new SyncTrigger("concept:user", "lookupByUsername", "FOUND");
        }
        public ActionRecord thenInvocation(ActionRecord source) {
            return new ActionRecord("inv:check-pw", source.flowToken(),
                "concept:password-auth", "check", Map.of("userId",
                    source.bindings().getOrDefault("userId", "")));
        }
        public String syncName() { return "whenUserFoundThenCheckPassword"; }
    }

    static class TestConcept extends PredicateConceptAgent {
        TestConcept(TestContext ctx, PredicateSyncDispatcher dispatcher) { super(ctx, dispatcher); }
        TestConcept(TestContext ctx) { super(ctx); }
        protected void processInvocation(ActionRecord inv) {
            if ("lookupByUsername".equals(inv.actionName())) {
                writeCompletion(inv, Map.of("outcome", "FOUND", "userId", "u-1"));
            }
        }
    }

    @BeforeEach
    void setUp() {
        context = new TestContext();
        dispatcher = new PredicateSyncDispatcher(List.of(new LookupSync()));
        concept = new TestConcept(context, dispatcher);
    }

    @Test
    @DisplayName("matched outcome writes completion + downstream invocation")
    void matchedOutcome() {
        var inv = new ActionRecord("inv:1", UUID.randomUUID(),
            "concept:user", "lookupByUsername", Map.of("username", "alice"));
        concept.processInvocation(inv);

        assertThat(context.completions).hasSize(2); // user completion + password-auth invocation
        assertThat(context.completions.get(0).invocation().actionName()).isEqualTo("lookupByUsername");
        assertThat(context.completions.get(1).invocation().actionName()).isEqualTo("check");
    }

    @Test
    @DisplayName("unmatched outcome throws SyncEvaluationException")
    void unmatchedOutcome() {
        var inv = new ActionRecord("inv:1", UUID.randomUUID(),
            "concept:user", "lookupByUsername", Map.of("username", "bob"));

        // Override concept to return an unmatched outcome
        var badConcept = new PredicateConceptAgent(context, dispatcher) {
            protected void processInvocation(ActionRecord inv) {
                writeCompletion(inv, Map.of("outcome", "UNKNOWN_STATUS"));
            }
        };

        assertThatThrownBy(() -> badConcept.processInvocation(inv))
            .isInstanceOf(SyncEvaluationException.class)
            .hasMessageContaining("UNKNOWN_STATUS")
            .hasMessageContaining("Add a sync rule");
    }

    @Test
    @DisplayName("wildcard sync (null outcome) matches any outcome")
    void wildcardSync() {
        var wildcardSync = new SyncAgent() {
            public SyncTrigger trigger() {
                return new SyncTrigger("concept:user", "delete", null);
            }
            public ActionRecord thenInvocation(ActionRecord source) {
                return new ActionRecord("inv:log", source.flowToken(),
                    "concept:audit", "logDeletion", Map.of());
            }
            public String syncName() { return "whenUserDeleteThenAudit"; }
        };

        dispatcher = new PredicateSyncDispatcher(List.of(wildcardSync));

        var deletingConcept = new PredicateConceptAgent(context, dispatcher) {
            protected void processInvocation(ActionRecord inv) {
                writeCompletion(inv, Map.of("outcome", "DELETED"));
            }
        };

        var inv = new ActionRecord("inv:1", UUID.randomUUID(),
            "concept:user", "delete", Map.of());
        deletingConcept.processInvocation(inv);

        assertThat(context.completions).hasSize(2);
        assertThat(context.completions.get(1).invocation().actionName()).isEqualTo("logDeletion");
    }

    @Test
    @DisplayName("test mode (null dispatcher) bypasses predicate evaluation")
    void testModeBypasses() {
        var testConcept = new TestConcept(context); // no dispatcher → test mode
        var inv = new ActionRecord("inv:1", UUID.randomUUID(),
            "concept:user", "lookupByUsername", Map.of());

        // Return an outcome that would fail under predicate enforcement
        var bypassing = new PredicateConceptAgent(context) {
            protected void processInvocation(ActionRecord inv) {
                writeCompletion(inv, Map.of("outcome", "CUSTOM_STATUS"));
            }
        };

        assertThatCode(() -> bypassing.processInvocation(inv))
            .doesNotThrowAnyException();
        assertThat(context.completions).hasSize(1);
    }

    @Test
    @DisplayName("Web/respond is always allowed (terminal action)")
    void terminalActionAlwaysAllowed() {
        var inv = new ActionRecord("inv:1", UUID.randomUUID(),
            "Web", "respond", Map.of());

        var webConcept = new PredicateConceptAgent(context, dispatcher) {
            protected void processInvocation(ActionRecord inv) {
                writeCompletion(inv, Map.of("outcome", "200"));
            }
        };

        // Web/respond with "200" has no matching sync but should be allowed
        assertThatCode(() -> webConcept.processInvocation(inv))
            .doesNotThrowAnyException();
        assertThat(context.completions).hasSize(1);
    }

    @Test
    @DisplayName("missing outcome field throws")
    void missingOutcomeThrows() {
        var inv = new ActionRecord("inv:1", UUID.randomUUID(),
            "concept:user", "lookupByUsername", Map.of());

        var badConcept = new PredicateConceptAgent(context, dispatcher) {
            protected void processInvocation(ActionRecord inv) {
                writeCompletion(inv, Map.of()); // no "outcome" field
            }
        };

        assertThatThrownBy(() -> badConcept.processInvocation(inv))
            .isInstanceOf(SyncEvaluationException.class)
            .hasMessageContaining("without an 'outcome'");
    }

    @Test
    @DisplayName("SyncTrigger key uses composite conceptIri::actionName")
    void triggerKeyComposite() {
        var trigger = new SyncTrigger("concept:user", "lookupByUsername", "FOUND");
        String key = PredicateSyncDispatcher.triggerKey(trigger);
        assertThat(key).isEqualTo("concept:user::lookupByUsername");
    }
}
