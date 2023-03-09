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

package org.jacodb.analysis.codegen

import mu.KotlinLogging
import org.jacodb.analysis.codegen.language.base.AnalysisVulnerabilityProvider
import org.jacodb.analysis.codegen.language.base.TargetLanguage
import org.jacodb.analysis.codegen.language.base.VulnerabilityInstance
import java.nio.file.Paths
import java.util.*
import java.util.Collections.min
import kotlin.io.path.notExists
import kotlin.io.path.useDirectoryEntries
import kotlin.random.Random

private val logger = KotlinLogging.logger { }

class AccessibilityCache(n: Int, private val graph: Map<Int, Set<Int>>) {
    private val used = Array(n) { 0 }
    private var currentWave = 0
    val badQuery = -1 to -1
    val badPath = listOf(-1)
    private var lastQuery = badQuery
    private var lastQueryPath = mutableListOf<Int>()

    private fun dfs(u: Int, target: Int): Boolean {
        used[u] = currentWave
        if (u == target) {
            lastQueryPath = mutableListOf(u)
            return true
        }

        for (v in graph.getOrDefault(u, emptySet())) {
            if (used[v] != currentWave && dfs(v, target)) {
                lastQueryPath.add(u)
                return true
            }
        }

        return false
    }

    fun isAccessible(u: Int, v: Int): Boolean {
        ++currentWave
        lastQuery = badQuery
        if (dfs(u, v)) {
            lastQueryPath.reverse()
            lastQuery = u to v
            return true
        }
        return false
    }


    fun getAccessPath(u: Int, v: Int): List<Int> {
        if (lastQuery == u to v || isAccessible(u, v))
            return lastQueryPath

        return badPath
    }
}

// primary languages - java, cpp.
// secondary - python, go, js, kotlin, etc.

// language features that are not supported and (most probably) will never be
// 1. package-private/internal/sealed modifiers - as they are not present in all languages
// 2. cpp private inheritance, multiple inheritance - as this complicates code model way too much
// 3. cpp constructor initialization features - complicates ast too much


// 1. assignment + in vulnerability transit do dispatch
//
// 2. refactor part with expressions in site
// 3. correct styling
// commentaries

// statistics dump - vulnerability id, source, sink, path

// optionally - conditional paths
// optionally - kotlinx serialization for hierarchy

// все не что указано в тразите - идет в дефолт валуе. иначе берется из параметра.
// так как рендеринг в конце - все будет ок
// путь задаеися глобальным стейтом на доменах?))))
// НЕТ! так как у нас проблема может быть только на одном путе, только пройдя его полностью - то нам не нужно эмулировать
// диспатчинг в ифдс, он сам найдет только то, что нужно, а вот верификация будет за юсвм!!

// we will differ static class and instance class like in kotlin.
// for Java and CPP this means that in case of static elements we will create static duplicate

// TODO PROTECTED - as it is relevant only when we will have inheritance
// todo assert that field is accessible to code value type
// todo field and method overloading implementation
// todo static initializer?
// todo assert initial value type is assignable to type
// todo flip inner element nullability?
// todo or one should override another if this is method
// todo returnStatement
// TODO tests - generate by hands some tests, 100% cover must be
// TODO c++ implementation
// TODO enums
// TODO complex representations - list of other
// TODO ifs, cycles, arrays, assignments, lambda invokes, returns
// TODO analyses aware constructors
// TODO interfaces - DAG, abstract classes - graph DFS, implementation of interfaces - zip dag to tree
// TODO method implementation - paths in tree divisino in half, random points
// TODO each call in graph path - may be virtual invoke, provide arguments on which this call should via generated hierarchy tree
// TODO generate data flow - first only simple returns and initial values in fields + tree types generation
// TODO then do complex reassignments with conditions
// TODO after that we can think of exceptions, lambdas, generics
// TODO final boss will be unsoundiness - reflection and jni
// TODO protected modifiers
// TODO type and/or signature correlation, covariance/contrvariance - this should be part of overloading
// TODO connecting already defined code
// TODO generating IFDS false positives to test USVM
// TODO verifications - all interfaces methods are implemented, no collisions, all abstract methods are defined in non-abstract classes etc
// TODO per language features to enable/disable some generations

// can be added with minimal work, but i do not see usefulness in foreseeable future
// TODO extension methods? - should be functions/methods with additional mark
// TODO annotations? - tbh i dunno for what right now it might be required

// hard and involves much design and refactoring
// TODO accessors? - in some sense this should be method with some field reference. but not all languages support this, so skip for now
// TODO generics? templates? - oh fuk this is hard tbh


fun main(args: Array<String>) {
    assert(args.size in 5..6) {
        "vertices:Int edges:Int vulnerabilities:Int pathToProjectDir:String targetLanguage:String [clearTargetDir: Boolean]"
    }
    val n = args[0].toInt()
    val m = args[1].toInt()
    val k = args[2].toInt()

    assert(n in 2 until 1000) { "currently big graphs not supported just in case" }
    assert(m in 1 until 1000000) { "though we permit duplicated edges, do not overflow graph too much" }
    assert(k in 0 until min(listOf(255, n, m)))

    val projectPath = Paths.get(args[3]).normalize()

    assert(projectPath.notExists() || projectPath.useDirectoryEntries { it.none() }) { "Provide path to directory which either does not exists or empty" }

    val targetLanguageString = args[4]
    val targetLanguageService = ServiceLoader.load(TargetLanguage::class.java)
    val targetLanguage = targetLanguageService.single { it.javaClass.simpleName == targetLanguageString }

    val vulnerabilityProviderService = ServiceLoader.load(AnalysisVulnerabilityProvider::class.java)
    val vulnerabilityProviders = mutableListOf<AnalysisVulnerabilityProvider>()

    for (analysis in vulnerabilityProviderService) {
        if (analysis.isApplicable(targetLanguage)) {
            vulnerabilityProviders.add(analysis)
        }
    }

    logger.info { "analyses summary: " }

    val randomSeed = arrayOf(n, m, k).contentHashCode()
    val randomer = Random(randomSeed)

    logger.info { "debug seed: $randomSeed" }

    // 1. как-то задавать анализы, для которых генерируем граф
    // Это говно должено реализовывать интерфейс какой-нибудь, который должен быть положен рядом-в класспас,
    // мы его каким-нибудь рефлекшеном находим и радуемся
    // 2. как-то задавать файл путь в который че генерим
    // наверное хочу задавать путь до папки, в которую нужно класть проект. и да, туда сразу внутренности архива
    // 3. как-то завязать кеш дфс
    // просто реально держит ссылку на граф и просто мапчик да-нет и все
    // 6. нужна презентация реальных функций и че она умеет
    // функция - название, параметры, у параметра тип, и пишется он явно в формате джавы(другой язык мб потом)
    // также функция имеет понимание, в каком порядке какие вызовы в ней будут делаться, и какие у каждого вызова параметры и в каком порядке
    // изменения каждого параметра производятся перед самым вызовом в пути, тем самым гарантируем, что там не будут важны предыдущие значения
    // также из этого следует, что мы не можем двумя разными способами вызываться в одном методе.
    // !!!проблема - мы практически точно будет генерировать бесконечные рекурсии при любом цикле!!!
    // то есть мы гарантированно должны быть ациклически! для этого будет использоваться стек, в который будет положен какое ребро нужно вызвать
    // на данный момент мы поддерживаем явный путь в графе, но никак не "исполнение"(то есть как бы историю работы дфс).
    // 4. как-то сделать реализацию vulnerabilities итдитп
    // ну наверное ему нужен стартовая вершина, конечная, естественное весь путь, также функциональная репрезентация каждого говна,
    // и каждый такой анализ дожен по этому путь пройтись и сам что-то сделать так, чтобы ничего не сломать остальным

    // 5. сделать дампалку итогового состояния функций через жопу
    // просто интерфейс, который принимает функцию из репрезентации и путь, куда это говно надо написать. Дальше уже разбирается сама.
    // их тоже можно искать сервисом

    val fullClear = args.getOrElse(5) { "false" }.toBooleanStrict()

    targetLanguage.unzipTemplateProject(projectPath, fullClear)

    val graph = mutableMapOf<Int, MutableSet<Int>>()

    var i = 0
    while(i < m) {
        val u = randomer.nextInt(n)
        val v = randomer.nextInt(n)

        if (u != v) {
            // TODO loops v->v?
            graph.getOrPut(u) { mutableSetOf() }.add(v)
            i++
        }
    }

    val accessibilityCache = AccessibilityCache(n, graph)
    val codeRepresentation = CodeRepresentation(targetLanguage)
    val generatedVulnerabilitiesList = mutableListOf<VulnerabilityInstance>()

    i = 0
    while(i < k) {
        val u = randomer.nextInt(n)
        val v = randomer.nextInt(n)
        val vulnerabilityIndex = randomer.nextInt(vulnerabilityProviders.size)
        val vulnerabilityProvider = vulnerabilityProviders[vulnerabilityIndex]

        if (accessibilityCache.isAccessible(u, v)) {
            val path = accessibilityCache.getAccessPath(u, v)
            val instance = vulnerabilityProvider.provideInstance(codeRepresentation) {
                createSource(u)
                for (j in 0 until path.lastIndex) {
                    val startOfEdge = path[j]
                    val endOfEdge = path[j + 1]
                    mutateVulnerability(startOfEdge, endOfEdge)
                    transitVulnerability(startOfEdge, endOfEdge)
                }
                createSink(v)
            }
            generatedVulnerabilitiesList.add(instance)
            i++
        }
    }

    codeRepresentation.dumpTo(projectPath)
}