package ai.nooa.strategy;

import org.junit.jupiter.api.*;

import java.util.function.BiPredicate;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MethodConditions")
class MethodConditionsTest {

    @Test
    @DisplayName("precondition passes when check succeeds")
    void preconditionPasses() {
        BiPredicate<Object, Object> check = (a, args) -> ((Object[]) args).length > 0;
        var cond = new MethodConditions().precondition("Args not empty", check);
        assertThatCode(() -> cond.checkPreconditions(this, new Object[]{"test"}))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("precondition fails with PreconditionError")
    void preconditionFails() {
        BiPredicate<Object, Object> check = (a, args) -> ((Object[]) args).length > 0;
        var cond = new MethodConditions().precondition("Args not empty", check);
        assertThatThrownBy(() -> cond.checkPreconditions(this, new Object[]{}))
            .isInstanceOf(MethodConditions.PreconditionError.class)
            .hasMessageContaining("Args not empty");
    }

    @Test
    @DisplayName("postcondition passes when result valid")
    void postconditionPasses() {
        BiPredicate<Object, Object> check = (a, result) -> result != null;
        var cond = new MethodConditions().postcondition("Result non-null", check);
        assertThatCode(() -> cond.checkPostconditions(this, "valid"))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("postcondition fails with InvariantError for retry")
    void postconditionFails() {
        BiPredicate<Object, Object> check = (a, result) -> result != null;
        var cond = new MethodConditions().postcondition("Result non-null", check);
        assertThatThrownBy(() -> cond.checkPostconditions(this, null))
            .isInstanceOf(MethodConditions.InvariantError.class)
            .hasMessageContaining("Result non-null");
    }

    @Test
    @DisplayName("multiple preconditions all evaluated")
    void multiplePreconditions() {
        var cond = new MethodConditions()
            .precondition("check 1", (a, args) -> ((Object[]) args).length >= 1)
            .precondition("check 2", (a, args) -> ((Object[]) args)[0] != null);

        assertThatCode(() -> cond.checkPreconditions(this, new Object[]{"ok"}))
            .doesNotThrowAnyException();
    }
}
