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
package eu.maveniverse.maven.pilot.mvn3;

import eu.maveniverse.maven.pilot.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;

/**
 * Dependency analysis: CI-friendly report/check/fix modes.
 *
 * <p>Three actions via {@code -Dpilot.action}:</p>
 * <ul>
 *   <li><b>report</b> (default) — prints unused declared and used transitive dependencies without failing</li>
 *   <li><b>check</b> — reports issues and fails the build if any are found</li>
 *   <li><b>fix</b> — removes unused declared and adds used transitive dependencies to the POM</li>
 * </ul>
 *
 * <p>Runs once per module in a multi-module reactor. For an interactive TUI,
 * use {@code pilot:pilot} instead.</p>
 *
 * <p>When the project has been compiled ({@code target/classes} exists), performs bytecode-level
 * analysis to determine which dependencies are actually referenced in code.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * mvn compile pilot:dependencies
 * mvn compile pilot:dependencies -Dpilot.action=check
 * mvn compile pilot:dependencies -Dpilot.action=fix
 * </pre>
 *
 * @since 0.1.0
 */
@Mojo(name = "dependencies", requiresProject = true, threadSafe = true)
public class DependenciesMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
    private RepositorySystemSession repoSession;

    @Parameter(property = "pilot.action", defaultValue = "report")
    String action = "report";

    @Parameter
    private List<String> runtimeArtifacts;

    @Parameter
    private List<String> annotationOnlyArtifacts;

    @Parameter
    private Map<String, String> reflectionLoadedClasses;

    @Parameter
    private List<String> ignoredUnusedDeclared;

    @Parameter
    private List<String> ignoredUsedTransitive;

    private final RepositorySystem repoSystem;

    @Inject
    DependenciesMojo(RepositorySystem repoSystem) {
        this.repoSystem = repoSystem;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!"report".equals(action) && !"check".equals(action) && !"fix".equals(action)) {
            throw new MojoExecutionException("Invalid action '" + action + "'. Use 'report', 'check', or 'fix'.");
        }
        try {
            executeForProject(project);
        } catch (MojoFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to analyze dependencies: " + e.getMessage(), e);
        }
    }

    private void executeForProject(MavenProject proj) throws Exception {
        if ("pom".equals(proj.getPackaging())) {
            getLog().debug("Skipping " + proj.getArtifactId() + " (pom packaging, no classes to analyse).");
            return;
        }
        Set<String> declaredGAs = new HashSet<>();
        List<DependenciesTui.DepEntry> declared = new ArrayList<>();
        for (Dependency dep : proj.getDependencies()) {
            DependenciesTui.addDeclaredEntry(
                    declaredGAs,
                    declared,
                    dep.getGroupId(),
                    dep.getArtifactId(),
                    dep.getClassifier(),
                    dep.getVersion(),
                    dep.getScope());
        }

        DependencyRequest depRequest = new DependencyRequest(MojoHelper.buildCollectRequest(proj), null);
        DependencyResult depResult = repoSystem.resolveDependencies(repoSession, depRequest);

        DependencyTreeModel depTree = MojoHelper.fromDependencyNode(depResult.getRoot());
        Set<String> transitiveGAs = new HashSet<>();
        List<DependenciesTui.DepEntry> transitive = new ArrayList<>();
        DependenciesTui.collectTransitive(depTree.root, declaredGAs, transitiveGAs, transitive);

        Map<String, File> gaToJar = new HashMap<>();
        Map<String, String> gaToVersion = new HashMap<>();
        for (ArtifactResult ar : depResult.getArtifactResults()) {
            var art = ar.getArtifact();
            if (art != null) {
                String classifier = art.getClassifier();
                String ga = (classifier != null && !classifier.isEmpty())
                        ? art.getGroupId() + ":" + art.getArtifactId() + ":" + classifier
                        : art.getGroupId() + ":" + art.getArtifactId();
                gaToVersion.put(ga, art.getVersion());
                if (art.getFile() != null && art.getFile().getName().endsWith(".jar")) {
                    gaToJar.put(ga, art.getFile());
                }
            }
        }

        // Build set of GAs already managed by an ancestor BOM/parent POM (not by this module itself).
        // When a transitive dependency is already version-managed by an ancestor, the fix action
        // should add it without a <version> element rather than hardcoding the resolved literal.
        Set<String> ancestorManagedGAs = buildAncestorManagedGAs(proj);

        Path classesDir = Path.of(proj.getBuild().getOutputDirectory());
        Path testClassesDir = Path.of(proj.getBuild().getTestOutputDirectory());

        DependencyUsageAnalyzer.AnalysisResult usage = null;
        if (Files.isDirectory(classesDir)) {

            ClassFileScanner.ScanResult mainScan = ClassFileScanner.scanDirectory(classesDir);
            ClassFileScanner.ScanResult testScan = Files.isDirectory(testClassesDir)
                    ? ClassFileScanner.scanDirectory(testClassesDir)
                    : new ClassFileScanner.ScanResult(Set.of(), Map.of());

            Map<String, String> classIndex = DependencyUsageAnalyzer.buildClassIndex(gaToJar);
            usage = buildAnalyzer()
                    .analyze(
                            mainScan.referencedClasses(),
                            testScan.referencedClasses(),
                            classIndex,
                            gaToJar,
                            declared,
                            transitive);
            applyUsageStatus(declared, transitive, usage);
        } else {
            throw new MojoExecutionException(
                    "target/classes not found — run 'mvn compile' before dependencies report/check/fix.");
        }

        executeNonInteractive(proj, declared, transitive, gaToVersion, ancestorManagedGAs);
    }

    /**
     * Computes the set of {@code groupId:artifactId} keys that are version-managed by an ancestor
     * POM or imported BOM, but <em>not</em> declared in this module's own {@code
     * <dependencyManagement>} section.
     *
     * <p>When a used-transitive dependency is already managed by an ancestor, the fix action
     * should add it to {@code <dependencies>} without a {@code <version>} element.</p>
     *
     * <p>Note: dependencies provided by a BOM that is <em>imported</em> in this module's own
     * {@code <dependencyManagement>} (via {@code <scope>import</scope>}) are classified as
     * ancestor-managed. This is intentional — those dependencies are version-pinned by the BOM
     * import, so omitting {@code <version>} is correct as long as the import remains present.</p>
     */
    static Set<String> buildAncestorManagedGAs(MavenProject proj) {
        // Collect GAs declared in this module's own <dependencyManagement> section (raw, unmerged).
        // getOriginalModel() returns only this POM's own declarations, without parent inheritance —
        // that is exactly what we want to exclude from the ancestor-managed set.
        Set<String> ownManagedGAs = new HashSet<>();
        if (proj.getOriginalModel().getDependencyManagement() != null
                && proj.getOriginalModel().getDependencyManagement().getDependencies() != null) {
            for (Dependency dep :
                    proj.getOriginalModel().getDependencyManagement().getDependencies()) {
                String classifier = dep.getClassifier();
                String ga = (classifier != null && !classifier.isEmpty())
                        ? dep.getGroupId() + ":" + dep.getArtifactId() + ":" + classifier
                        : dep.getGroupId() + ":" + dep.getArtifactId();
                ownManagedGAs.add(ga);
            }
        }

        // getManagedVersionMap() returns the full effective managed-version map (own + inherited),
        // with keys in the form "groupId:artifactId:type[:classifier]".
        // Any entry not covered by this module's own DM section is ancestor-managed.
        Set<String> ancestorManagedGAs = new HashSet<>();
        for (String artifactKey : proj.getManagedVersionMap().keySet()) {
            // Artifact key format: groupId:artifactId:type[:classifier]
            String[] parts = artifactKey.split(":");
            if (parts.length < 3) continue;
            String groupId = parts[0];
            String artifactId = parts[1];
            // type is parts[2]; classifier is parts[3] if present
            String classifier = parts.length > 3 ? parts[3] : null;
            String ga = (classifier != null && !classifier.isEmpty())
                    ? groupId + ":" + artifactId + ":" + classifier
                    : groupId + ":" + artifactId;
            if (!ownManagedGAs.contains(ga)) {
                ancestorManagedGAs.add(ga);
            }
        }
        return ancestorManagedGAs;
    }

    void executeNonInteractive(
            MavenProject proj,
            List<DependenciesTui.DepEntry> declared,
            List<DependenciesTui.DepEntry> transitive,
            Map<String, String> gaToVersion)
            throws Exception {
        executeNonInteractive(proj, declared, transitive, gaToVersion, Set.of());
    }

    void executeNonInteractive(
            MavenProject proj,
            List<DependenciesTui.DepEntry> declared,
            List<DependenciesTui.DepEntry> transitive,
            Map<String, String> gaToVersion,
            Set<String> ancestorManagedGAs)
            throws Exception {
        List<DependenciesTui.DepEntry> unusedDeclared = new ArrayList<>();
        for (var dep : declared) {
            if (dep.usageStatus == DependencyUsageAnalyzer.UsageStatus.UNUSED) {
                unusedDeclared.add(dep);
            }
        }

        List<DependenciesTui.DepEntry> usedTransitive = new ArrayList<>();
        for (var dep : transitive) {
            if (dep.usageStatus == DependencyUsageAnalyzer.UsageStatus.USED) {
                usedTransitive.add(dep);
            }
        }

        Set<String> ignoredUnused = buildIgnoreSet(ignoredUnusedDeclared);
        Set<String> ignoredTransitive = buildIgnoreSet(ignoredUsedTransitive);
        unusedDeclared.removeIf(dep -> DependencyUsageAnalyzer.matchesArtifactPattern(dep.ga(), ignoredUnused));
        usedTransitive.removeIf(dep -> DependencyUsageAnalyzer.matchesArtifactPattern(dep.ga(), ignoredTransitive));

        if (unusedDeclared.isEmpty() && usedTransitive.isEmpty()) {
            getLog().info("No dependency issues found.");
            return;
        }

        switch (action) {
            case "fix" ->
                DependenciesReporter.fix(
                        proj.getFile().toPath(),
                        unusedDeclared,
                        usedTransitive,
                        gaToVersion,
                        ancestorManagedGAs,
                        getLog()::info);
            case "report" -> getLog().warn(DependenciesReporter.formatFindings(unusedDeclared, usedTransitive));
            default ->
                throw new MojoFailureException(DependenciesReporter.formatCheckFailure(unusedDeclared, usedTransitive));
        }
    }

    private void applyUsageStatus(
            List<DependenciesTui.DepEntry> declared,
            List<DependenciesTui.DepEntry> transitive,
            DependencyUsageAnalyzer.AnalysisResult usage) {
        for (var dep : declared) {
            dep.usageStatus =
                    usage.declaredUsage().getOrDefault(dep.ga(), DependencyUsageAnalyzer.UsageStatus.UNDETERMINED);
        }
        for (var dep : transitive) {
            dep.usageStatus =
                    usage.transitiveUsage().getOrDefault(dep.ga(), DependencyUsageAnalyzer.UsageStatus.UNDETERMINED);
        }
    }

    DependencyUsageAnalyzer buildAnalyzer() {
        DependencyUsageAnalyzer.Builder builder = DependencyUsageAnalyzer.builder();
        if (runtimeArtifacts != null && !runtimeArtifacts.isEmpty()) {
            builder.runtimeArtifacts(new HashSet<>(runtimeArtifacts));
        }
        if (annotationOnlyArtifacts != null && !annotationOnlyArtifacts.isEmpty()) {
            builder.annotationOnlyArtifacts(new HashSet<>(annotationOnlyArtifacts));
        }
        if (reflectionLoadedClasses != null && !reflectionLoadedClasses.isEmpty()) {
            Map<String, List<String>> parsed = new HashMap<>();
            for (var entry : reflectionLoadedClasses.entrySet()) {
                parsed.put(entry.getKey(), List.of(entry.getValue().split(",")));
            }
            builder.reflectionLoadedClasses(parsed);
        }
        return builder.build();
    }

    static Set<String> buildIgnoreSet(List<String> patterns) {
        return patterns != null && !patterns.isEmpty() ? new HashSet<>(patterns) : Set.of();
    }
}
