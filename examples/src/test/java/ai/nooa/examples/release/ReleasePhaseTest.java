package ai.nooa.examples.release;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ReleasePhase (enum FSM)")
class ReleasePhaseTest {

    @Test
    void legalTransitionsAdvance() {
        assertThat(ReleasePhase.PLANNED.advance(ReleaseEvent.START_BUILD))
            .isEqualTo(ReleasePhase.BUILDING);
        assertThat(ReleasePhase.BUILDING.advance(ReleaseEvent.TESTS_PASS))
            .isEqualTo(ReleasePhase.TESTING);
        assertThat(ReleasePhase.TESTING.advance(ReleaseEvent.APPROVE))
            .isEqualTo(ReleasePhase.APPROVED);
        assertThat(ReleasePhase.APPROVED.advance(ReleaseEvent.DEPLOY))
            .isEqualTo(ReleasePhase.DEPLOYED);
        assertThat(ReleasePhase.DEPLOYED.advance(ReleaseEvent.ROLLBACK))
            .isEqualTo(ReleasePhase.ROLLED_BACK);
    }

    @Test
    void failuresAndRejectionsReachRejected() {
        assertThat(ReleasePhase.BUILDING.advance(ReleaseEvent.TESTS_FAIL))
            .isEqualTo(ReleasePhase.REJECTED);
        assertThat(ReleasePhase.TESTING.advance(ReleaseEvent.REJECT))
            .isEqualTo(ReleasePhase.REJECTED);
    }

    @Test
    void terminalPhasesAreIdentified() {
        assertThat(ReleasePhase.DEPLOYED.terminal()).isTrue();
        assertThat(ReleasePhase.REJECTED.terminal()).isTrue();
        assertThat(ReleasePhase.ROLLED_BACK.terminal()).isTrue();
        assertThat(ReleasePhase.PLANNED.terminal()).isFalse();
        assertThat(ReleasePhase.TESTING.terminal()).isFalse();
    }

    @Test
    void illegalTransitionsThrow() {
        assertThatThrownBy(() -> ReleasePhase.PLANNED.advance(ReleaseEvent.DEPLOY))
            .isInstanceOf(IllegalReleaseTransition.class);
        assertThatThrownBy(() -> ReleasePhase.REJECTED.advance(ReleaseEvent.START_BUILD))
            .isInstanceOf(IllegalReleaseTransition.class);
    }
}
