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

package org.jacodb.testing;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;
import org.utbot.jacodb.api.JcClassOrInterface;
import org.utbot.jacodb.api.JcClasspath;
import org.utbot.jacodb.api.JcDatabase;
import org.utbot.jacodb.impl.JacoDB;
import org.utbot.jacodb.impl.JcSettings;
import org.utbot.jacodb.impl.features.Usages;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static org.jacodb.testing.LibrariesMixinKt.getAllClasspath;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JavaApiTest {

    @Test
    public void createJcdb() throws ExecutionException, InterruptedException, IOException {
        System.out.println("Creating database");
        try (JcDatabase instance = JacoDB.async(new JcSettings().installFeatures(Usages.INSTANCE)).get()) {
            System.out.println("Database is ready: " + instance);
        }
    }

    @Test
    public void createClasspath() throws ExecutionException, InterruptedException, IOException {
        System.out.println("Creating database");
        try (JcDatabase instance = JacoDB.async(new JcSettings().installFeatures(Usages.INSTANCE)).get()) {
            try (JcClasspath classpath = instance.asyncClasspath(Lists.newArrayList()).get()) {
                JcClassOrInterface clazz = classpath.findClassOrNull("java.lang.String");
                assertNotNull(clazz);
                assertNotNull(classpath.asyncRefreshed(false).get());
            }
            System.out.println("Database is ready: " + instance);
        }
    }

    @Test
    public void jcdbOperations() throws ExecutionException, InterruptedException, IOException {
        System.out.println("Creating database");
        try (JcDatabase instance = JacoDB.async(new JcSettings().installFeatures(Usages.INSTANCE)).get()) {
            instance.asyncLoad(getAllClasspath()).get();
            System.out.println("asyncLoad finished");
            instance.asyncRefresh().get();
            System.out.println("asyncRefresh finished");
            instance.asyncRebuildFeatures().get();
            System.out.println("asyncRebuildFeatures finished");
            instance.asyncAwaitBackgroundJobs().get();
            System.out.println("asyncAwaitBackgroundJobs finished");
        }
    }
}
