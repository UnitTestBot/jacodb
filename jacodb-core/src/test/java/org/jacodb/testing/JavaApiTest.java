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

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import org.jacodb.api.jvm.JcClassOrInterface;
import org.jacodb.api.jvm.JcProject;
import org.jacodb.api.jvm.JcDatabase;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static org.jacodb.testing.LibrariesMixinKt.getAllClasspath;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JavaApiTest {

    private final Supplier<JcDatabase> db = Suppliers.memoize(() -> {
        try {
            return BaseTestKt.getGlobalDb();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    });

    @Test
    public void createJcdb() {
        System.out.println("Creating database");
        JcDatabase database = db.get();
        assertNotNull(database);
        System.out.println("Database is ready: " + database);
    }

    @Test
    public void createClasspath() throws ExecutionException, InterruptedException, IOException {
        System.out.println("Creating database");
        JcDatabase instance = db.get();
        try (JcProject classpath = instance.asyncClasspath(Lists.newArrayList()).get()) {
            JcClassOrInterface clazz = classpath.findClassOrNull("java.lang.String");
            assertNotNull(clazz);
            assertNotNull(classpath.asyncRefreshed(false).get());
        }
        System.out.println("Database is ready: " + instance);
    }

    @Test
    public void jcdbOperations() throws ExecutionException, InterruptedException, IOException {
        System.out.println("Creating database");
        JcDatabase instance = db.get();
        instance.asyncLoad(getAllClasspath()).get();
        System.out.println("asyncLoad finished");
        instance.asyncRefresh().get();
        System.out.println("asyncRefresh finished");
        instance.asyncRebuildFeatures().get();
        System.out.println("asyncRebuildFeatures finished");
        instance.asyncAwaitBackgroundJobs().get();
        System.out.println("asyncAwaitBackgroundJobs finished");
        instance.getFeatures();
    }
}
