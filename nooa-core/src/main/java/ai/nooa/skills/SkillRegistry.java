package ai.nooa.skills;

import ai.nooa.NooaException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

/** Runtime skill discovery and activation registry. */
public final class SkillRegistry {
    private final Map<String, Skill> available = new LinkedHashMap<>();
    private final Map<String, Skill> active = new LinkedHashMap<>();

    public SkillRegistry register(Skill skill) {
        Objects.requireNonNull(skill, "skill must not be null");
        available.put(skill.name(), skill);
        return this;
    }

    public SkillRegistry registerAll(Iterable<? extends Skill> skills) {
        for (Skill skill : skills) register(skill);
        return this;
    }

    /** Discover skills published through {@link ServiceLoader}. */
    public SkillRegistry discover() {
        return registerAll(ServiceLoader.load(Skill.class));
    }

    public List<String> available() { return List.copyOf(available.keySet()); }
    public List<String> active() { return List.copyOf(active.keySet()); }
    public Skill find(String name) { return available.get(name); }

    public Skill activate(String name) {
        Skill skill = available.get(name);
        if (skill == null) throw new NooaException("Unknown skill: " + name);
        for (String requirement : skill.requires()) {
            if (!active.containsKey(requirement)) {
                throw new NooaException("Skill " + name + " requires " + requirement);
            }
        }
        if (active.putIfAbsent(name, skill) == null) skill.onAttach();
        return skill;
    }

    public void deactivate(String name) {
        Skill skill = active.remove(name);
        if (skill != null) skill.onDetach();
    }

    public void deactivateAll() {
        for (String name : List.copyOf(active.keySet())) deactivate(name);
    }
}