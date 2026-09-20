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

import eu.maveniverse.domtrip.Document;
import eu.maveniverse.domtrip.maven.AlignOptions;
import eu.maveniverse.domtrip.maven.PomEditor;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared utility for locating reactor-local parent POMs with dependency management.
 */
public final class AlignHelper {

    private static final Logger LOGGER = Logger.getLogger(AlignHelper.class.getName());

    private AlignHelper() {}

    /**
     * Walk the parent chain to find the reactor-local ancestor with the most
     * {@code <dependencyManagement>} entries (i.e. where the project conventionally
     * centralises managed dependencies). Falls back to the direct reactor parent if no
     * ancestor has dependency management at all.
     *
     * <p>When multiple ancestors tie on score the highest one in the hierarchy wins,
     * since the goal is to consolidate management as close to the root as possible.</p>
     *
     * @param proj the project to find a parent for
     * @param reactorProjects all projects in the reactor
     * @return parent POM info, or {@code null} if the project has no reactor-local parent
     */
    public static AlignTui.ParentPomInfo findParentPomInfo(PilotProject proj, List<PilotProject> reactorProjects) {
        Set<String> reactorPaths = new HashSet<>();
        for (PilotProject p : reactorProjects) {
            reactorPaths.add(p.pomPath.toString());
        }

        PilotProject directParent = null;
        PilotProject bestCandidate = null;
        int bestScore = -1;

        PilotProject current = proj.parent;
        while (current != null && current.pomPath != null) {
            if (!reactorPaths.contains(current.pomPath.toString())) {
                break;
            }
            if (directParent == null) {
                directParent = current;
            }
            int score = current.originalManagedDependencies != null ? current.originalManagedDependencies.size() : 0;
            // Greater-or-equal: on a tie the higher (later in walk) ancestor replaces the lower one
            if (score >= bestScore) {
                bestScore = score;
                bestCandidate = current;
            }
            current = current.parent;
        }

        if (bestCandidate != null) {
            return buildParentPomInfo(bestCandidate);
        }
        // No reactor-local parent at all
        if (directParent != null) {
            return buildParentPomInfo(directParent);
        }
        return null;
    }

    private static AlignTui.ParentPomInfo buildParentPomInfo(PilotProject parent) {
        String parentPomPath = parent.pomPath.toString();
        String parentGav = parent.gav();
        try {
            PomEditor parentEditor = new PomEditor(Document.of(Files.readString(parent.pomPath)));
            AlignOptions parentOptions = parentEditor.dependencies().detectConventions();
            return new AlignTui.ParentPomInfo(parentPomPath, parentGav, parentOptions);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to read parent POM at {0} ({1}); using defaults", new Object[] {
                parentPomPath, parentGav
            });
            return new AlignTui.ParentPomInfo(parentPomPath, parentGav, AlignOptions.defaults());
        }
    }
}
