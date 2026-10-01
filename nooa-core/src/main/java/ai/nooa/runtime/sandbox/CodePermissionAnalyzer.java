package ai.nooa.runtime.sandbox;

import ai.nooa.security.PermissionCallback;
import ai.nooa.security.Permissions;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Static gate over a sandbox code cell. Parses the cell with JavaParser and
 * resolves restricted API usage by AST node (imports, qualified type names,
 * object creations, method calls) rather than substring matching, then decides
 * ALLOW / ASK / DENY against the agent's {@link Permissions}.
 *
 * <p>Package-private: an implementation detail of {@link JShellSandbox}.</p>
 */
final class CodePermissionAnalyzer {

    private CodePermissionAnalyzer() {}

    enum Category { FILE, URL, COMMAND, CLASS, ALWAYS, DYNAMIC }

    record Finding(Category category, String value, String detail, Permissions.Level level) {}

    record Decision(boolean allowed, String reason, List<Finding> findings) {
        static Decision allow(List<Finding> findings) {
            return new Decision(true, null, findings);
        }
    }

    private record Restricted(String prefix, Category category) {}

    private static final List<Restricted> FQN_RESTRICTED = List.of(
        new Restricted("java.lang.reflect", Category.CLASS),
        new Restricted("java.lang.invoke", Category.CLASS),
        new Restricted("java.lang.ClassLoader", Category.CLASS),
        new Restricted("java.io.File", Category.FILE),
        new Restricted("java.nio.file", Category.FILE),
        new Restricted("java.net.URL", Category.URL),
        new Restricted("java.net.URI", Category.URL),
        new Restricted("java.lang.ProcessBuilder", Category.COMMAND),
        new Restricted("java.lang.Runtime", Category.ALWAYS),
        new Restricted("java.lang.System", Category.ALWAYS),
        new Restricted("java.net.Socket", Category.ALWAYS),
        new Restricted("java.lang.Thread", Category.ALWAYS),
        new Restricted("sun.", Category.ALWAYS),
        new Restricted("jdk.internal", Category.ALWAYS),
        new Restricted("javax.script", Category.ALWAYS));

    private static final Set<String> FILE_TYPES = Set.of(
        "File", "Files", "Path", "Paths", "FileReader", "FileWriter",
        "RandomAccessFile", "FileInputStream", "FileOutputStream", "FileChannel");
    private static final Set<String> URL_TYPES = Set.of("URL", "URI");
    private static final Set<String> PROCESS_TYPES = Set.of("ProcessBuilder");
    private static final Set<String> REFLECT_TYPES = Set.of(
        "Field", "Method", "Constructor", "MethodHandle", "MethodHandles", "VarHandle",
        "ClassLoader", "Modifier", "Array", "Proxy", "Parameter", "Executable", "AccessibleObject");
    private static final Set<String> REFLECTION_METHODS = Set.of(
        "forName", "getDeclaredField", "getDeclaredMethod", "getDeclaredConstructor",
        "getDeclaredFields", "getDeclaredMethods", "getDeclaredConstructors",
        "getMethod", "getField", "getConstructor", "getMethods", "getFields",
        "getConstructors", "getDeclaredClasses", "loadClass", "defineClass");

    private static final Pattern URL_LITERAL = Pattern.compile("^https?://.+");

    static Decision analyze(String code, Permissions permissions, PermissionCallback callback) {
        if (code == null || code.isBlank()) {
            return Decision.allow(List.of());
        }
        CompilationUnit unit = parse(code);
        List<RawFinding> raw = unit != null ? collect(unit) : legacyScan(code);
        return decide(raw, permissions, callback);
    }

    // ---- parsing ----

    private static CompilationUnit parse(String code) {
        try {
            String wrapped = wrap(code);
            ParseResult<CompilationUnit> result = new JavaParser().parse(wrapped);
            if (result.isSuccessful() && result.getResult().isPresent()) {
                return result.getResult().get();
            }
        } catch (RuntimeException _) {
            // Fall through to legacy scan.
        }
        return null;
    }

    private static String wrap(String code) {
        StringBuilder imports = new StringBuilder();
        StringBuilder body = new StringBuilder();
        for (String line : code.split("\n", -1)) {
            String trimmed = line.strip();
            if (trimmed.startsWith("import ") && trimmed.endsWith(";")) {
                imports.append(trimmed).append('\n');
            } else {
                body.append(line).append('\n');
            }
        }
        return imports
            + "class __NooaCell__ { void __NooaRun__() throws Exception {\n"
            + body
            + "\n} }";
    }

    // ---- collection ----

    private record RawFinding(Category category, String value, String detail) {}

    private static List<RawFinding> collect(CompilationUnit unit) {
        Map<String, RawFinding> findings = new LinkedHashMap<>();

        for (ImportDeclaration imp : unit.findAll(ImportDeclaration.class)) {
            String name = imp.getNameAsString();
            if (imp.isAsterisk()) {
                Restricted restricted = restrictedPackage(name);
                if (restricted != null) {
                    put(findings, restricted.category(), name + ".*",
                        "wildcard import of restricted package " + name);
                }
            } else {
                Category category = restrictedCategory(name);
                if (category == Category.CLASS || category == Category.ALWAYS) {
                    put(findings, category, name, "restricted import");
                }
                if (category == Category.ALWAYS) {
                    // already added
                }
                if (category == null) {
                    // non-restricted import
                }
            }
        }

        for (ClassOrInterfaceType type : unit.findAll(ClassOrInterfaceType.class)) {
            // Qualified names appear as nested scope fragments; only inspect the
            // outermost node (keep generic type arguments, which use the same parent).
            if (type.getParentNode().orElse(null) instanceof ClassOrInterfaceType parent
                    && parent.getScope().map(scope -> scope == type).orElse(false)) {
                continue;
            }
            String name = type.getNameWithScope();
            Category category = restrictedCategory(name);
            if (category == Category.CLASS) {
                put(findings, Category.CLASS, name, "restricted type");
            } else if (category == Category.ALWAYS) {
                put(findings, Category.ALWAYS, name, "always-blocked type");
            }
        }

        for (ObjectCreationExpr creation : unit.findAll(ObjectCreationExpr.class)) {
            String name = creation.getType().getNameWithScope();
            Category category = restrictedCategory(name);
            if (category == null) {
                continue;
            }
            collectSink(findings, category, creation.getArguments(), name);
        }

        for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
            String method = call.getNameAsString();

            if (method.equals("forName") && !call.getArguments().isEmpty()) {
                addReflectionTarget(findings, call.getArgument(0));
                continue;
            }
            if (REFLECTION_METHODS.contains(method)) {
                put(findings, Category.CLASS, scopeName(call),
                    "reflective method " + method);
                continue;
            }
            if (method.equals("exec")) {
                collectSink(findings, Category.COMMAND, call.getArguments(), "Runtime.exec");
                continue;
            }

            String scope = scopeName(call);
            if (FILE_TYPES.contains(scope)) {
                collectSink(findings, Category.FILE, call.getArguments(), scope + "." + method);
            } else if (PROCESS_TYPES.contains(scope)) {
                collectSink(findings, Category.COMMAND, call.getArguments(), scope + "." + method);
            } else if (URL_TYPES.contains(scope)) {
                collectSink(findings, Category.URL, call.getArguments(), scope + "." + method);
            } else if (REFLECT_TYPES.contains(scope)) {
                put(findings, Category.CLASS, scope, "reflective call scope");
            }
        }

        for (StringLiteralExpr literal : unit.findAll(StringLiteralExpr.class)) {
            String value = literal.getValue();
            if (isPathLike(value)) {
                put(findings, Category.FILE, value, "path literal");
            } else if (URL_LITERAL.matcher(value).matches()) {
                put(findings, Category.URL, value, "url literal");
            }
        }

        return new ArrayList<>(findings.values());
    }

    private static void collectSink(Map<String, RawFinding> findings, Category category,
                                    List<Expression> arguments, String sink) {
        Set<String> literals = new LinkedHashSet<>();
        for (Expression argument : arguments) {
            for (StringLiteralExpr literal : argument.findAll(StringLiteralExpr.class)) {
                literals.add(literal.getValue());
            }
        }
        if (literals.isEmpty()) {
            if (!arguments.isEmpty()) {
                put(findings, Category.DYNAMIC, arguments.toString(),
                    "non-literal argument to " + sink);
            }
            return;
        }
        for (String value : literals) {
            put(findings, category, value, "sink " + sink);
        }
    }

    private static void addReflectionTarget(Map<String, RawFinding> findings, Expression argument) {
        if (argument instanceof StringLiteralExpr literal) {
            put(findings, Category.CLASS, literal.getValue(), "Class.forName target");
        } else {
            put(findings, Category.DYNAMIC, argument.toString(), "dynamic Class.forName target");
        }
    }

    private static void put(Map<String, RawFinding> findings, Category category,
                            String value, String detail) {
        findings.putIfAbsent(category + "\u0000" + value, new RawFinding(category, value, detail));
    }

    private static String scopeName(MethodCallExpr call) {
        return call.getScope().map(CodePermissionAnalyzer::simpleName).orElse("");
    }

    private static String simpleName(Expression expression) {
        if (expression instanceof NameExpr name) {
            return name.getNameAsString();
        }
        if (expression instanceof FieldAccessExpr field) {
            return field.getNameAsString();
        }
        if (expression instanceof MethodCallExpr method) {
            return method.getNameAsString();
        }
        return "";
    }

    private static Category restrictedCategory(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (Restricted restricted : FQN_RESTRICTED) {
            if (name.equals(restricted.prefix()) || name.startsWith(restricted.prefix() + ".")
                    || (restricted.prefix().endsWith(".") && name.startsWith(restricted.prefix()))) {
                return restricted.category();
            }
        }
        if (FILE_TYPES.contains(name)) {
            return Category.FILE;
        }
        if (URL_TYPES.contains(name)) {
            return Category.URL;
        }
        if (PROCESS_TYPES.contains(name)) {
            return Category.COMMAND;
        }
        if (REFLECT_TYPES.contains(name)) {
            return Category.CLASS;
        }
        return null;
    }

    private static Restricted restrictedPackage(String pkg) {
        for (Restricted restricted : FQN_RESTRICTED) {
            String prefix = restricted.prefix();
            if (prefix.startsWith(pkg + ".") || prefix.equals(pkg + ".")
                    || prefix.equals(pkg)) {
                return restricted;
            }
        }
        return null;
    }

    private static boolean isPathLike(String value) {
        return value.startsWith("/") || value.startsWith("~/") || value.contains("../");
    }

    // ---- fallback scan when parsing fails ----

    private static List<RawFinding> legacyScan(String code) {
        Map<String, RawFinding> findings = new LinkedHashMap<>();
        for (Restricted restricted : FQN_RESTRICTED) {
            if (code.contains(restricted.prefix())) {
                put(findings, restricted.category(), restricted.prefix(),
                    "legacy contains-scan");
            }
        }
        return new ArrayList<>(findings.values());
    }

    // ---- decision ----

    private static Decision decide(List<RawFinding> raw, Permissions permissions,
                                   PermissionCallback callback) {
        List<Finding> findings = new ArrayList<>();
        boolean deny = false;
        boolean ask = false;
        String reason = null;
        for (RawFinding finding : raw) {
            Permissions.Level level = levelFor(finding.category(), finding.value(), permissions);
            findings.add(new Finding(finding.category(), finding.value(), finding.detail(), level));
            if (level == Permissions.Level.DENY && !deny) {
                deny = true;
                reason = describe(finding);
            } else if (level == Permissions.Level.ASK) {
                ask = true;
                if (reason == null) {
                    reason = describe(finding);
                }
            }
        }
        if (deny) {
            return new Decision(false, "Blocked (" + reason + ")", findings);
        }
        if (ask) {
            if (callback == null) {
                return new Decision(false,
                    "Blocked (approval required but no permission callback is registered: "
                        + reason + ")", findings);
            }
            for (Finding finding : findings) {
                if (finding.level() == Permissions.Level.ASK) {
                    boolean approved = callback.approve(resourceName(finding.category()),
                        finding.value());
                    if (!approved) {
                        return new Decision(false,
                            "Blocked (permission denied by callback: " + finding.value() + ")",
                            findings);
                    }
                }
            }
        }
        return Decision.allow(findings);
    }

    private static Permissions.Level levelFor(Category category, String value,
                                              Permissions permissions) {
        return switch (category) {
            case FILE -> permissions.checkFile(value);
            case URL -> permissions.checkUrl(value);
            case COMMAND -> permissions.checkCommand(value);
            case CLASS -> permissions.checkClassLoad(value);
            case DYNAMIC -> Permissions.Level.ASK;
            case ALWAYS -> Permissions.Level.DENY;
        };
    }

    static String resourceName(Category category) {
        return switch (category) {
            case FILE -> "file";
            case URL -> "url";
            case COMMAND -> "command";
            case CLASS -> "class";
            case DYNAMIC -> "dynamic";
            case ALWAYS -> "class";
        };
    }

    private static String describe(RawFinding finding) {
        return resourceName(finding.category()) + " '" + finding.value() + "' ("
            + finding.detail() + ")";
    }
}
