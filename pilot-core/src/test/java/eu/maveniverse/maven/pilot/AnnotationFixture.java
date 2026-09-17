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

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.io.TempDir;

/**
 * Fixture class used by {@link ClassFileScannerTest} to verify that the bytecode scanner
 * correctly detects annotation types used exclusively through annotations (not in method/field
 * descriptors or call sites).
 *
 * <p>This class uses:
 * <ul>
 *   <li>{@link ExtendWith} as a <em>class-level</em> annotation whose {@code value()} is a
 *       {@code Class[]} literal referencing {@link NoopExtension} — exercises the annotation
 *       element-value scanning path ({@code annotationScanner().visit(name, Type)} +
 *       {@code visitArray})</li>
 *   <li>{@link TempDir} as a <em>field-level</em> annotation — exercises field-annotation
 *       scanning (the {@code FieldVisitor.visitAnnotation} path)</li>
 * </ul>
 *
 * <p>Neither annotation type appears in any method/field descriptor or call site, so without the
 * fix they would be missed by the scanner.
 */
@SuppressWarnings("unused")
@ExtendWith(AnnotationFixture.NoopExtension.class)
class AnnotationFixture {

    /** No-op extension used as the {@code Class[]} literal value in {@code @ExtendWith}. */
    public static class NoopExtension implements Extension {}

    /** Field whose only annotation is {@link TempDir} — tests field-annotation path. */
    @TempDir
    java.nio.file.Path tempDir;
}
