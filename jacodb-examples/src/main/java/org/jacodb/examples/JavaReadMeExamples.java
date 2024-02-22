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

package org.jacodb.examples;

import org.jacodb.api.jvm.*;
import org.jacodb.api.jvm.ext.JcClasses;
import org.jacodb.impl.JacoDB;
import org.jacodb.impl.JcSettings;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.util.Arrays;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class JavaReadMeExamples {

    private static JcClasspath classpath = null;
    private static JcDatabase db = null;

    private static File lib1 = new File("1");
    private static File lib2 = new File("2");
    private static File buildDir = new File("3");

    public static void getStringFields() throws Exception {
        JcDatabase database = JacoDB.async(new JcSettings().useProcessJavaRuntime()).get();
        JcClassOrInterface clazz = database.asyncClasspath(emptyList()).get().findClassOrNull("java.lang.String");
        System.out.println(clazz.getDeclaredFields());
    }

    public static MethodNode findNormalDistribution() throws Exception {
        File commonsMath32 = new File("commons-math3-3.2.jar");
        File commonsMath36 = new File("commons-math3-3.6.1.jar");
        File buildDir = new File("my-project/build/classes/java/main");
        JcDatabase database = JacoDB.async(
                new JcSettings()
                        .useProcessJavaRuntime()
                        .persistent("/tmp/compilation-db/" + System.currentTimeMillis()) // persist data
        ).get();

        // Let's load these three bytecode locations
        database.asyncLoad(Arrays.asList(commonsMath32, commonsMath36, buildDir));

        // This method just refreshes the libraries inside the database. If there are any changes in libs then
        // the database updates data with the new results.
        database.asyncLoad(singletonList(buildDir));

        // Let's assume that we want to get bytecode info only for `commons-math3` version 3.2.
        JcClassOrInterface jcClass = database.asyncClasspath(Arrays.asList(commonsMath32, buildDir))
                .get().findClassOrNull("org.apache.commons.math3.distribution.NormalDistribution");
        System.out.println(jcClass.getDeclaredMethods().size());
        System.out.println(jcClass.getAnnotations().size());
        System.out.println(JcClasses.getConstructors(jcClass).size());

        // At this point the database read the method bytecode and return the result.
        return jcClass.getDeclaredMethods().get(0).asmNode();
    }

    public static void watchFileChanges() throws Exception {
        JcDatabase database = JacoDB.async(new JcSettings()
                .watchFileSystem()
                .useProcessJavaRuntime()
                .loadByteCode(Arrays.asList(lib1, buildDir))
                .persistent("", false)).get();
    }

    public static class A<T> {
        T x = null;
    }

    public static class B extends A<String> {
    }

    public static void typesSubstitution() {
        JcClassType b = (JcClassType) classpath.findTypeOrNull("org.jacodb.examples.JavaReadMeExamples.B");
        JcType xType = b.getFields()
                .stream()
                .filter(it -> "x".equals(it.getName()))
                .findFirst().get().getType();
        JcClassType stringType = (JcClassType) classpath.findTypeOrNull("java.lang.String");
        System.out.println(xType.equals(stringType)); // will print `true`
    }

    public static void refresh() throws Exception {
        JcDatabase database = JacoDB.async(
                new JcSettings()
                        .watchFileSystem()
                        .useProcessJavaRuntime()
                        .loadByteCode(Arrays.asList(lib1, buildDir))
                        .persistent("...")
        ).get();

        JcClasspath cp = database.asyncClasspath(singletonList(buildDir)).get();
        database.asyncRefresh().get(); // does not affect cp classes

        JcClasspath cp1 = database.asyncClasspath(singletonList(buildDir)).get(); // will use new version of compiled results in buildDir
    }

    public static void autoProcessing() throws Exception {
        JcDatabase database = JacoDB.async(
                new JcSettings()
                        .loadByteCode(Arrays.asList(lib1))
                        .persistent("...")
        ).get();

        JcClasspath cp = database.asyncClasspath(singletonList(buildDir)).get(); // database will automatically process buildDir
    }

    public static void threadSafe() throws Exception {
        JcDatabase db = JacoDB.async(new JcSettings()).get();

        new Thread(() -> {
            try {
                db.asyncLoad(Arrays.asList(lib1, lib2)).get();
            } catch (Exception e) {
                // should never happen
            }
        }).start();

        new Thread(() -> {
            // maybe created when lib2 or both are not loaded into database
            // but buildDir will be loaded anyway
            try {
                JcClasspath cp = db.asyncClasspath(singletonList(buildDir)).get();
            } catch (Exception e) {
                // should never happen
            }
        }).start();
    }
}
