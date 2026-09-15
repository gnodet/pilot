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

import java.io.File;
import java.nio.file.Files;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Print the raw POM XML as plain text.
 *
 * <p>Runs once per module in a multi-module reactor. For an interactive TUI with
 * syntax highlighting and effective POM comparison, use {@code pilot:pilot} instead.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * mvn pilot:pom
 * </pre>
 *
 * @since 0.1.0
 */
@Mojo(name = "pom", requiresProject = true, threadSafe = true)
public class PomMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            File pomFile = project.getFile();
            String rawPom = Files.readString(pomFile.toPath());
            getLog().info(rawPom);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to display POM: " + e.getMessage(), e);
        }
    }
}
