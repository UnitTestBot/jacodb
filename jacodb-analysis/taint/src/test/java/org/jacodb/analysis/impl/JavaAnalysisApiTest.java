/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jacodb.analysis.impl;

import kotlin.time.DurationUnit;
import org.jacodb.analysis.graph.ApplicationGraphFactory;
import org.jacodb.api.JcClassOrInterface;
import org.jacodb.api.JcClasspath;
import org.jacodb.api.JcDatabase;
import org.jacodb.api.JcMethod;
import org.jacodb.api.analysis.JcApplicationGraph;
import org.jacodb.ifds.common.ChunkStrategiesKt;
import org.jacodb.ifds.common.JcAsyncIfdsFacade;
import org.jacodb.ifds.taint.TaintDomainFact;
import org.jacodb.ifds.taint.TaintVulnerability;
import org.jacodb.impl.JacoDB;
import org.jacodb.impl.JcSettings;
import org.jacodb.impl.features.InMemoryHierarchy;
import org.jacodb.impl.features.Usages;
import org.jacodb.testing.LibrariesMixinKt;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static kotlin.time.DurationKt.toDuration;
import static org.jacodb.analysis.graph.ApplicationGraphFactory.getDefaultBannedPackagePrefixes;
import static org.jacodb.ifds.taint.BuildersKt.asyncTaintIfdsFacade;


public class JavaAnalysisApiTest {
    private static JcClasspath classpath;

    @BeforeAll
    public static void initClasspath() throws ExecutionException, InterruptedException {
        JcDatabase instance = JacoDB.async(new JcSettings().installFeatures(Usages.INSTANCE, InMemoryHierarchy.INSTANCE)).get();
        classpath = instance.asyncClasspath(LibrariesMixinKt.getAllClasspath()).get();
    }

    @Test
    public void testJavaAnalysisApi() throws ExecutionException, InterruptedException {
        JcClassOrInterface analyzedClass = classpath.findClassOrNull("org.jacodb.testing.analysis.NpeExamples");
        Assertions.assertNotNull(analyzedClass);

        List<JcMethod> methodsToAnalyze = analyzedClass.getDeclaredMethods();
        JcApplicationGraph applicationGraph = ApplicationGraphFactory
            .newApplicationGraphForAnalysisAsync(classpath)
            .get();

        JcAsyncIfdsFacade<TaintDomainFact, TaintVulnerability> ifds = asyncTaintIfdsFacade(
            "ifds",
            applicationGraph.getClasspath(),
            applicationGraph,
            getDefaultBannedPackagePrefixes(),
            ChunkStrategiesKt.getClassChunkStrategy());
        ifds.runAnalysis(
            methodsToAnalyze,
            toDuration(30, DurationUnit.SECONDS))
            .get();
    }
}
