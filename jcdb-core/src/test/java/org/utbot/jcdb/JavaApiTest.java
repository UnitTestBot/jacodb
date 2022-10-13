package org.utbot.jcdb;

import org.junit.jupiter.api.Test;
import org.utbot.jcdb.api.JCDB;
import org.utbot.jcdb.impl.index.Usages;

import java.util.concurrent.ExecutionException;

public class JavaApiTest {

    @Test
    public void createJcdb() throws ExecutionException, InterruptedException {
        System.out.println("Starting create database");
        JCDB instance = JcdbKt.futureJcdb(new JCDBSettings().installFeatures(Usages.INSTANCE)).get();
        System.out.println("Database is ready: " + instance);
    }
}
