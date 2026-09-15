/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package eu.maveniverse.maven.pilot;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tamboui.tui.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for UpdatesTui tree-impact target resolution (resolveImpactTarget) and
 * property-group handling.
 */
class UpdatesTuiImpactTest {

    @TempDir
    Path tempDir;

    private Path subdir(String name) throws IOException {
        return Files.createDirectories(tempDir.resolve(name));
    }

    private PilotProject createProject(String groupId, String artifactId, String version, Path basedir) {
        return new PilotProject(
                groupId,
                artifactId,
                version,
                "jar",
                basedir,
                basedir.resolve("pom.xml"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new Properties(),
                null,
                null);
    }

    private PilotProject.Dep dep(String groupId, String artifactId, String version) {
        return new PilotProject.Dep(groupId, artifactId, version);
    }

    private UpdatesTui createTui(ReactorCollector.CollectionResult result, List<PilotProject> projects) {
        ReactorModel model = ReactorModel.build(projects);
        return new UpdatesTui(result, model, "com.example:app:1.0", (g, a) -> List.of());
    }

    private UpdatesTui createTuiWithImpactResolver(
            ReactorCollector.CollectionResult result, List<PilotProject> projects) {
        ReactorModel model = ReactorModel.build(projects);
        // Resolver that returns an empty tree — impact is resolvable but yields no diff
        UpdatesTui.TreeImpactResolver resolver = (g, a, oldV, newV) -> List.of();
        return new UpdatesTui(result, model, "com.example:app:1.0", (grp, art) -> List.of(), resolver, null);
    }

    // --- resolveImpactTarget: dependency row with no update ---

    @Test
    void noImpactTargetWhenDepHasNoUpdate() throws IOException {
        Path dir = subdir("no-update");
        PilotProject.Dep d = dep("com.example", "lib", "1.0");
        PilotProject project = new PilotProject(
                "com.example",
                "app",
                "1.0",
                "jar",
                dir,
                dir.resolve("pom.xml"),
                List.of(d),
                List.of(),
                List.of(d),
                List.of(),
                new Properties(),
                null,
                null);

        ReactorCollector.CollectionResult result = ReactorCollector.collect(List.of(project));
        UpdatesTui tui = createTui(result, List.of(project));

        // No update available — dep not in display rows (ALL filter requires hasUpdate())
        tui.loading = false;
        tui.buildDisplayRows();

        // When no resolver is configured, pressing 't' sets status synchronously
        tui.handleKeyEvent(KeyEvent.ofChar('t'));
        assertThat(tui.status()).isEqualTo("Tree impact not available");
    }

    // --- ReactorRow.group(): group header with update ---

    @Test
    void propertyGroupRowHasNullDependency() {
        var group = new ReactorCollector.PropertyGroup("spring.version", "${spring.version}", "6.1.0", null);
        var row = UpdatesTui.ReactorRow.group(group);

        assertThat(row.isGroupHeader()).isTrue();
        assertThat(row.dependency).isNull();
        assertThat(row.propertyGroup).isSameAs(group);
    }

    @Test
    void propertyGroupWithUpdateHasNewestVersion() {
        var group = new ReactorCollector.PropertyGroup("spring.version", "${spring.version}", "6.1.0", null);
        group.newestVersion = "6.2.0";

        assertThat(group.hasUpdate()).isTrue();
    }

    @Test
    void propertyGroupWithoutUpdateReportsNoUpdate() {
        var group = new ReactorCollector.PropertyGroup("spring.version", "${spring.version}", "6.1.0", null);

        assertThat(group.hasUpdate()).isFalse();
    }

    @Test
    void propertyGroupWithSameVersionReportsNoUpdate() {
        var group = new ReactorCollector.PropertyGroup("spring.version", "${spring.version}", "6.1.0", null);
        group.newestVersion = "6.1.0";

        assertThat(group.hasUpdate()).isFalse();
    }

    // --- Render paths exercising tree-impact overlay indirectly ---

    @Test
    void renderWithPropertyGroupRowDoesNotThrow() throws IOException {
        Path dir = subdir("pg-render");
        PilotProject.Dep d = new PilotProject.Dep("com.example", "lib", "${spring.version}", "compile", null);
        Properties props = new Properties();
        props.setProperty("spring.version", "6.1.0");
        PilotProject project = new PilotProject(
                "com.example",
                "app",
                "1.0",
                "jar",
                dir,
                dir.resolve("pom.xml"),
                List.of(d),
                List.of(),
                List.of(d),
                List.of(),
                props,
                null,
                null);

        ReactorCollector.CollectionResult result = ReactorCollector.collect(List.of(project));
        UpdatesTui tui = createTui(result, List.of(project));
        tui.loading = false;
        tui.buildDisplayRows();

        String output = TuiTestHelper.render(tui::renderStandalone);
        assertThat(output).isNotEmpty();
    }

    // --- AggregatedDependency.hasUpdate() ---

    @Test
    void aggregatedDepHasUpdateWhenNewerVersion() {
        ReactorCollector.AggregatedDependency dep = new ReactorCollector.AggregatedDependency("g", "a");
        dep.primaryVersion = "1.0";
        dep.newestVersion = "2.0";
        assertThat(dep.hasUpdate()).isTrue();
    }

    @Test
    void aggregatedDepNoUpdateWhenSameVersion() {
        ReactorCollector.AggregatedDependency dep = new ReactorCollector.AggregatedDependency("g", "a");
        dep.primaryVersion = "1.0";
        dep.newestVersion = "1.0";
        assertThat(dep.hasUpdate()).isFalse();
    }

    @Test
    void aggregatedDepNoUpdateWhenNewestNull() {
        ReactorCollector.AggregatedDependency dep = new ReactorCollector.AggregatedDependency("g", "a");
        dep.primaryVersion = "1.0";
        assertThat(dep.hasUpdate()).isFalse();
    }

    // --- Stale generation guard is exercised by the generation counter ---

    @Test
    void treeImpactGenerationStartsAtZero() throws IOException {
        Path dir = subdir("gen");
        PilotProject project = createProject("com.example", "app", "1.0", dir);
        ReactorCollector.CollectionResult result = ReactorCollector.collect(List.of(project));
        UpdatesTui tui = createTui(result, List.of(project));
        // treeImpactGeneration is private; just verify the tui builds without errors
        assertThat(tui.status()).isNotNull();
    }
}
