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

import eu.maveniverse.maven.pilot.DependencyTreeModel;
import org.junit.jupiter.api.Test;

class TreeMojoTest {

    // --- formatNode ---

    @Test
    void formatNodeEmitsExtensionWhenPresent() {
        var mojo = new TreeMojo();
        var node = new DependencyTreeModel.TreeNode("com.example", "myapp", "", "war", "1.0", "compile", false, 1);
        assertThat(mojo.formatNode(node)).isEqualTo("com.example:myapp:war:1.0");
    }

    @Test
    void formatNodeFallsBackToJarWhenExtensionEmpty() {
        var mojo = new TreeMojo();
        var node = new DependencyTreeModel.TreeNode("com.example", "myapp", "", "", "1.0", "compile", false, 1);
        assertThat(mojo.formatNode(node)).isEqualTo("com.example:myapp:jar:1.0");
    }

    @Test
    void formatNodeIncludesClassifierBetweenTypeAndVersion() {
        var mojo = new TreeMojo();
        var node = new DependencyTreeModel.TreeNode("org.foo", "bar", "tests", "jar", "1.0", "test", false, 1);
        assertThat(mojo.formatNode(node)).isEqualTo("org.foo:bar:jar:tests:1.0:test");
    }

    @Test
    void formatNodeSuppressesCompileScope() {
        var mojo = new TreeMojo();
        var node = new DependencyTreeModel.TreeNode("com.example", "myapp", "", "jar", "1.0", "compile", false, 1);
        assertThat(mojo.formatNode(node)).isEqualTo("com.example:myapp:jar:1.0");
    }

    @Test
    void formatNodeIncludesNonCompileScope() {
        var mojo = new TreeMojo();
        var node = new DependencyTreeModel.TreeNode("com.example", "myapp", "", "jar", "1.0", "test", false, 1);
        assertThat(mojo.formatNode(node)).isEqualTo("com.example:myapp:jar:1.0:test");
    }
}
