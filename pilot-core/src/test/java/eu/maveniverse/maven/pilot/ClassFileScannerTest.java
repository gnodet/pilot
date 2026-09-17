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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClassFileScannerTest {

    @Test
    void scanDirectoryFindsClasses() throws Exception {
        Path testClasses = Path.of("target/test-classes");
        Assumptions.assumeTrue(
                Files.isDirectory(testClasses), "target/test-classes not found — skipping bytecode scan test");

        ClassFileScanner.ScanResult result = ClassFileScanner.scanDirectory(testClasses);

        // Should find references from all test classes
        assertThat(result.referencedClasses()).isNotEmpty().contains("java.lang.Object");
    }

    @Test
    void scanDirectoryFindsMemberReferences() throws Exception {
        Path testClasses = Path.of("target/test-classes");
        Assumptions.assumeTrue(
                Files.isDirectory(testClasses), "target/test-classes not found — skipping bytecode scan test");

        ClassFileScanner.ScanResult result = ClassFileScanner.scanDirectory(testClasses);

        // Should find member-level references (e.g. method calls on Path, assertThat, etc.)
        assertThat(result.memberReferences()).isNotEmpty();
        // This test class calls Path.of(), so we should see it
        assertThat(result.referencedClasses()).contains("java.nio.file.Path");
    }

    @Test
    void scanEmptyDirectory(@TempDir Path tempDir) throws Exception {
        ClassFileScanner.ScanResult result = ClassFileScanner.scanDirectory(tempDir);
        assertThat(result.referencedClasses()).isEmpty();
        assertThat(result.memberReferences()).isEmpty();
    }

    @Test
    void referencedClassesFiltersArrayDescriptors() throws Exception {
        Path mainClasses = Path.of("target/classes");
        Assumptions.assumeTrue(
                Files.isDirectory(mainClasses), "target/classes not found — skipping bytecode scan test");

        ClassFileScanner.ScanResult result = ClassFileScanner.scanDirectory(mainClasses);

        // No class name should start with '[' (array descriptors should be filtered)
        assertThat(result.referencedClasses())
                .noneMatch(name -> name.startsWith("["))
                // No class name should contain '/' (should be dot-separated)
                .noneMatch(name -> name.contains("/"));
    }

    @Test
    void formatDescriptorHumanReadable() {
        assertThat(ClassFileScanner.formatDescriptor("()V")).isEqualTo("()");
        assertThat(ClassFileScanner.formatDescriptor("(Ljava/lang/String;)V")).isEqualTo("(String)");
        assertThat(ClassFileScanner.formatDescriptor("(Ljava/lang/String;I)Z")).isEqualTo("(String, int)");
        assertThat(ClassFileScanner.formatDescriptor("([Ljava/lang/Object;)V")).isEqualTo("(Object[])");
    }

    /**
     * Regression test for <a href="https://github.com/maveniverse/pilot/issues/158">issue #158</a>:
     * the bytecode scanner must detect annotation types used only as annotations (not appearing in
     * method/field descriptors or call sites).
     *
     * <p>Specifically verifies:
     * <ul>
     *   <li>Method-level annotation types are collected ({@code @Test} on methods in this class)</li>
     *   <li>Field-level annotation types are collected ({@link org.junit.jupiter.api.io.TempDir}
     *       on a field in {@link AnnotationFixture}) — tests {@code FieldVisitor.visitAnnotation}</li>
     *   <li>Annotation element {@code Class[]} literals are collected
     *       ({@link AnnotationFixture.NoopExtension} referenced as the value of {@code @ExtendWith}
     *       on {@link AnnotationFixture}) — tests {@code annotationScanner().visit(name, Type)} +
     *       {@code visitArray}</li>
     * </ul>
     */
    @Test
    void scanDetectsAnnotationOnlyDependencies() throws Exception {
        Path testClasses = Path.of("target/test-classes");
        Assumptions.assumeTrue(
                Files.isDirectory(testClasses), "target/test-classes not found — skipping bytecode scan test");

        ClassFileScanner.ScanResult result = ClassFileScanner.scanDirectory(testClasses);

        // @Test is a method-level annotation used in many test classes — must be detected
        assertThat(result.referencedClasses())
                .as("method-level annotation type @Test must be detected")
                .contains("org.junit.jupiter.api.Test");

        // @TempDir is used as a field annotation in AnnotationFixture — tests the field-annotation path
        assertThat(result.referencedClasses())
                .as("field-level annotation type @TempDir must be detected (regression: issue #158)")
                .contains("org.junit.jupiter.api.io.TempDir");

        // NoopExtension is a Class<?> literal in @ExtendWith(NoopExtension.class) on AnnotationFixture
        // — tests the annotation element-value scanning path (visitArray + visit(name, Type))
        assertThat(result.referencedClasses())
                .as("Class[] annotation element value must be detected (regression: issue #158)")
                .contains("eu.maveniverse.maven.pilot.AnnotationFixture$NoopExtension");
    }
}
