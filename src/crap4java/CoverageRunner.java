package crap4java;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class CoverageRunner {

    private static final String JACOCO_PLUGIN = "jacoco-maven-plugin";
    private static final String JACOCO_GROUP = "org.jacoco";
    private static final String FALLBACK_JACOCO_VERSION = "0.8.12";

    private final CommandExecutor executor;

    CoverageRunner(CommandExecutor executor) {
        this.executor = executor;
    }

    void generateCoverage(Path projectRoot) throws Exception {
        deleteIfExists(projectRoot.resolve("target/site/jacoco"));
        deleteIfExists(projectRoot.resolve("target/jacoco.exec"));

        int exit = executor.run(coverageCommand(projectRoot), projectRoot);
        if (exit != 0) {
            throw new IllegalStateException("Coverage command failed with exit " + exit);
        }
    }

    /**
     * A project that already binds jacoco's prepare-agent to its own lifecycle gets a second agent
     * if we add ours, and two agents crash the forked test JVM (LinkageError on java.lang.$JaCoCo).
     * In that case we only run the tests and let the project's agent record; the report is taken
     * with the project's own jacoco version.
     */
    private List<String> coverageCommand(Path projectRoot) throws Exception {
        Optional<String> projectVersion = projectJacocoVersionWithAgent(projectRoot);
        if (projectVersion.isPresent()) {
            return List.of("mvn", "-q", "test", reportGoal(projectVersion.get()));
        }
        return List.of("mvn", "-q",
                goal(FALLBACK_JACOCO_VERSION, "prepare-agent"),
                "test",
                reportGoal(FALLBACK_JACOCO_VERSION));
    }

    private static String reportGoal(String version) {
        return goal(version, "report");
    }

    private static String goal(String version, String goal) {
        return JACOCO_GROUP + ":" + JACOCO_PLUGIN + ":" + version + ":" + goal;
    }

    /** Empty when the effective pom cannot be read or does not bind prepare-agent with a version. */
    private Optional<String> projectJacocoVersionWithAgent(Path projectRoot) throws Exception {
        Path effectivePom = Files.createTempFile("crap4java-effective-pom", ".xml");
        try {
            int exit = executor.run(List.of("mvn", "-q", "help:effective-pom",
                    "-Doutput=" + effectivePom), projectRoot);
            if (exit != 0 || Files.size(effectivePom) == 0) {
                return Optional.empty();
            }
            return jacocoVersionWithAgent(effectivePom);
        } finally {
            Files.deleteIfExists(effectivePom);
        }
    }

    private static Optional<String> jacocoVersionWithAgent(Path effectivePom) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            Document document = builder.parse(effectivePom.toFile());
            NodeList plugins = document.getElementsByTagName("plugin");
            for (int i = 0; i < plugins.getLength(); i++) {
                Element plugin = (Element) plugins.item(i);
                if (isJacocoWithAgent(plugin)) {
                    return Optional.ofNullable(childText(plugin, "version"));
                }
            }
            return Optional.empty();
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static boolean isJacocoWithAgent(Element plugin) {
        if (!JACOCO_PLUGIN.equals(childText(plugin, "artifactId"))) {
            return false;
        }
        NodeList goals = plugin.getElementsByTagName("goal");
        for (int i = 0; i < goals.getLength(); i++) {
            if ("prepare-agent".equals(goals.item(i).getTextContent().trim())) {
                return true;
            }
        }
        return false;
    }

    private static String childText(Element parent, String tag) {
        for (var node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element child && tag.equals(child.getTagName())) {
                return child.getTextContent().trim();
            }
        }
        return null;
    }

    private void deleteIfExists(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            try (var walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ex) {
                                throw new IllegalStateException("Failed deleting stale coverage: " + p, ex);
                            }
                        });
            }
            return;
        }
        Files.deleteIfExists(path);
    }
}

/* mutate4java-manifest
version=1
moduleHash=080f89894e26acfa200b3c2dca10a0349ee9996687565dbfc43572b1d90a8eb1
scope.0.id=Y2xhc3M6Q292ZXJhZ2VSdW5uZXIjQ292ZXJhZ2VSdW5uZXI6OQ
scope.0.kind=class
scope.0.startLine=9
scope.0.endLine=51
scope.0.semanticHash=222115fc5fd871d8ad974cf4c94cd0e52e88ee06fe1e89a8e2c6416610e667ec
scope.1.id=ZmllbGQ6Q292ZXJhZ2VSdW5uZXIjZXhlY3V0b3I6MTE
scope.1.kind=field
scope.1.startLine=11
scope.1.endLine=11
scope.1.semanticHash=c4eedf9e7c0e6dffb225b9db0a0ca739c26698121741ae20349559e2bc48602a
scope.2.id=bWV0aG9kOkNvdmVyYWdlUnVubmVyI2N0b3IoMSk6MTM
scope.2.kind=method
scope.2.startLine=13
scope.2.endLine=15
scope.2.semanticHash=24bd1d89a71e4c966e1f894918fbada2a751dbd894339d3832764a5b9ddf2608
scope.3.id=bWV0aG9kOkNvdmVyYWdlUnVubmVyI2RlbGV0ZUlmRXhpc3RzKDEpOjMy
scope.3.kind=method
scope.3.startLine=32
scope.3.endLine=50
scope.3.semanticHash=3e9c1df528853970581858951a8747028bd49974850a38b37d4b10b3527140c5
scope.4.id=bWV0aG9kOkNvdmVyYWdlUnVubmVyI2dlbmVyYXRlQ292ZXJhZ2UoMSk6MTc
scope.4.kind=method
scope.4.startLine=17
scope.4.endLine=30
scope.4.semanticHash=2370d9f69b2da165bd3ec40585384649f6d75eb3193c5a09baff7d25762fa826
*/
