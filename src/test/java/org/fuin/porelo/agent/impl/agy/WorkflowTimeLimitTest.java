package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class WorkflowTimeLimitTest {

    /** A job's id: any key directly under {@code jobs:}, whatever its case. */
    private static final Pattern JOB = Pattern.compile("^    ([A-Za-z0-9_][A-Za-z0-9_-]*):\\s*$");

    /** A key at the top of the file, which ends {@code jobs:}. */
    private static final Pattern TOP_LEVEL = Pattern.compile("^[A-Za-z_][^:]*:.*$");

    @Test
    void everyJobStopsAfterALimit() throws IOException {

        // GitHub's default is six hours, and a leg on a rented machine pays for every minute of a
        // hang.
        final List<String> jobs = new ArrayList<>();
        final List<String> unlimited = new ArrayList<>();
        try (Stream<Path> files =
                Files.list(RepositoryDocuments.root().resolve(".github/workflows"))) {
            for (final Path file :
                    files.filter(f -> f.toString().endsWith(".yml")).sorted().toList()) {
                boolean inJobs = false;
                String job = null;
                boolean limited = false;
                for (final String line : Files.readAllLines(file)) {
                    if (line.equals("jobs:")) {
                        inJobs = true;
                        continue;
                    }
                    if (TOP_LEVEL.matcher(line).matches()) {
                        inJobs = false;
                    }
                    final Matcher m = JOB.matcher(line);
                    if (inJobs && m.matches()) {
                        if (job != null && !limited) {
                            unlimited.add(file.getFileName() + " " + job);
                        }
                        job = m.group(1);
                        jobs.add(file.getFileName() + " " + job);
                        limited = false;
                    } else if (job != null
                            && line.matches("^        timeout-minutes: [1-9][0-9]*$")) {
                        limited = true;
                    }
                }
                if (job != null && !limited) {
                    unlimited.add(file.getFileName() + " " + job);
                }
            }
        }
        // A job the scan does not see is a job nobody checks: the set read is the set there is.
        assertThat(jobs)
                .as("the jobs read from the workflows")
                .containsExactlyInAnyOrder(
                        "build.yml build",
                        "build.yml proof",
                        "build.yml acceptance",
                        "build.yml release",
                        "shared-rules.yml check-shared",
                        "update.yml update",
                        "workflows.yml check");
        assertThat(unlimited).as("jobs without timeout-minutes").isEmpty();
    }
}
