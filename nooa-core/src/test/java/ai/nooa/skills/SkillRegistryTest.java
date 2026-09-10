package ai.nooa.skills;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import java.util.ServiceLoader;

class SkillRegistryTest {
    @Test
    void activatesAndDeactivatesRegisteredSkill() {
        var calls = new int[2];
        var skill = new Skill("demo") {
            @Override public void onAttach() { calls[0]++; }
            @Override public void onDetach() { calls[1]++; }
        };
        var registry = new SkillRegistry().register(skill);
        registry.activate("demo");
        registry.deactivate("demo");
        assertThat(calls).containsExactly(1, 1);
        assertThat(registry.active()).isEmpty();
    }

    @Test
    void discoversSkillsFromServiceLoader() {
        var registry = new SkillRegistry();
        registry.registerAll(ServiceLoader.load(Skill.class));
        assertThat(registry.available()).isNotNull();
    }
}