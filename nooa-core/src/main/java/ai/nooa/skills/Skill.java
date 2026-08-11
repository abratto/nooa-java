package ai.nooa.skills;

import java.util.*;

/**
 * Base class for agent-loadable skills. A skill encapsulates capabilities
 * (methods, data) that can be attached to an agent at runtime.
 *
 * <pre>{@code
 * class WebSearchSkill extends Skill {
 *     public WebSearchSkill() { super("web_search"); }
 *     public List<String> search(String query) { ... }
 * }
 *
 * agent.attachSkill(new WebSearchSkill());
 * }</pre>
 */
public abstract class Skill {

    private final String name;
    private final Set<String> requires;

    protected Skill(String name) {
        this(name, Set.of());
    }

    protected Skill(String name, Set<String> requires) {
        this.name = name;
        this.requires = Set.copyOf(requires);
    }

    public String name() { return name; }
    public Set<String> requires() { return requires; }

    /** Called when the skill is attached to an agent. */
    public void onAttach() {}

    /** Called when the skill is detached. */
    public void onDetach() {}

    @Override
    public String toString() {
        return "Skill[" + name + "]";
    }
}
