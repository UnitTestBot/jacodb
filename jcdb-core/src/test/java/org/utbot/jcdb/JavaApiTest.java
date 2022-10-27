package org.utbot.jcdb;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;
import org.utbot.jcdb.api.JCDB;
import org.utbot.jcdb.api.JcClassOrInterface;
import org.utbot.jcdb.api.JcClasspath;
import org.utbot.jcdb.impl.index.Usages;
import org.utbot.jcdb.impl.performance.TakeMemoryDumpKt;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JavaApiTest {

    @Test
    public void createJcdb() throws ExecutionException, InterruptedException, IOException {
        System.out.println("Creating database");
        try (JCDB instance = JcdbKt.asyncJcdb(new JCDBSettings().installFeatures(Usages.INSTANCE)).get()) {
            System.out.println("Database is ready: " + instance);
        }
    }

    @Test
    public void createClasspath() throws ExecutionException, InterruptedException, IOException {
        System.out.println("Creating database");
        try (JCDB instance = JcdbKt.asyncJcdb(new JCDBSettings().installFeatures(Usages.INSTANCE)).get()) {
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
        try (JCDB instance = JcdbKt.asyncJcdb(new JCDBSettings().installFeatures(Usages.INSTANCE)).get()) {
            instance.asyncLoad(TakeMemoryDumpKt.getAllClasspath()).get();
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
