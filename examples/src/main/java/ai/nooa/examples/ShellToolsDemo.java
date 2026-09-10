package ai.nooa.examples;

import ai.nooa.tools.ShellTools;

import java.nio.file.Path;

/** Runs a small trusted-environment shell command through {@code ShellTools}. */
public final class ShellToolsDemo {
    private ShellToolsDemo() {}

    public static void main(String[] args) {
        try (var shell = new ShellTools(Path.of(System.getProperty("user.home")))) {
            var result = shell.run("echo 'hello from shell' && ls ~ | head -3");
            System.out.println(result.stdout());
        }
    }
}