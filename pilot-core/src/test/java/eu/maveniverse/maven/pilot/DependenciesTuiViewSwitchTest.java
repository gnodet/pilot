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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the view-switch hint text and help sections in DependenciesTui.
 */
class DependenciesTuiViewSwitchTest {

    @TempDir
    Path tempDir;

    private Path pomPath() throws IOException {
        Path pom = tempDir.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0"?>
                <project><modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId><artifactId>app</artifactId><version>1.0</version>
                </project>
                """);
        return pom;
    }

    // --- Help sections contain view labels ---

    @Test
    void helpSectionsContainViewSwitchHint() throws IOException {
        Path pom = pomPath();
        DependenciesTui tui = new DependenciesTui(List.of(), List.of(), pom.toString(), "com.example:app:1.0", false);

        List<HelpOverlay.Section> sections = tui.helpSections();
        assertThat(sections).isNotEmpty();

        // At least one section should mention view switching with "1-"
        boolean hasViewHint = sections.stream()
                .flatMap(s -> s.entries().stream())
                .anyMatch(e -> e.key().startsWith("1-") || e.description().contains("Switch"));
        assertThat(hasViewHint).isTrue();
    }

    @Test
    void helpSectionsWithManagedDepsReflectsCorrectViewCount() throws IOException {
        Path pom = pomPath();
        DependenciesTui.ManagedEntry me =
                new DependenciesTui.ManagedEntry("org.slf4j", "slf4j-api", "2.0.9", "compile", "jar", "own");
        DependenciesTui tui = new DependenciesTui(
                List.of(), List.of(), List.of(me), new PomEditSession(pom), "com.example:app:1.0", false);

        List<HelpOverlay.Section> sections = tui.helpSections();
        assertThat(sections).isNotEmpty();

        // Should show at least 3 views (Declared, Transitive, Managed)
        boolean hasViewHint = sections.stream()
                .flatMap(s -> s.entries().stream())
                .anyMatch(e -> e.key().contains("1-3")
                        || e.key().contains("1-4")
                        || e.key().contains("1-5"));
        assertThat(hasViewHint).isTrue();
    }

    @Test
    void renderStandaloneWithDeclaredDeps() throws IOException {
        Path pom = pomPath();
        DependenciesTui.DepEntry dep =
                new DependenciesTui.DepEntry("org.slf4j", "slf4j-api", "", "2.0.9", "compile", true);
        DependenciesTui tui = new DependenciesTui(List.of(dep), List.of(), pom.toString(), "com.example:app:1.0", true);

        String output = TuiTestHelper.render(tui::renderStandalone);
        assertThat(output).isNotEmpty();
    }

    @Test
    void renderStandaloneWithManagedDeps() throws IOException {
        Path pom = pomPath();
        DependenciesTui.ManagedEntry me =
                new DependenciesTui.ManagedEntry("org.slf4j", "slf4j-api", "2.0.9", "compile", "jar", "own");
        DependenciesTui tui = new DependenciesTui(
                List.of(), List.of(), List.of(me), new PomEditSession(pom), "com.example:app:1.0", false);

        // Switch to Managed view (view index 2 in Declared/Transitive/Managed layout)
        tui.setActiveSubView(2);
        String output = TuiTestHelper.render(tui::renderStandalone);
        assertThat(output).isNotEmpty();
    }

    @Test
    void subViewCountReflectsPresenceOfManagedDeps() throws IOException {
        Path pom = pomPath();
        DependenciesTui tuiNoManaged =
                new DependenciesTui(List.of(), List.of(), pom.toString(), "com.example:app:1.0", false);
        // Without tree panel: DECLARED, TRANSITIVE, MANAGED → 3 views
        assertThat(tuiNoManaged.subViewCount()).isEqualTo(3);
    }

    // --- View labels are meaningful strings ---

    @Test
    void statusNotNullAfterConstruction() throws IOException {
        Path pom = pomPath();
        DependenciesTui tui = new DependenciesTui(List.of(), List.of(), pom.toString(), "com.example:app:1.0", false);
        assertThat(tui.status()).isNotNull();
    }

    @Test
    void toolNameIsDependencies() throws IOException {
        Path pom = pomPath();
        DependenciesTui tui = new DependenciesTui(List.of(), List.of(), pom.toString(), "com.example:app:1.0", false);
        assertThat(tui.toolName()).isEqualTo("Deps");
    }
}
