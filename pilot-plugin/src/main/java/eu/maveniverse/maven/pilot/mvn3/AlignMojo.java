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

import eu.maveniverse.domtrip.Document;
import eu.maveniverse.domtrip.maven.AlignOptions;
import eu.maveniverse.domtrip.maven.PomEditor;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Locale;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Detect and align dependency conventions across the POM — report/check/fix modes.
 *
 * <p>Three actions via {@code -Dpilot.action}:</p>
 * <ul>
 *   <li><b>report</b> (default) — prints a diff of what would change, exits 0</li>
 *   <li><b>check</b> — same as report but fails the build if any changes would be made</li>
 *   <li><b>fix</b> — applies alignment and writes the POM file in-place</li>
 * </ul>
 *
 * <p>Runs once per module in a multi-module reactor. For an interactive TUI,
 * use {@code pilot:pilot} instead.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * mvn pilot:align
 * mvn pilot:align -Dpilot.action=check
 * mvn pilot:align -Dpilot.action=fix
 * mvn pilot:align -Dpilot.action=fix -Dpilot.versionStyle=MANAGED -Dpilot.versionSource=PROPERTY
 * </pre>
 *
 * @since 0.2.0
 */
@Mojo(name = "align", requiresProject = true, threadSafe = true)
public class AlignMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Action to perform: {@code report} (default) prints a diff of alignment changes without
     * failing; {@code check} reports changes and fails the build if any are found;
     * {@code fix} applies the alignment and writes the POM in-place.
     */
    @Parameter(property = "pilot.action", defaultValue = "report")
    String action = "report";

    /**
     * Target version style for headless modes. One of: {@code INLINE}, {@code MANAGED}.
     * When omitted, the detected convention is used as default.
     */
    @Parameter(property = "pilot.versionStyle")
    String versionStyle;

    /**
     * Target version source for headless modes. One of: {@code LITERAL}, {@code PROPERTY}.
     * When omitted, the detected convention is used as default.
     */
    @Parameter(property = "pilot.versionSource")
    String versionSource;

    /**
     * Target property naming convention for headless modes.
     * One of: {@code DOT_SUFFIX}, {@code DASH_SUFFIX}, {@code CAMEL_CASE}, {@code DOT_PREFIX}.
     * When omitted, the detected convention is used as default.
     */
    @Parameter(property = "pilot.namingConvention")
    String namingConvention;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!("report".equals(action) || "check".equals(action) || "fix".equals(action))) {
            throw new MojoExecutionException("Invalid action '" + action + "'. Use 'report', 'check', or 'fix'.");
        }
        try {
            executeForProject(project);
        } catch (MojoExecutionException | MojoFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to run alignment: " + e.getMessage(), e);
        }
    }

    private void executeForProject(MavenProject proj) throws Exception {
        String pomPath = proj.getFile().getAbsolutePath();
        String pomContent = Files.readString(Path.of(pomPath));
        PomEditor editor = new PomEditor(Document.of(pomContent));
        AlignOptions detectedOptions = editor.dependencies().detectConventions();

        AlignOptions opts = buildOptions(detectedOptions);
        PomEditor applyEditor = new PomEditor(Document.of(pomContent));
        int count = applyEditor.dependencies().alignAllDependencies(opts);
        String aligned = applyEditor.toXml();

        if (count == 0) {
            getLog().info("No alignment changes needed.");
            return;
        }

        String diff = buildDiff(pomContent, aligned);
        switch (action) {
            case "report" -> getLog().info(diff);
            case "check" ->
                throw new MojoFailureException(
                        count + " alignment change(s) would be made. Run with -Dpilot.action=fix to apply.\n" + diff);
            case "fix" -> {
                getLog().info(diff);
                writePom(Path.of(pomPath), aligned);
                getLog().info("Applied " + count + " alignment change(s) to " + pomPath);
            }
            default -> throw new MojoExecutionException("Unexpected action: " + action);
        }
    }

    /**
     * Builds {@link AlignOptions} using detected conventions as defaults, overridden by
     * any explicitly provided parameters.
     */
    AlignOptions buildOptions(AlignOptions detected) throws MojoExecutionException {
        AlignOptions.VersionStyle style = detected.versionStyle();
        AlignOptions.VersionSource source = detected.versionSource();
        AlignOptions.PropertyNamingConvention naming = detected.namingConvention();

        if (versionStyle != null) {
            try {
                style = AlignOptions.VersionStyle.valueOf(versionStyle.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new MojoExecutionException(
                        "Invalid pilot.versionStyle '" + versionStyle + "'. Valid values: INLINE, MANAGED.");
            }
        }
        if (versionSource != null) {
            try {
                source = AlignOptions.VersionSource.valueOf(versionSource.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new MojoExecutionException(
                        "Invalid pilot.versionSource '" + versionSource + "'. Valid values: LITERAL, PROPERTY.");
            }
        }
        if (namingConvention != null) {
            try {
                naming = AlignOptions.PropertyNamingConvention.valueOf(namingConvention.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new MojoExecutionException("Invalid pilot.namingConvention '" + namingConvention
                        + "'. Valid values: DOT_SUFFIX, DASH_SUFFIX, CAMEL_CASE, DOT_PREFIX.");
            }
        }

        return AlignOptions.builder()
                .versionStyle(style)
                .versionSource(source)
                .namingConvention(naming)
                .build();
    }

    /**
     * Produces a simple before/after diff for logging.
     */
    private String buildDiff(String before, String after) {
        String[] beforeLines = before.split("\\r?\\n", -1);
        String[] afterLines = after.split("\\r?\\n", -1);
        StringBuilder sb = new StringBuilder();
        int max = Math.max(beforeLines.length, afterLines.length);
        for (int i = 0; i < max; i++) {
            String b = i < beforeLines.length ? beforeLines[i] : "";
            String a = i < afterLines.length ? afterLines[i] : "";
            if (!b.equals(a)) {
                sb.append("- ").append(b).append("\n");
                sb.append("+ ").append(a).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Writes {@code content} to {@code target} atomically (temp file + rename).
     * Preserves the target's POSIX permissions on the temporary file where supported.
     */
    private void writePom(Path target, String content) throws IOException {
        Path tmp = Files.createTempFile(target.getParent(), target.getFileName() + ".pilot-align-", ".tmp");
        try {
            var posixTarget = Files.getFileAttributeView(target, PosixFileAttributeView.class);
            if (posixTarget != null) {
                var posixTmp = Files.getFileAttributeView(tmp, PosixFileAttributeView.class);
                if (posixTmp != null) {
                    posixTmp.setPermissions(posixTarget.readAttributes().permissions());
                }
            }
            Files.writeString(tmp, content);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
