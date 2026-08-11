package ai.nooa.clad;

import java.io.*;
import java.nio.file.*;
import java.util.List;

/**
 * Scaffolds a new CLAD project from templates bundled in the jar
 * and from the clad/ submodule's methodology and feature skeleton.
 */
final class ProjectScaffolder {

    private static final List<String> STAGE_DIRS = List.of(
        "stages/00_actor-goal",
        "stages/01_usecase",
        "stages/02a_responsibility-map",
        "stages/02b_chain-table",
        "stages/02_concepts",
        "stages/03_syncs",
        "stages/03a_dependency-review",
        "stages/03b_data-model",
        "stages/04_implement/04a_storage-mapping",
        "stages/04_implement/04b_spec",
        "stages/04_implement/04c_flow-tests",
        "stages/04_implement/04d_concept-tdd/04d_red-tests",
        "stages/04_implement/04d_concept-tdd/04d_green-impl",
        "stages/04_implement/04e_sync-tdd/04e_red-tests",
        "stages/04_implement/04e_sync-tdd/04e_green-impl",
        "stages/05_verify"
    );

    void scaffold(Path projectDir, String projectName) throws IOException {
        // 1. Create directory structure
        Files.createDirectories(projectDir);

        // 2. Find CLAD methodology source (submodule)
        Path cladSource = findCladSource();

        // 3. Copy methodology
        if (cladSource != null) {
            copyDir(cladSource.resolve("methodology"), projectDir.resolve("methodology"));
            // Copy feature skeleton template for _system
            copyDir(cladSource.resolve("templates/feature-skeleton"),
                projectDir.resolve("features/_system"));
        }

        // 4. Create features directories
        Files.createDirectories(projectDir.resolve("features/_system/stages"));
        System.out.println("  Created features/_system/");

        // 5. Write pom.xml
        writePomXml(projectDir, projectName);

        // 6. Write README
        writeReadme(projectDir, projectName);

        // 7. Write CLAUDE.md starter
        Files.writeString(projectDir.resolve("CLAUDE.md"), """
            # %s

            CLAD project: Contract-Led, Artefact-Driven Development.

            Run stages with: nooa clad run
            """.formatted(projectName));

        System.out.println("  Done.");
    }

    private Path findCladSource() {
        // Look for clad/ submodule relative to the jar or current directory
        Path candidate = Path.of("clad");
        if (Files.exists(candidate.resolve("methodology"))) return candidate.toAbsolutePath();

        // Try relative to user.dir
        candidate = Path.of(System.getProperty("user.dir")).resolve("clad");
        if (Files.exists(candidate.resolve("methodology"))) return candidate;

        // Try relative to the classpath
        return null;
    }

    private void copyDir(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            System.out.println("  (skipped: " + source.getFileName() + " not found)");
            return;
        }
        try (var files = Files.walk(source)) {
            files.forEach(src -> {
                try {
                    Path dest = target.resolve(source.relativize(src));
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.createDirectories(dest.getParent());
                        Files.copy(src, dest);
                    }
                } catch (IOException ignored) {}
            });
        }
        System.out.println("  Copied " + source.getFileName());
    }

    private void writePomXml(Path projectDir, String projectName) throws IOException {
        String pom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                     http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>%s</artifactId>
                <version>1.0-SNAPSHOT</version>

                <properties>
                    <maven.compiler.release>21</maven.compiler.release>
                    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                </properties>

                <dependencies>
                    <dependency>
                        <groupId>ai.nooa</groupId>
                        <artifactId>nooa-clad-runtime</artifactId>
                        <version>0.1.0</version>
                    </dependency>
                    <dependency>
                        <groupId>org.apache.jena</groupId>
                        <artifactId>jena-core</artifactId>
                        <version>5.1.0</version>
                    </dependency>
                    <dependency>
                        <groupId>org.apache.jena</groupId>
                        <artifactId>jena-arq</artifactId>
                        <version>5.1.0</version>
                    </dependency>
                    <dependency>
                        <groupId>org.junit.jupiter</groupId>
                        <artifactId>junit-jupiter</artifactId>
                        <version>5.11.3</version>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
            </project>
            """.formatted(projectName);
        Files.writeString(projectDir.resolve("pom.xml"), pom);
    }

    private void writeReadme(Path projectDir, String projectName) throws IOException {
        Files.writeString(projectDir.resolve("README.md"), """
            # %s

            CLAD project bootstrapped with NOOA CLAD agent.

            ## Getting Started

            ```bash
            # Run through all CLAD stages
            nooa clad run

            # Run a specific stage
            nooa clad run --stage 01_usecase

            # Auto-advance non-gate stages
            nooa clad run --auto
            ```

            ## Project Structure

            - `methodology/` — CLAD methodology docs (from bundled submodule)
            - `features/_system/` — system-level stages (00_actor-goal)
            - `features/UC-XX-<slug>/` — per-feature stages

            ## After Completing Stages

            ```bash
            # Generate reference implementation code
            nooa clad run --stage 04_implement

            # Run all tests
            mvn test
            ```

            ## See Also

            - [CLAD Methodology](methodology/core/CLAD.md)
            - [NOOA CLAD Agent](https://github.com/abratto/nooa-java)
            """.formatted(projectName));
    }
}
