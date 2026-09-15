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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.nio.file.Files;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PomMojoTest {

    @Test
    void printsRawPomContent(@TempDir java.nio.file.Path tmpDir) throws Exception {
        // Write a minimal POM file
        java.nio.file.Path pomFile = tmpDir.resolve("pom.xml");
        String content = "<project><modelVersion>4.0.0</modelVersion></project>";
        Files.writeString(pomFile, content);

        MavenProject project = new MavenProject();
        project.setFile(pomFile.toFile());

        PomMojo mojo = new PomMojo();
        MojoTestHelper.setField(mojo, "project", project);

        // execute() logs via getLog() — just verify it doesn't throw
        mojo.execute();
    }

    @Test
    void throwsWhenPomFileNotReadable() throws Exception {
        MavenProject project = new MavenProject();
        project.setFile(new File("/nonexistent/path/pom.xml"));

        PomMojo mojo = new PomMojo();
        MojoTestHelper.setField(mojo, "project", project);

        assertThatThrownBy(mojo::execute).isInstanceOf(MojoExecutionException.class);
    }
}
