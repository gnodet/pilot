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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.maveniverse.domtrip.maven.AlignOptions;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

class AlignMojoTest {

    @Test
    void defaultActionIsReport() {
        var mojo = new AlignMojo();
        assertThat(mojo.action).isEqualTo("report");
    }

    @Test
    void rejectsInvalidAction() {
        var mojo = new AlignMojo();
        mojo.action = "bogus";
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Invalid action 'bogus'");
    }

    @Test
    void rejectsTuiAction() {
        var mojo = new AlignMojo();
        mojo.action = "tui";
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Invalid action 'tui'");
    }

    // --- buildOptions ---

    private static AlignOptions defaultAlignOptions() {
        return AlignOptions.builder()
                .versionStyle(AlignOptions.VersionStyle.INLINE)
                .versionSource(AlignOptions.VersionSource.LITERAL)
                .namingConvention(AlignOptions.PropertyNamingConvention.DOT_SUFFIX)
                .insertionOrdering(AlignOptions.InsertionOrdering.NONE)
                .build();
    }

    @Test
    void buildOptionsRejectsInvalidVersionStyle() {
        var mojo = new AlignMojo();
        mojo.versionStyle = "bogus";
        assertThatThrownBy(() -> mojo.buildOptions(defaultAlignOptions()))
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Invalid pilot.versionStyle");
    }

    @Test
    void buildOptionsRejectsInvalidVersionSource() {
        var mojo = new AlignMojo();
        mojo.versionSource = "bogus";
        assertThatThrownBy(() -> mojo.buildOptions(defaultAlignOptions()))
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Invalid pilot.versionSource");
    }

    @Test
    void buildOptionsRejectsInvalidNamingConvention() {
        var mojo = new AlignMojo();
        mojo.namingConvention = "bogus";
        assertThatThrownBy(() -> mojo.buildOptions(defaultAlignOptions()))
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Invalid pilot.namingConvention");
    }

    @Test
    void buildOptionsRejectsInvalidInsertionOrdering() {
        var mojo = new AlignMojo();
        mojo.insertionOrdering = "bogus";
        assertThatThrownBy(() -> mojo.buildOptions(defaultAlignOptions()))
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Invalid pilot.insertionOrdering");
    }

    @Test
    void buildOptionsAcceptsLowercaseValues() throws Exception {
        var mojo = new AlignMojo();
        mojo.versionStyle = "managed";
        mojo.versionSource = "property";
        mojo.namingConvention = "dot_suffix";
        mojo.insertionOrdering = "scope_then_alpha";
        AlignOptions opts = mojo.buildOptions(defaultAlignOptions());
        assertThat(opts.versionStyle()).isEqualTo(AlignOptions.VersionStyle.MANAGED);
        assertThat(opts.versionSource()).isEqualTo(AlignOptions.VersionSource.PROPERTY);
        assertThat(opts.namingConvention()).isEqualTo(AlignOptions.PropertyNamingConvention.DOT_SUFFIX);
        assertThat(opts.insertionOrdering()).isEqualTo(AlignOptions.InsertionOrdering.SCOPE_THEN_ALPHA);
    }
}
