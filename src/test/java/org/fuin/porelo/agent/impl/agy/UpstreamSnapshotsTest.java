package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Where snapshots come from on a forge whose registry holds only its organization's packages: a
 * second, anonymous repository the forge names, which never gets the forge's token, and which a
 * tag's build reads no more than the others. Self-contained, so the same test stands beside every
 * adapter's {@code settings.xml}.
 */
class UpstreamSnapshotsTest {

    /** The server id that carries the forge's read-only token. */
    private static final String FORGE = "porelo-forge";

    private static final String FORGE_URL = "${env.MAVEN_SNAPSHOTS_URL}";

    private static final String UPSTREAM_URL = "${env.MAVEN_UPSTREAM_SNAPSHOTS_URL}";

    /** Maven's id for a profile or a server that names none. */
    private static final String DEFAULT_ID = "default";

    private static final Pattern TAG_ARGS =
            Pattern.compile(
                    "(?m)^\\s*MAVEN_ARGS:\\s*\\$\\{\\{\\s*startsWith\\(github\\.ref, 'refs/tags/v'\\)"
                            + "\\s*&&\\s*'([^']*)'");

    private static final String SETTINGS_HEAD =
            "<settings xmlns=\"http://maven.apache.org/SETTINGS/1.0.0\"><servers><server><id>"
                    + FORGE
                    + "</id></server><server><id>central-sonatype</id></server></servers>";

    private static final String FORGE_PROFILE =
            "<profile><id>forge</id><repositories><repository><id>"
                    + FORGE
                    + "</id><url>"
                    + FORGE_URL
                    + "</url><snapshots><enabled>true</enabled></snapshots></repository>"
                    + "</repositories></profile>";

    private static final String NO_REPOSITORIES_POM =
            "<project><modelVersion>4.0.0</modelVersion></project>";

    private static final String TAG_BUILD =
            "env:\n    MAVEN_ARGS: ${{ startsWith(github.ref, 'refs/tags/v') && '-P!forge' || '-U' }}\n";

    @Test
    void theForgesTokenGoesToTheForgesRegistryAlone() throws Exception {

        // Maven sends a server's credential to every repository and mirror of the same id, wherever
        // its URL points: a second one under the forge's id would hand its token to that host.
        final Document settings = parse(RepositoryDocuments.root().resolve("settings.xml"));
        final Document pom = parse(RepositoryDocuments.root().resolve("pom.xml"));

        assertThat(serverIds(settings))
                .as("the servers settings.xml gives credentials for")
                .containsExactlyInAnyOrder("central-sonatype", FORGE);
        assertThat(urls(settings, FORGE))
                .as("the URLs the forge's token is sent to")
                .containsOnly(FORGE_URL)
                .hasSize(2);
        assertThat(urls(settings, "porelo-upstream-snapshots"))
                .as("the URLs of the upstream repository and plugin repository")
                .containsExactly(UPSTREAM_URL, UPSTREAM_URL);
        assertThat(credentialLeaks(settings, pom))
                .as("where a credential could go astray")
                .isEmpty();
    }

    @Test
    void seesEveryWayACredentialGoesAstray() throws Exception {

        assertThat(credentialLeaks(settings(FORGE_PROFILE), pom(NO_REPOSITORIES_POM)))
                .as("the forge's repository alone")
                .isEmpty();
        assertThat(
                        credentialLeaks(
                                settings(
                                        "<profile><id>x</id><repositories><repository><id>"
                                                + FORGE
                                                + "</id><url>https://elsewhere.example.org/</url>"
                                                + "</repository></repositories></profile>"),
                                pom(NO_REPOSITORIES_POM)))
                .as("a second repository under the forge's id")
                .isNotEmpty();
        assertThat(
                        credentialLeaks(
                                settings(
                                        "<profile><id>x</id><repositories><repository><id>"
                                                + "central-sonatype</id><url>"
                                                + FORGE_URL
                                                + "</url></repository></repositories></profile>"),
                                pom(NO_REPOSITORIES_POM)))
                .as("another server's credential sent to the forge")
                .isNotEmpty();
        assertThat(
                        credentialLeaks(
                                settings(
                                        "<profile><repositories><repository><id>"
                                                + FORGE
                                                + "</id><url>https://elsewhere.example.org/</url>"
                                                + "</repository></repositories><id>x</id></profile>"),
                                pom(NO_REPOSITORIES_POM)))
                .as("a repository read before its profile's id")
                .isNotEmpty();
        assertThat(
                        credentialLeaks(
                                settings(
                                        "</profiles><mirrors><mirror><id>"
                                                + FORGE
                                                + "</id><mirrorOf>*</mirrorOf>"
                                                + "<url>https://elsewhere.example.org/</url>"
                                                + "</mirror></mirrors><profiles>"),
                                pom(NO_REPOSITORIES_POM)))
                .as("a mirror")
                .isNotEmpty();
        assertThat(
                        credentialLeaks(
                                settings(FORGE_PROFILE),
                                pom(
                                        "<project><repositories><repository><id>"
                                                + FORGE
                                                + "</id><url>https://elsewhere.example.org/</url>"
                                                + "</repository></repositories></project>")))
                .as("a repository in pom.xml")
                .isNotEmpty();
    }

    @Test
    void aTagReadsNoSnapshotRepository() throws Exception {

        // A release built against a snapshot ships what nobody released.
        final Document settings = parse(RepositoryDocuments.root().resolve("settings.xml"));
        final Document pom = parse(RepositoryDocuments.root().resolve("pom.xml"));
        final String build =
                Files.readString(RepositoryDocuments.root().resolve(".github/workflows/build.yml"));

        assertThat(snapshotProfiles(settings))
                .as("the profiles of settings.xml that read snapshots")
                .containsExactly("forge", "standard", "upstream");
        assertThat(tagReadsSnapshots(settings, pom, build))
                .as("what a tag's build could still read snapshots from")
                .isEmpty();
    }

    @Test
    void seesEveryWayATagReadsSnapshots() throws Exception {

        final Document pom = pom(NO_REPOSITORIES_POM);
        assertThat(tagReadsSnapshots(settings(FORGE_PROFILE), pom, TAG_BUILD))
                .as("the one profile turned off")
                .isEmpty();
        assertThat(
                        tagReadsSnapshots(
                                settings(
                                        FORGE_PROFILE
                                                + "<profile><id>extra</id><repositories>"
                                                + "<repository><id>r</id><url>u</url></repository>"
                                                + "</repositories></profile>"),
                                pom,
                                TAG_BUILD))
                .as("a profile whose repository reads snapshots by Maven's default")
                .isNotEmpty();
        assertThat(
                        tagReadsSnapshots(
                                settings(
                                        FORGE_PROFILE
                                                + "<profile><repositories><repository>"
                                                + "<id>forge</id><url>u</url><snapshots><updatePolicy>always"
                                                + "</updatePolicy></snapshots></repository></repositories>"
                                                + "</profile>"),
                                pom,
                                TAG_BUILD))
                .as("a profile without an id, its repository named like a counted profile")
                .isNotEmpty();
        assertThat(
                        tagReadsSnapshots(
                                settings(
                                        FORGE_PROFILE
                                                + "</profiles><activeProfiles><activeProfile>"
                                                + "forge</activeProfile></activeProfiles><profiles>"),
                                pom,
                                TAG_BUILD))
                .as("a profile settings.xml always activates")
                .isNotEmpty();
        assertThat(
                        tagReadsSnapshots(
                                settings(FORGE_PROFILE),
                                pom(
                                        "<project><repositories><repository><id>r</id><url>u</url>"
                                                + "</repository></repositories></project>"),
                                TAG_BUILD))
                .as("a repository in pom.xml, which no profile switch turns off")
                .isNotEmpty();
        for (final String args :
                List.of(
                        "'!forge'",
                        "'-D!forge'",
                        "'-P!forge, !other'",
                        "'-P!forge,forge'",
                        "'-P!other'")) {
            assertThat(
                            tagReadsSnapshots(
                                    settings(FORGE_PROFILE),
                                    pom,
                                    TAG_BUILD.replace("'-P!forge'", args)))
                    .as("a tag's MAVEN_ARGS of %s", args)
                    .isNotEmpty();
        }
        assertThat(
                        tagReadsSnapshots(
                                settings(FORGE_PROFILE),
                                pom,
                                "#" + TAG_BUILD.substring(TAG_BUILD.indexOf('\n') + 1)))
                .as("a commented-out MAVEN_ARGS")
                .isNotEmpty();
    }

    /** What could send a server's credential to a host other than the forge's registry. */
    private static List<String> credentialLeaks(final Document settings, final Document pom) {
        final List<String> leaks = new ArrayList<>(pomRepositories(pom));
        final Set<String> servers = serverIds(settings);
        if (settings.getElementsByTagName("mirror").getLength() > 0) {
            leaks.add("settings.xml has a mirror, which takes over a repository's id and URL");
        }
        for (final Element repository : repositories(settings.getDocumentElement())) {
            final String id = child(repository, "id", DEFAULT_ID);
            final String url = child(repository, "url", "");
            if (servers.contains(id) && !(FORGE.equals(id) && FORGE_URL.equals(url))) {
                leaks.add(
                        "repository " + id + " at " + url + " gets server " + id + "'s credential");
            }
        }
        return leaks;
    }

    /** What a tag's build could read snapshots from, read as Maven reads it. */
    private static List<String> tagReadsSnapshots(
            final Document settings, final Document pom, final String build) {
        final List<String> reads = new ArrayList<>(pomRepositories(pom));
        if (settings.getElementsByTagName("activeProfile").getLength() > 0) {
            reads.add("settings.xml activates a profile no -P! turns off");
        }
        final Matcher tag = TAG_ARGS.matcher(build);
        if (!tag.find()) {
            reads.add("no MAVEN_ARGS for a tag in build.yml");
            return reads;
        }
        final String args = tag.group(1);
        if (!args.startsWith("-P") || args.chars().anyMatch(Character::isWhitespace)) {
            reads.add("a tag's MAVEN_ARGS is not one -P list: " + args);
            return reads;
        }
        final Set<String> listed = new TreeSet<>(List.of(args.substring(2).split(",")));
        final Set<String> wanted = new TreeSet<>();
        snapshotProfiles(settings).forEach(id -> wanted.add("!" + id));
        if (!listed.equals(wanted)) {
            reads.add("a tag's MAVEN_ARGS lists " + listed + " where it must list " + wanted);
        }
        return reads;
    }

    /** A pom's own repositories, which no profile switch of settings.xml turns off. */
    private static List<String> pomRepositories(final Document pom) {
        final List<String> found = new ArrayList<>();
        for (final Element repository : repositories(pom.getDocumentElement())) {
            found.add("pom.xml names repository " + child(repository, "id", DEFAULT_ID));
        }
        return found;
    }

    /** The ids of the profiles with a repository that reads snapshots. */
    private static Set<String> snapshotProfiles(final Document settings) {
        final Set<String> ids = new TreeSet<>();
        final NodeList profiles = settings.getElementsByTagName("profile");
        for (int i = 0; i < profiles.getLength(); i++) {
            final Element profile = (Element) profiles.item(i);
            if (repositories(profile).stream().anyMatch(UpstreamSnapshotsTest::takesSnapshots)) {
                ids.add(child(profile, "id", DEFAULT_ID));
            }
        }
        return ids;
    }

    /**
     * Snapshots are on unless the repository's own {@code snapshots} says {@code enabled} false.
     */
    private static boolean takesSnapshots(final Element repository) {
        final Element snapshots = childElement(repository, "snapshots");
        return snapshots == null || !"false".equalsIgnoreCase(child(snapshots, "enabled", "true"));
    }

    private static Set<String> serverIds(final Document settings) {
        final Set<String> ids = new TreeSet<>();
        final NodeList servers = settings.getElementsByTagName("server");
        for (int i = 0; i < servers.getLength(); i++) {
            ids.add(child((Element) servers.item(i), "id", DEFAULT_ID));
        }
        return ids;
    }

    /** The URLs of every repository and plugin repository of one id, in document order. */
    private static List<String> urls(final Document settings, final String id) {
        return repositories(settings.getDocumentElement()).stream()
                .filter(repository -> id.equals(child(repository, "id", DEFAULT_ID)))
                .map(repository -> child(repository, "url", ""))
                .toList();
    }

    /** Every repository and plugin repository below an element, in document order. */
    private static List<Element> repositories(final Element root) {
        final List<Element> found = new ArrayList<>();
        for (final String tag : List.of("repository", "pluginRepository")) {
            final NodeList nodes = root.getElementsByTagName(tag);
            for (int i = 0; i < nodes.getLength(); i++) {
                final Element each = (Element) nodes.item(i);
                // A pom's distributionManagement deploys; it is not read from.
                if (!"distributionManagement".equals(each.getParentNode().getNodeName())) {
                    found.add(each);
                }
            }
        }
        return found;
    }

    /** The text of an element's own child of that name, stripped, or the fallback without one. */
    private static String child(final Element parent, final String name, final String fallback) {
        final Element found = childElement(parent, name);
        return found == null ? fallback : found.getTextContent().strip();
    }

    private static Element childElement(final Element parent, final String name) {
        final NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node node = children.item(i);
            if (node instanceof Element element && name.equals(element.getTagName())) {
                return element;
            }
        }
        return null;
    }

    private static Document settings(final String profiles) throws Exception {
        return parse(SETTINGS_HEAD + "<profiles>" + profiles + "</profiles></settings>");
    }

    private static Document pom(final String text) throws Exception {
        return parse(text);
    }

    private static Document parse(final Path file) throws Exception {
        return parse(Files.readString(file));
    }

    private static Document parse(final String text) throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }
}
