package ai.nooa.security;

/**
 * Callback invoked when the agent requests a resource that needs approval.
 * Implement this to integrate with your application's permission UI.
 *
 * <pre>{@code
 * // Console-based approval:
 * agent.setPermissionCallback((resource, detail) -> {
 *     System.out.println("Agent wants to " + resource + ": " + detail);
 *     System.out.print("Allow? [y/N] ");
 *     return System.console().readLine().trim().equalsIgnoreCase("y");
 * });
 *
 * // Always-deny dangerous operations:
 * agent.setPermissionCallback((resource, detail) -> {
 *     if (resource.equals("command") && detail.contains("rm -rf")) return false;
 *     return true;
 * });
 * }</pre>
 */
@FunctionalInterface
public interface PermissionCallback {

    /**
     * @param resource "file", "command", "url", or "class"
     * @param detail   the path, command, URL, or class name being accessed
     * @return true to allow, false to deny
     */
    boolean approve(String resource, String detail);
}
