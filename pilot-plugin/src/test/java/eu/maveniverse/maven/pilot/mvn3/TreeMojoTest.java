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

import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

class TreeMojoTest {

    @Test
    void defaultActionIsTui() {
        var mojo = new TreeMojo();
        assertThat(mojo.action).isEqualTo("tui");
    }

    @Test
    void rejectsCheckAction() {
        var mojo = new TreeMojo();
        mojo.action = "check";
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("check")
                .hasMessageContaining("not supported");
    }

    @Test
    void rejectsFixAction() {
        var mojo = new TreeMojo();
        mojo.action = "fix";
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("fix")
                .hasMessageContaining("not supported");
    }

    @Test
    void rejectsInvalidAction() {
        var mojo = new TreeMojo();
        mojo.action = "bogus";
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Invalid action 'bogus'");
    }

    // --- resolveAction / headless fallback ---

    @Test
    void resolveActionSwitchesTuiToReportWhenHeadless() {
        var mojo = new TreeMojo() {
            @Override
            boolean isHeadless() {
                return true;
            }
        };
        assertThat(mojo.action).isEqualTo("tui");

        mojo.resolveAction();

        assertThat(mojo.action)
                .as("tui must be rerouted to report in headless environments")
                .isEqualTo("report");
    }

    @Test
    void resolveActionPreservesReportWhenHeadless() {
        var mojo = new TreeMojo() {
            @Override
            boolean isHeadless() {
                return true;
            }
        };
        mojo.action = "report";
        mojo.resolveAction();
        assertThat(mojo.action).isEqualTo("report");
    }

    @Test
    void resolveActionPreservesTuiWhenInteractive() {
        var mojo = new TreeMojo() {
            @Override
            boolean isHeadless() {
                return false;
            }
        };
        mojo.resolveAction();
        assertThat(mojo.action).isEqualTo("tui");
    }

    @Test
    void isHeadlessTrueWhenNoConsole() {
        assertThat(System.console()).isNull();
        var mojo = new TreeMojo() {
            @Override
            boolean isHeadless() {
                return System.console() == null;
            }
        };
        assertThat(mojo.isHeadless()).isTrue();
    }

    @Test
    void acceptsValidActions() {
        for (String a : List.of("tui", "report")) {
            var mojo = new TreeMojo();
            mojo.action = a;
            assertThat(mojo.action).isEqualTo(a);
        }
    }
}
