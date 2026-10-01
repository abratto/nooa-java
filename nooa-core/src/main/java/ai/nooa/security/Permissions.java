package ai.nooa.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Fine-grained permissions for agent resource access.
 * By default, everything is DENIED. The developer adds ALLOW rules
 * for resources the agent should access, and ASK rules for things
 * that need user approval at runtime.
 *
 * <pre>{@code
 * var perms = new Permissions()
 *     .file("src/**", Permission.ALLOW)           // agent can read project source
 *     .file("/etc/**", Permission.DENY)            // never touch system config
 *     .command("git *", Permission.ALLOW)          // git commands allowed
 *     .command("rm *", Permission.ASK)             // destructive — ask user
 *     .url("https://api.example.com/**", Permission.ALLOW)
 *     .url("*", Permission.DENY);                  // block all other URLs
 *
 * agent.setPermissions(perms);
 * }</pre>
 */
public final class Permissions {

    public enum Level { DENY, ASK, ALLOW }

    private final List<Rule> fileRules = new ArrayList<>();
    private final List<Rule> commandRules = new ArrayList<>();
    private final List<Rule> urlRules = new ArrayList<>();
    private final List<Rule> classRules = new ArrayList<>();

    /** Default: deny everything. */
    public Permissions() {
        // Implicit default DENY for all resources
    }

    /** Add a file glob permission rule. */
    public Permissions file(String glob, Level level) {
        fileRules.add(new Rule(fileGlobToRegex(glob), level, glob));
        return this;
    }

    /** Add a shell command glob permission rule. */
    public Permissions command(String glob, Level level) {
        commandRules.add(new Rule(globToRegex(glob), level, glob));
        return this;
    }

    /** Add a URL glob permission rule. */
    public Permissions url(String glob, Level level) {
        urlRules.add(new Rule(globToRegex(glob), level, glob));
        return this;
    }

    /** Add a class loading permission rule. */
    public Permissions classLoad(String glob, Level level) {
        classRules.add(new Rule(globToRegex(glob), level, glob));
        return this;
    }

    // ---- Check methods ----

    /** Check permission for a file path, resolving symlinks when the path exists. */
    public Level checkFile(Path path) {
        return check(fileRules, canonical(path).toString());
    }

    /** Check permission for a file path string, resolving symlinks when it exists. */
    public Level checkFile(String path) {
        return checkFile(Path.of(path));
    }

    /**
     * Resolve a path to its canonical form so a symlink inside an allowed root
     * cannot be used to escape it. Falls back to absolute normalization when
     * the path does not exist or cannot be resolved.
     */
    private static Path canonical(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        try {
            if (Files.exists(normalized)) {
                return normalized.toRealPath();
            }
        } catch (IOException | RuntimeException _) {
            // Fall back to the normalized path.
        }
        return normalized;
    }

    /** Check permission for a shell command. */
    public Level checkCommand(String command) {
        return check(commandRules, command.strip());
    }

    /** Check permission for a URL. */
    public Level checkUrl(String url) {
        return check(urlRules, url);
    }

    /** Check permission to load a class. */
    public Level checkClassLoad(String className) {
        return check(classRules, className);
    }

    private Level check(List<Rule> rules, String target) {
        // Most-specific matching rule wins; ties break in favor of the rule
        // added last, so narrow rules override broad ones regardless of order.
        Level result = Level.DENY;
        int bestSpecificity = -1;
        for (Rule rule : rules) {
            if (rule.pattern().matcher(target).matches()) {
                int specificity = specificity(rule.glob());
                if (specificity >= bestSpecificity) {
                    bestSpecificity = specificity;
                    result = rule.level();
                }
            }
        }
        return result;
    }

    /** Number of literal (non-wildcard) glob characters; more characters = more specific. */
    private static int specificity(String glob) {
        int count = 0;
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c != '*' && c != '?') {
                count++;
            }
        }
        return count;
    }

    /** Convert a glob to regex. For commands, * matches anything (including spaces/slashes). */
    static Pattern globToRegex(String glob) {
        return Pattern.compile(globToRegexString(glob, true), Pattern.CASE_INSENSITIVE);
    }

    /** Convert a file-path glob to regex. * matches within segment, ** across directories. */
    static Pattern fileGlobToRegex(String glob) {
        return Pattern.compile(globToRegexString(glob, false), Pattern.CASE_INSENSITIVE);
    }

    private static String globToRegexString(String glob, boolean matchAll) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> {
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        sb.append(".*");  // ** matches everything
                        i++;
                    } else if (matchAll) {
                        sb.append(".*");  // * matches anything for commands/URLs
                    } else {
                        sb.append("[^/]*"); // * matches within path segment for files
                    }
                }
                case '?' -> sb.append('.');
                default -> {
                    if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
                        sb.append('\\').append(c);
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /** Convenience: allow everything (dangerous — use only for trusted agents). */
    public static Permissions allowAll() {
        return new Permissions()
            .file("**", Level.ALLOW)
            .command("*", Level.ALLOW)
            .url("*", Level.ALLOW)
            .classLoad("*", Level.ALLOW);
    }

    record Rule(Pattern pattern, Level level, String glob) {}

    @Override
    public String toString() {
        return "Permissions[files=" + fileRules.size()
            + ", commands=" + commandRules.size()
            + ", urls=" + urlRules.size()
            + ", classes=" + classRules.size() + "]";
    }
}
