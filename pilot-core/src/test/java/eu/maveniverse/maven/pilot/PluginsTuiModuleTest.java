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

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Additional PluginsTui tests focusing on per-module version tracking and module identity.
 */
class PluginsTuiModuleTest {

    @TempDir
    Path tempDir;

    private Path subdir(String name) throws IOException {
        return Files.createDirectories(tempDir.resolve(name));
    }

    private PilotProject createProject(
            String groupId,
            String artifactId,
            String version,
            Path basedir,
            List<PilotProject.Plugin> plugins,
            List<PilotProject.Plugin> managedPlugins) {
        PilotProject pp = new PilotProject(
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
        pp.setPlugins(plugins);
        pp.setManagedPlugins(managedPlugins);
        return pp;
    }

    private PluginsTui createTui(PilotProject project, List<PilotProject> allProjects) {
        return new PluginsTui(project, allProjects, (g, a) -> List.of());
    }

    // --- PluginEntry.hasVersionConflict() ---

    static Stream<Arguments> hasVersionConflictCases() {
        return Stream.of(
                // description, entryVersion, v1, v2, expectedConflict
                Arguments.of("same version → no conflict", "1.0", "1.0", "1.0", false),
                Arguments.of("different versions → conflict", "1.0", "1.0", "2.0", true),
                Arguments.of("all empty versions → no conflict", "", "", "", false),
                Arguments.of("one empty version ignored → no conflict", "1.0", "1.0", "", false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("hasVersionConflictCases")
    void pluginEntryVersionConflict(
            String description, String entryVersion, String v1, String v2, boolean expectedConflict) {
        PluginsTui.PluginEntry entry = new PluginsTui.PluginEntry("g", "a", entryVersion, false);
        entry.moduleVersions.put("g1:mod1", v1);
        entry.moduleVersions.put("g2:mod2", v2);
        assertThat(entry.hasVersionConflict()).isEqualTo(expectedConflict);
    }

    // --- Module identity uses ga() not artifactId ---

    @Test
    void sameArtifactIdDifferentGroupIdAreDistinctModules() throws IOException {
        // Two modules with same artifactId but different groupIds should NOT collide
        Path dir1 = subdir("ga-mod1");
        Path dir2 = subdir("ga-mod2");
        PilotProject p1 = createProject(
                "com.group1",
                "app",
                "1.0",
                dir1,
                List.of(new PilotProject.Plugin("org.apache.maven.plugins", "maven-compiler-plugin", "3.11.0")),
                List.of());
        PilotProject p2 = createProject(
                "com.group2",
                "app", // same artifactId, different groupId
                "1.0",
                dir2,
                List.of(new PilotProject.Plugin("org.apache.maven.plugins", "maven-compiler-plugin", "3.12.0")),
                List.of());

        PluginsTui tui = createTui(p1, List.of(p1, p2));

        // The plugin entry should see both modules as distinct keys and report a conflict
        PluginsTui.PluginEntry entry = tui.plugins.stream()
                .filter(e -> "maven-compiler-plugin".equals(e.artifactId))
                .findFirst()
                .orElseThrow();
        assertThat(entry.moduleVersions).containsOnlyKeys("com.group1:app", "com.group2:app");
        assertThat(entry.hasVersionConflict()).isTrue();
    }

    @Test
    void versionConflictAcrossModulesWithSameArtifactId() throws IOException {
        Path dir1 = subdir("conflict1");
        Path dir2 = subdir("conflict2");
        // Same groupId to ensure this is about the artifactId collision fix
        PilotProject p1 = createProject(
                "com.example",
                "module-a",
                "1.0",
                dir1,
                List.of(new PilotProject.Plugin("org.apache.maven.plugins", "maven-compiler-plugin", "3.11.0")),
                List.of());
        PilotProject p2 = createProject(
                "com.example",
                "module-b",
                "1.0",
                dir2,
                List.of(new PilotProject.Plugin("org.apache.maven.plugins", "maven-compiler-plugin", "3.12.0")),
                List.of());

        PluginsTui tui = createTui(p1, List.of(p1, p2));
        // Module list for the plugin should contain both ga() keys (not both mapped to "module-a")
        PluginsTui.PluginEntry entry = tui.plugins.stream()
                .filter(e -> "maven-compiler-plugin".equals(e.artifactId))
                .findFirst()
                .orElseThrow();
        assertThat(entry.moduleVersions).containsOnlyKeys("com.example:module-a", "com.example:module-b");
        assertThat(entry.hasVersionConflict()).isTrue();
    }

    // --- Sorting in all three views ---

    static Stream<Arguments> sortViewCases() {
        List<PilotProject.Plugin> twoPlugins = List.of(
                new PilotProject.Plugin("org.apache.maven.plugins", "maven-surefire-plugin", "3.2.5"),
                new PilotProject.Plugin("org.apache.maven.plugins", "maven-compiler-plugin", "3.11.0"));
        List<PilotProject.Plugin> twoManaged = List.of(
                new PilotProject.Plugin("org.apache.maven.plugins", "maven-jar-plugin", "3.3.0"),
                new PilotProject.Plugin("org.apache.maven.plugins", "maven-assembly-plugin", "3.6.0"));
        List<PilotProject.Plugin> onePlugin =
                List.of(new PilotProject.Plugin("org.apache.maven.plugins", "maven-compiler-plugin", "3.11.0"));
        return Stream.of(
                Arguments.of("Plugins view", 0, twoPlugins, List.of(), false),
                Arguments.of("Managed view", 1, List.of(), twoManaged, false),
                Arguments.of("Updates view", 2, onePlugin, List.of(), true));
    }

    @ParameterizedTest(name = "sort key handled in {0}")
    @MethodSource("sortViewCases")
    void sortKeyPressHandledInView(
            String viewName,
            int subView,
            List<PilotProject.Plugin> plugins,
            List<PilotProject.Plugin> managedPlugins,
            boolean setLoadingFalse)
            throws IOException {
        Path dir = subdir("sort-" + viewName.toLowerCase().replace(' ', '-'));
        PilotProject project = createProject("com.example", "app", "1.0", dir, plugins, managedPlugins);
        PluginsTui tui = createTui(project, List.of(project));
        if (setLoadingFalse) {
            tui.loading = false;
        }
        tui.setActiveSubView(subView);

        // s key → triggers sort (should not throw)
        assertThat(tui.handleKeyEvent(KeyEvent.ofChar('s'))).isTrue();
        String output = TuiTestHelper.render(tui::renderStandalone);
        assertThat(output).isNotEmpty();
    }

    // --- Digit key view switching in standalone ---

    @Test
    void digitKey1SwitchesToPluginsView() throws IOException {
        Path dir = subdir("digit-1");
        PilotProject project = createProject("com.example", "app", "1.0", dir, List.of(), List.of());
        PluginsTui tui = createTui(project, List.of(project));
        tui.setActiveSubView(2);

        tui.handleEvent(KeyEvent.ofChar('1'), null);
        assertThat(tui.activeSubView()).isZero();
    }

    @Test
    void digitKey2SwitchesToManagedView() throws IOException {
        Path dir = subdir("digit-2");
        PilotProject project = createProject("com.example", "app", "1.0", dir, List.of(), List.of());
        PluginsTui tui = createTui(project, List.of(project));

        tui.handleEvent(KeyEvent.ofChar('2'), null);
        assertThat(tui.activeSubView()).isEqualTo(1);
    }

    @Test
    void digitKey3SwitchesToUpdatesView() throws IOException {
        Path dir = subdir("digit-3");
        PilotProject project = createProject("com.example", "app", "1.0", dir, List.of(), List.of());
        PluginsTui tui = createTui(project, List.of(project));

        tui.handleEvent(KeyEvent.ofChar('3'), null);
        assertThat(tui.activeSubView()).isEqualTo(2);
    }

    // --- Render with version conflict detail pane ---

    @Test
    void renderPluginWithVersionConflictShowsAnnotation() throws IOException {
        Path dir1 = subdir("conflict-render1");
        Path dir2 = subdir("conflict-render2");
        PilotProject p1 = createProject(
                "com.example",
                "mod-a",
                "1.0",
                dir1,
                List.of(new PilotProject.Plugin("org.apache.maven.plugins", "maven-compiler-plugin", "3.11.0")),
                List.of());
        PilotProject p2 = createProject(
                "com.example",
                "mod-b",
                "1.0",
                dir2,
                List.of(new PilotProject.Plugin("org.apache.maven.plugins", "maven-compiler-plugin", "3.12.0")),
                List.of());

        PluginsTui tui = createTui(p1, List.of(p1, p2));
        // Render — should not throw even with version conflict
        String output = TuiTestHelper.render(tui::renderStandalone);
        assertThat(output).isNotEmpty();
    }

    // --- totalLibYears deduplicates by GA ---

    @Test
    void totalLibYearsDoesNotDoubleCountSharedGaEntries() throws IOException {
        // When a declared plugin and a managed plugin share the same GA, the status
        // bar libyear total should count that GA only once, not twice.
        Path dir = subdir("libyears-dedup");
        PilotProject.Plugin declared =
                new PilotProject.Plugin("org.apache.maven.plugins", "maven-compiler-plugin", "3.11.0");
        PilotProject.Plugin managed =
                new PilotProject.Plugin("org.apache.maven.plugins", "maven-compiler-plugin", "3.11.0");
        PilotProject project = createProject("com.example", "app", "1.0", dir, List.of(declared), List.of(managed));
        PluginsTui tui = createTui(project, List.of(project));

        // Simulate version resolution for both entries
        for (PluginsTui.PluginEntry e : tui.plugins) {
            e.newestVersion = "3.14.0";
            e.updateType = VersionComparator.UpdateType.MINOR;
            e.libYears = 1.5f;
        }
        for (PluginsTui.PluginEntry e : tui.managed) {
            e.newestVersion = "3.14.0";
            e.updateType = VersionComparator.UpdateType.MINOR;
            // Simulate both entries having libYears set (the double-count bug scenario)
            e.libYears = 1.5f;
        }

        tui.loading = false;
        tui.applyFilter();

        // Both entries appear in updates (size 2), but totalLibYears() must count the GA only once
        assertThat(tui.updates).hasSize(2);
        assertThat(tui.totalLibYears()).isEqualTo(1.5f);
    }

    // --- applyFilter includes both declared and managed entries with same GA ---

    @Test
    void updatesViewIncludesBothDeclaredAndManagedEntriesWithSameGa() throws IOException {
        // When a declared plugin and a managed plugin share the same GA (both have updates),
        // the Updates view must show both entries, not suppress the managed one.
        Path dir = subdir("declared-managed-same-ga");
        PilotProject.Plugin declared =
                new PilotProject.Plugin("org.apache.maven.plugins", "maven-compiler-plugin", "3.11.0");
        PilotProject.Plugin managed =
                new PilotProject.Plugin("org.apache.maven.plugins", "maven-compiler-plugin", "3.11.0");
        PilotProject project = createProject("com.example", "app", "1.0", dir, List.of(declared), List.of(managed));
        PluginsTui tui = createTui(project, List.of(project));

        // Simulate version resolution resolving the same GA to a newer version
        for (PluginsTui.PluginEntry e : tui.plugins) {
            e.newestVersion = "3.14.0";
            e.updateType = VersionComparator.UpdateType.MINOR;
        }
        for (PluginsTui.PluginEntry e : tui.managed) {
            e.newestVersion = "3.14.0";
            e.updateType = VersionComparator.UpdateType.MINOR;
        }

        tui.loading = false;
        tui.applyFilter();

        // Both the declared and managed entry should appear in updates (not suppressed by putIfAbsent)
        assertThat(tui.updates).hasSize(2);
    }

    // --- handleEvent key ---

    @Test
    void searchKeyEventHandled() throws IOException {
        Path dir = subdir("search");
        PilotProject project = createProject(
                "com.example",
                "app",
                "1.0",
                dir,
                List.of(new PilotProject.Plugin("org.apache.maven.plugins", "maven-compiler-plugin", "3.11.0")),
                List.of());
        PluginsTui tui = createTui(project, List.of(project));

        // '/' → starts filter; Escape → clears filter
        assertThat(tui.handleKeyEvent(KeyEvent.ofChar('/'))).isTrue();
        assertThat(tui.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE))).isTrue();
    }
}
