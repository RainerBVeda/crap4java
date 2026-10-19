package crap4java;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoverageRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void deletesStaleCoverageAndRunsMavenCoverageCommand() throws Exception {
        Path jacocoDir = tempDir.resolve("target/site/jacoco");
        Files.createDirectories(jacocoDir);
        Files.writeString(jacocoDir.resolve("old.xml"), "stale");
        Path exec = tempDir.resolve("target/jacoco.exec");
        Files.createDirectories(exec.getParent());
        Files.writeString(exec, "stale");

        RecordingExecutor executor = new RecordingExecutor(0, null);
        CoverageRunner runner = new CoverageRunner(executor);

        runner.generateCoverage(tempDir);

        assertFalse(Files.exists(jacocoDir));
        assertFalse(Files.exists(exec));
        assertEquals(List.of(
                "mvn", "-q",
                "org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent",
                "test",
                "org.jacoco:jacoco-maven-plugin:0.8.12:report"
        ), executor.lastCommand());
        assertEquals(tempDir, executor.directories.get(executor.directories.size() - 1));
    }

    @Test
    void skipsOwnAgentWhenProjectBindsPrepareAgent() throws Exception {
        RecordingExecutor executor = new RecordingExecutor(0, effectivePom("0.8.13", "prepare-agent", "report"));

        new CoverageRunner(executor).generateCoverage(tempDir);

        assertEquals(List.of(
                "mvn", "-q",
                "test",
                "org.jacoco:jacoco-maven-plugin:0.8.13:report"
        ), executor.lastCommand());
    }

    @Test
    void addsOwnAgentWhenProjectBindsJacocoWithoutPrepareAgent() throws Exception {
        RecordingExecutor executor = new RecordingExecutor(0, effectivePom("0.8.13", "report"));

        new CoverageRunner(executor).generateCoverage(tempDir);

        assertEquals(List.of(
                "mvn", "-q",
                "org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent",
                "test",
                "org.jacoco:jacoco-maven-plugin:0.8.12:report"
        ), executor.lastCommand());
    }

    @Test
    void addsOwnAgentWhenEffectivePomCannotBeRead() throws Exception {
        RecordingExecutor executor = new RecordingExecutor(0, "<project><not-closed>");

        new CoverageRunner(executor).generateCoverage(tempDir);

        assertEquals("org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent", executor.lastCommand().get(2));
    }

    @Test
    void deletesTheEffectivePomAfterReadingIt() throws Exception {
        RecordingExecutor executor = new RecordingExecutor(0, effectivePom("0.8.13", "prepare-agent"));

        new CoverageRunner(executor).generateCoverage(tempDir);

        assertFalse(Files.exists(executor.effectivePomPath));
    }

    @Test
    void failsWhenCoverageCommandFails() {
        RecordingExecutor executor = new RecordingExecutor(2, null);
        CoverageRunner runner = new CoverageRunner(executor);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> runner.generateCoverage(tempDir));

        assertEquals("Coverage command failed with exit 2", ex.getMessage());
    }

    private static String effectivePom(String jacocoVersion, String... goals) {
        StringBuilder goalXml = new StringBuilder();
        for (String goal : goals) {
            goalXml.append("<goal>").append(goal).append("</goal>");
        }
        return "<project><build><plugins>"
                + "<plugin><artifactId>maven-surefire-plugin</artifactId><version>3.5.4</version>"
                + "<executions><execution><goals><goal>test</goal></goals></execution></executions></plugin>"
                + "<plugin><groupId>org.jacoco</groupId><artifactId>jacoco-maven-plugin</artifactId>"
                + "<version>" + jacocoVersion + "</version>"
                + "<executions><execution><goals>" + goalXml + "</goals></execution></executions></plugin>"
                + "</plugins></build></project>";
    }

    private static final class RecordingExecutor implements CommandExecutor {
        private static final String EFFECTIVE_POM_GOAL = "help:effective-pom";
        private static final String OUTPUT_OPTION = "-Doutput=";

        private final int exitCode;
        private final String effectivePomContent;
        private final List<List<String>> commands = new ArrayList<>();
        private final List<Path> directories = new ArrayList<>();
        private Path effectivePomPath;

        private RecordingExecutor(int exitCode, String effectivePomContent) {
            this.exitCode = exitCode;
            this.effectivePomContent = effectivePomContent;
        }

        @Override
        public int run(List<String> command, Path directory) throws java.io.IOException {
            commands.add(command);
            directories.add(directory);
            if (command.contains(EFFECTIVE_POM_GOAL)) {
                return answerEffectivePom(command);
            }
            return exitCode;
        }

        private int answerEffectivePom(List<String> command) throws java.io.IOException {
            effectivePomPath = Path.of(command.get(command.size() - 1).substring(OUTPUT_OPTION.length()));
            if (effectivePomContent == null) {
                return exitCode;
            }
            Files.writeString(effectivePomPath, effectivePomContent);
            return 0;
        }

        private List<String> lastCommand() {
            return commands.get(commands.size() - 1);
        }
    }
}
