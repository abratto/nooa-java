package ai.nooa.strategy;

/** Supplies optional Java setup code executed before the first CodeAct turn. */
@FunctionalInterface
public interface Prefill {

    String code(CurrentCall call);
}