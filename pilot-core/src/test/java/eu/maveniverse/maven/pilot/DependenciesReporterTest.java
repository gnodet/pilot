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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DependenciesReporterTest {

    @Test
    void formatFindingsUnusedDeclaredOnly() {
        var dep = new DependenciesTui.DepEntry("com.example", "unused-lib", "", "1.0", "compile", true);

        String output = DependenciesReporter.formatFindings(List.of(dep), List.of());

        assertThat(output)
                .contains("Unused declared dependency (can be removed):")
                .contains("com.example:unused-lib")
                .doesNotContain("transitive");
    }

    @Test
    void formatFindingsUsedTransitiveOnly() {
        var dep = new DependenciesTui.DepEntry("com.transitive", "lib", "", "2.0", "compile", false);

        String output = DependenciesReporter.formatFindings(List.of(), List.of(dep));

        assertThat(output)
                .contains("Used transitive dependency (should be declared):")
                .contains("com.transitive:lib")
                .doesNotContain("Unused");
    }

    @Test
    void formatFindingsBothSections() {
        var unused = new DependenciesTui.DepEntry("com.example", "unused", "", "1.0", "compile", true);
        var transitive = new DependenciesTui.DepEntry("com.transitive", "needed", "", "2.0", "runtime", false);

        String output = DependenciesReporter.formatFindings(List.of(unused), List.of(transitive));

        assertThat(output)
                .contains("Unused declared dependency (can be removed):")
                .contains("com.example:unused")
                .contains("Used transitive dependency (should be declared):")
                .contains("com.transitive:needed (runtime)");
    }

    @Test
    void formatFindingsMultipleDeps() {
        var unused1 = new DependenciesTui.DepEntry("com.a", "one", "", "1.0", "compile", true);
        var unused2 = new DependenciesTui.DepEntry("com.b", "two", "", "1.0", "test", true);

        String output = DependenciesReporter.formatFindings(List.of(unused1, unused2), List.of());

        assertThat(output)
                .contains("Unused declared dependencies (can be removed):")
                .contains("com.a:one")
                .contains("com.b:two (test)");
    }

    @Test
    void formatFindingsCompileScopeOmitted() {
        var dep = new DependenciesTui.DepEntry("com.example", "lib", "", "1.0", "compile", true);

        String output = DependenciesReporter.formatFindings(List.of(dep), List.of());

        assertThat(output).contains("com.example:lib\n").doesNotContain("(compile)");
    }

    @Test
    void formatFindingsNonCompileScopeShown() {
        var dep = new DependenciesTui.DepEntry("com.example", "lib", "", "1.0", "provided", true);

        String output = DependenciesReporter.formatFindings(List.of(dep), List.of());

        assertThat(output).contains("com.example:lib (provided)");
    }

    @Test
    void appendScopeSkipsCompile() {
        StringBuilder sb = new StringBuilder();
        var dep = new DependenciesTui.DepEntry("g", "a", "", "1", "compile", true);
        DependenciesReporter.appendScope(sb, dep);
        assertThat(sb).isEmpty();
    }

    @Test
    void appendScopeIncludesNonCompile() {
        StringBuilder sb = new StringBuilder();
        var dep = new DependenciesTui.DepEntry("g", "a", "", "1", "test", true);
        DependenciesReporter.appendScope(sb, dep);
        assertThat(sb).hasToString(" (test)");
    }

    @Test
    void formatCheckFailureIncludesHint() {
        var dep = new DependenciesTui.DepEntry("com.example", "unused", "", "1.0", "compile", true);

        String msg = DependenciesReporter.formatCheckFailure(List.of(dep), List.of());

        assertThat(msg).contains("com.example:unused").contains("pilot.action=fix");
    }

    @Test
    void formatFindingsEmpty() {
        String output = DependenciesReporter.formatFindings(List.of(), List.of());
        assertThat(output).isEmpty();
    }

    // -- fix --

    @Test
    void fixRemovesUnusedDependency(@TempDir Path tempDir) throws Exception {
        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, """
                <project>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>unused-lib</artifactId>
                      <version>1.0</version>
                    </dependency>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>kept-lib</artifactId>
                      <version>2.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        var unused = new DependenciesTui.DepEntry("com.example", "unused-lib", "", "1.0", "compile", true);
        List<String> logs = new ArrayList<>();

        DependenciesReporter.fix(pomPath, List.of(unused), List.of(), Map.of(), logs::add);

        String result = Files.readString(pomPath);
        assertThat(result).doesNotContain("unused-lib").contains("kept-lib");
        assertThat(logs).anyMatch(l -> l.contains("Removed unused dependency: com.example:unused-lib"));
    }

    @Test
    void fixAddsUsedTransitiveDependency(@TempDir Path tempDir) throws Exception {
        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, """
                <project>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>existing-lib</artifactId>
                      <version>1.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        var transitive = new DependenciesTui.DepEntry("org.needed", "transitive-lib", "", "2.0", "compile", false);
        List<String> logs = new ArrayList<>();

        DependenciesReporter.fix(
                pomPath, List.of(), List.of(transitive), Map.of("org.needed:transitive-lib", "2.0"), logs::add);

        String result = Files.readString(pomPath);
        assertThat(result).contains("transitive-lib").contains("existing-lib");
        assertThat(logs).anyMatch(l -> l.contains("Added used transitive dependency: org.needed:transitive-lib"));
    }

    @Test
    void fixHandlesNonCompileScope(@TempDir Path tempDir) throws Exception {
        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, """
                <project>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>existing</artifactId>
                      <version>1.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        var transitive = new DependenciesTui.DepEntry("org.test", "test-lib", "", "1.0", "test", false);
        List<String> logs = new ArrayList<>();

        DependenciesReporter.fix(
                pomPath, List.of(), List.of(transitive), Map.of("org.test:test-lib", "1.0"), logs::add);

        String result = Files.readString(pomPath);
        assertThat(result).contains("test-lib").contains("<scope>test</scope>");
    }

    @Test
    void fixBothRemovesAndAdds(@TempDir Path tempDir) throws Exception {
        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, """
                <project>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>unused</artifactId>
                      <version>1.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        var unused = new DependenciesTui.DepEntry("com.example", "unused", "", "1.0", "compile", true);
        var transitive = new DependenciesTui.DepEntry("org.needed", "needed", "", "2.0", "compile", false);
        List<String> logs = new ArrayList<>();

        DependenciesReporter.fix(
                pomPath, List.of(unused), List.of(transitive), Map.of("org.needed:needed", "2.0"), logs::add);

        String result = Files.readString(pomPath);
        assertThat(result).doesNotContain("com.example").contains("needed");
        assertThat(logs).hasSize(3); // remove + add + updated
    }

    // -- fix with ancestorManagedGAs --

    @Test
    void fixAncestorManagedOmitsVersion(@TempDir Path tempDir) throws Exception {
        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, """
                <project>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>existing</artifactId>
                      <version>1.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        var transitive = new DependenciesTui.DepEntry("org.managed", "ancestor-lib", "", "3.0", "compile", false);
        List<String> logs = new ArrayList<>();
        Set<String> ancestorManaged = Set.of("org.managed:ancestor-lib");

        DependenciesReporter.fix(
                pomPath,
                List.of(),
                List.of(transitive),
                Map.of("org.managed:ancestor-lib", "3.0"),
                ancestorManaged,
                logs::add);

        String result = Files.readString(pomPath);
        assertThat(result).contains("ancestor-lib");
        // The newly added dependency must NOT have its resolved version hardcoded
        assertThat(result).doesNotContain("<version>3.0</version>");
        assertThat(logs).anyMatch(l -> l.contains("version managed by ancestor"));
    }

    @Test
    void fixAncestorManagedNonCompileScopeWritten(@TempDir Path tempDir) throws Exception {
        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, """
                <project>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>existing</artifactId>
                      <version>1.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        var transitive = new DependenciesTui.DepEntry("org.managed", "test-lib", "", "2.5", "test", false);
        List<String> logs = new ArrayList<>();
        Set<String> ancestorManaged = Set.of("org.managed:test-lib");

        DependenciesReporter.fix(
                pomPath,
                List.of(),
                List.of(transitive),
                Map.of("org.managed:test-lib", "2.5"),
                ancestorManaged,
                logs::add);

        String result = Files.readString(pomPath);
        assertThat(result).contains("test-lib");
        assertThat(result).doesNotContain("<version>2.5</version>");
        assertThat(result).contains("<scope>test</scope>");
    }

    @Test
    void fixNonAncestorManagedWritesVersion(@TempDir Path tempDir) throws Exception {
        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, """
                <project>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>existing</artifactId>
                      <version>1.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        var transitive = new DependenciesTui.DepEntry("org.other", "unmanaged-lib", "", "4.0", "compile", false);
        List<String> logs = new ArrayList<>();
        // ancestorManaged does NOT include org.other:unmanaged-lib
        Set<String> ancestorManaged = Set.of("org.managed:something-else");

        DependenciesReporter.fix(
                pomPath,
                List.of(),
                List.of(transitive),
                Map.of("org.other:unmanaged-lib", "4.0"),
                ancestorManaged,
                logs::add);

        String result = Files.readString(pomPath);
        assertThat(result).contains("unmanaged-lib").contains("4.0");
    }

    @Test
    void fixAncestorManagedNoDependenciesSection(@TempDir Path tempDir) throws Exception {
        // Tests the case where the POM has no <dependencies> section yet
        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>my-module</artifactId>
                  <version>1.0</version>
                </project>
                """);

        var transitive = new DependenciesTui.DepEntry("org.managed", "bom-lib", "", "5.0", "compile", false);
        List<String> logs = new ArrayList<>();
        Set<String> ancestorManaged = Set.of("org.managed:bom-lib");

        DependenciesReporter.fix(
                pomPath,
                List.of(),
                List.of(transitive),
                Map.of("org.managed:bom-lib", "5.0"),
                ancestorManaged,
                logs::add);

        String result = Files.readString(pomPath);
        assertThat(result).contains("bom-lib");
        assertThat(result).doesNotContain("<version>5.0</version>");
        assertThat(result).contains("<dependencies>");
    }

    // -- fix with gaToRawVersion (property expressions) --

    @Test
    void fixUsesPropertyExpressionFromAncestorDM(@TempDir Path tempDir) throws Exception {
        // Non-ancestor-managed dep whose ancestor DM version is a property expression
        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, """
                <project>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>existing</artifactId>
                      <version>1.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        var transitive = new DependenciesTui.DepEntry(
                "org.apache.maven.resolver", "maven-resolver-named-locks", "", "2.0.22", "compile", false);
        List<String> logs = new ArrayList<>();

        DependenciesReporter.fix(
                pomPath,
                List.of(),
                List.of(transitive),
                Map.of("org.apache.maven.resolver:maven-resolver-named-locks", "2.0.22"),
                Map.of("org.apache.maven.resolver:maven-resolver-named-locks", "${resolverVersion}"),
                Set.of(),
                logs::add);

        String result = Files.readString(pomPath);
        assertThat(result).contains("maven-resolver-named-locks");
        // Must write the property expression, NOT the resolved literal
        assertThat(result).contains("<version>${resolverVersion}</version>");
        assertThat(result).doesNotContain("<version>2.0.22</version>");
    }

    @Test
    void fixUsesLiteralWhenAncestorDMAlsoUsesLiteral(@TempDir Path tempDir) throws Exception {
        // Non-ancestor-managed dep whose ancestor DM version is a literal (no property)
        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, """
                <project>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>existing</artifactId>
                      <version>1.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        var transitive = new DependenciesTui.DepEntry("org.other", "plain-lib", "", "3.5", "compile", false);
        List<String> logs = new ArrayList<>();

        DependenciesReporter.fix(
                pomPath,
                List.of(),
                List.of(transitive),
                Map.of("org.other:plain-lib", "3.5"),
                // raw version is also a literal — no property expression
                Map.of("org.other:plain-lib", "3.5"),
                Set.of(),
                logs::add);

        String result = Files.readString(pomPath);
        assertThat(result).contains("plain-lib");
        assertThat(result).contains("3.5");
    }

    @Test
    void fixAncestorManagedTakesPrecedenceOverRawVersion(@TempDir Path tempDir) throws Exception {
        // When a dep is ancestor-managed, gaToRawVersion must be ignored — no <version> at all
        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, """
                <project>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>existing</artifactId>
                      <version>1.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        var transitive = new DependenciesTui.DepEntry("org.managed", "managed-lib", "", "4.0", "compile", false);
        List<String> logs = new ArrayList<>();

        DependenciesReporter.fix(
                pomPath,
                List.of(),
                List.of(transitive),
                Map.of("org.managed:managed-lib", "4.0"),
                // raw map also present but must be ignored for ancestor-managed deps
                Map.of("org.managed:managed-lib", "${managedVersion}"),
                Set.of("org.managed:managed-lib"),
                logs::add);

        String result = Files.readString(pomPath);
        assertThat(result).contains("managed-lib");
        // Neither the literal nor the property expression should appear — it's ancestor-managed
        assertThat(result).doesNotContain("<version>4.0</version>");
        assertThat(result).doesNotContain("<version>${managedVersion}</version>");
        assertThat(logs).anyMatch(l -> l.contains("version managed by ancestor"));
    }
}
