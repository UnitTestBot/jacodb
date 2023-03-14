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

package org.jacodb.analysis.codegen.language.impl

import net.lingala.zip4j.ZipFile
import org.jacodb.analysis.codegen.language.base.TargetLanguage
import org.jacodb.analysis.codegen.impossibleGraphId
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStreamWriter
import java.nio.file.Path
import kotlin.io.path.outputStream
import org.jacodb.analysis.codegen.ast.impl.*
import org.jacodb.analysis.codegen.ast.base.*
import org.jacodb.analysis.codegen.ast.base.expression.*
import org.jacodb.analysis.codegen.ast.base.expression.invocation.FunctionInvocationExpression
import org.jacodb.analysis.codegen.ast.base.expression.invocation.InvocationExpression
import org.jacodb.analysis.codegen.ast.base.expression.invocation.MethodInvocationExpression
import org.jacodb.analysis.codegen.ast.base.expression.invocation.ObjectCreationExpression
import org.jacodb.analysis.codegen.ast.base.presentation.callable.CallablePresentation
import org.jacodb.analysis.codegen.ast.base.presentation.callable.FunctionPresentation
import org.jacodb.analysis.codegen.ast.base.presentation.callable.local.LocalVariablePresentation
import org.jacodb.analysis.codegen.ast.base.presentation.type.ConstructorPresentation
import org.jacodb.analysis.codegen.ast.base.presentation.type.FieldPresentation
import org.jacodb.analysis.codegen.ast.base.presentation.type.MethodPresentation
import org.jacodb.analysis.codegen.ast.base.presentation.type.TypePresentation
import org.jacodb.analysis.codegen.ast.base.sites.CallSite
import org.jacodb.analysis.codegen.ast.base.sites.PreparationSite
import org.jacodb.analysis.codegen.ast.base.sites.Site
import org.jacodb.analysis.codegen.ast.base.sites.TerminationSite
import org.jacodb.analysis.codegen.ast.base.typeUsage.ArrayTypeUsage
import org.jacodb.analysis.codegen.ast.base.typeUsage.InstanceTypeUsage
import org.jacodb.analysis.codegen.ast.base.typeUsage.TypeUsage
import org.jacodb.analysis.codegen.currentDispatch
import org.jacodb.analysis.codegen.dispatcherQueueName
import java.io.FileOutputStream
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories

class JavaLanguage : TargetLanguage {
    private val realPrimitivesName = mutableMapOf<TargetLanguage.PredefinedPrimitives, String>()
    private val predefinedTypes = mutableMapOf<String, TypePresentation>()
    private val integer: TypePresentation
    private val arrayDeque: TypePresentation
    private val DOT = "."
    private val TAB = "\t"

    init {
        realPrimitivesName[TargetLanguage.PredefinedPrimitives.VOID] = TypePresentation.voidType.shortName
        predefinedTypes[TypePresentation.voidType.shortName] = TypePresentation.voidType

        integer = TypeImpl("Integer")
        realPrimitivesName[TargetLanguage.PredefinedPrimitives.INT] = integer.shortName
        predefinedTypes[integer.shortName] = integer

        // type argument cannot be primitive
        arrayDeque = TypeImpl("ArrayDeque<Integer>")
        arrayDeque.createMethod(impossibleGraphId, name = "add", parameters = listOf(integer.instanceType to "e"))
        arrayDeque.createMethod(impossibleGraphId, name = "remove", returnType = integer.instanceType)
        predefinedTypes[arrayDeque.shortName] = arrayDeque
    }

    override fun dispatch(callable: CallablePresentation) {
        val integerType = getPredefinedPrimitive(TargetLanguage.PredefinedPrimitives.INT)!!
        val integerUsage = integerType.instanceType
        val current = callable.getOrCreateLocalVariable(currentDispatch, integerUsage, object :
            DirectStringSubstitution {
            override val substitution: String = "-1"
            override val evaluatedType: TypeUsage = integerUsage
        })
        val dispatcherQueue = callable.getLocal(dispatcherQueueName)!!
        val dispatcherReference = dispatcherQueue.reference
        val removeMethod = arrayDeque.getMethods("remove").single()
        val methodInvocation = MethodInvocationExpressionImpl(removeMethod, dispatcherReference)
        val assignment = AssignmentExpressionImpl(current.reference, methodInvocation)
        callable.preparationSite.addAfter(assignment)
    }

    override fun resolveProjectPathToSources(projectPath: Path): Path {
        val relativeJavaSourceSetPath =
            "UtBotTemplateForIfdsSyntheticTests\\src\\main\\java\\org\\utbot\\ifds\\synthetic\\tests"

        return projectPath.resolve(relativeJavaSourceSetPath.replace('\\', File.separatorChar))
    }

    override fun projectZipInResourcesName(): String {
        return "UtBotTemplateForIfdsSyntheticTests.zip"
    }

    override fun getPredefinedType(name: String): TypePresentation? {
        return predefinedTypes[name]
    }

    override fun getPredefinedPrimitive(primitive: TargetLanguage.PredefinedPrimitives): TypePresentation? {
        return predefinedTypes[realPrimitivesName[primitive]]
    }

    private var fileWriter: OutputStreamWriter? = null

    private fun inFile(fileName: String, pathToSourcesDir: Path, block: () -> Unit) {
        val javaFilePath = pathToSourcesDir.resolve("${fileName.capitalize()}.java")
        try {
            javaFilePath.outputStream().use {
                fileWriter = OutputStreamWriter(BufferedOutputStream(it))
                appendLine { write("package org.utbot.ifds.synthetic.tests") }
                // for simplification - all generated code will be in a single package
                // with all required imports added unconditionally to all files.
                write("\n")
                appendLine { write("import java.util.*") }
                write("\n")
                block()
                flush()
            }
        } finally {
            fileWriter = null
        }
    }

    private fun flush() {
        fileWriter!!.flush()
    }

    // write - just writes
    // append - handles with tabulation and necessary semicolons
    // dump - accept code element and creates text file with its representation
    private fun write(content: String) {
        fileWriter!!.write(content)
    }

    private fun writeSeparated(content: String) {
        write(content)
        write(" ")
    }

    private fun writeVisibility(visibility: VisibilityModifier) {
        writeSeparated(visibility.toString().lowercase())
    }

    private fun writeTypeSignature(type: TypePresentation) {
        writeVisibility(type.visibility)
        when (type.inheritanceModifier) {
            InheritanceModifier.ABSTRACT -> writeSeparated("abstract class")
            InheritanceModifier.OPEN -> writeSeparated("class")
            InheritanceModifier.FINAL -> writeSeparated("final class")
            InheritanceModifier.INTERFACE -> writeSeparated("interface")
            InheritanceModifier.STATIC -> writeSeparated("static class")
            InheritanceModifier.ENUM -> writeSeparated("enum")
        }
        writeSeparated(type.shortName)
    }

    private var offset = 0

    private fun addTab() {
        ++offset
    }

    private fun removeTab() {
        --offset
    }

    private fun tabulate() {
        for (i in 0 until offset) {
            write("\t")
        }
    }

    private fun throwCannotDump(ce: CodeElement) {
        assert(false) { "Do not know how to dump ${ce.javaClass.simpleName}" }
    }

    private fun inScope(block: () -> Unit) {
        try {
            write("{\n")
            addTab()
            // do not tabulate here.
            // each code element should be aware if it has to be tabulated and manage tabulation on its own
            block()
        } finally {
            write("\n")
            removeTab()
            tabulate()
            write("}")
        }
    }

    private fun inScopeWithoutSeparator(block: () -> Unit) {
        try {
            write("{\n")
            addTab()
            // do not tabulate here.
            // each code element should be aware if it has to be tabulated and manage tabulation on its own
            block()
        } finally {
            removeTab()
            tabulate()
            write("}")
        }
    }

    private fun writeParametersList(callable: CallablePresentation) {
        write("(")
        var first = true
        for (parameter in callable.parameters) {
            if (!first) {
                write(", ")
            }
            first = false
            writeTypeUsage(parameter.usage)
            write(parameter.shortName)
        }
        writeSeparated(")")
    }

    private fun appendField(field: FieldPresentation) {
        TODO("not implemented")
    }

    private fun writeTypeUsage(typeUsage: TypeUsage) {
        writeSeparated(typeUsage.stringPresentation)
    }

    private fun writeDefaultValueForTypeUsage(typeUsage: TypeUsage) {
        when (typeUsage) {
            is ArrayTypeUsage -> {
                throwCannotDump(typeUsage)
            }

            is InstanceTypeUsage -> {
                val presentation = typeUsage.typePresentation
                val valueToWrite = presentation.defaultValue
                writeCodeValue(valueToWrite)
            }

            else -> {
                throwCannotDump(typeUsage)
            }
        }
    }

    private fun appendCodeExpression(codeExpression: CodeExpression) =
        appendLine { writeCodeExpression(codeExpression) }

    private fun writeCodeExpression(codeExpression: CodeExpression) {
        when (codeExpression) {
            is ValueExpression -> writeValueExpression(codeExpression)
            is AssignmentExpression -> {
                writeCodeValue(codeExpression.assignmentTarget)
                write(" = ")
                writeCodeValue(codeExpression.assignmentValue)
            }

            else -> {
                throwCannotDump(codeExpression)
            }
        }
    }

    private fun appendCodeValue(codeValue: CodeValue) = appendLine { writeCodeValue(codeValue) }

    private fun writeCodeValue(codeValue: CodeValue) {
        when (codeValue) {
            is DirectStringSubstitution -> write(codeValue.substitution)
            is ValueReference -> {
                val presentation = codeValue.resolve()
                write(presentation.shortName)
            }

            is ValueExpression -> writeValueExpression(codeValue)
            else -> {
                throwCannotDump(codeValue)
            }
        }
    }

    private fun writeValueExpression(valueExpression: ValueExpression) {
        when (valueExpression) {
            is ObjectCreationExpression -> {
                writeSeparated("new")
                write(valueExpression.invokedConstructor.containingType.shortName)
                writeCallList(valueExpression)
            }

            is MethodInvocationExpression -> {
                writeCodeValue(valueExpression.invokedOn)
                write(DOT)
                write(valueExpression.invokedMethod.shortName)
                writeCallList(valueExpression)
            }

            is FunctionInvocationExpression -> {
                write(classNameForStaticFunction(valueExpression.invokedCallable.shortName))
                write(DOT)
                write(valueExpression.invokedCallable.shortName)
                writeCallList(valueExpression)
            }

            else -> {
                throwCannotDump(valueExpression)
            }
        }
    }

    private fun writeCallList(invocationExpression: InvocationExpression) {
        write("(")
        var first = true
        for (parameter in invocationExpression.invokedCallable.parameters) {
            if (!first) {
                write(", ")
            }
            first = false
            val argument: CodeValue? = invocationExpression.parameterToArgument[parameter]
            if (argument == null) {
                writeDefaultValueForTypeUsage(parameter.usage)
            } else {
                writeCodeValue(argument)
            }
        }
        write(")")
    }

    private fun appendLocalVariable(localVariablePresentation: LocalVariablePresentation) = appendLine {
        writeTypeUsage(localVariablePresentation.usage)
        writeSeparated(localVariablePresentation.shortName)

        val initialValue = localVariablePresentation.initialValue

        if (initialValue != null) {
            writeSeparated("=")
            writeCodeValue(initialValue)
        } else {
            writeSeparated("= null")
        }
    }

    private fun appendLine(block: () -> Unit) {
        try {
            tabulate()
            block()
        } finally {
            write(";")
            write("\n")
        }
    }

    private fun appendSite(
        site: Site,
        isFirstInCallSite: Boolean = false,
        isLastInCallSite: Boolean = false,
        hasTerminations: Boolean = true,
        hasCalls: Boolean = true
    ) {
        // we always dump sites in following order:
        // 1. preparation site
        // 2. call sites
        // 3. termination site
        // there are no different function for each site as their logic is connected:
        // there is always termination site in each callable.
        // so after each call site `else` is added.
        when (site) {
            is PreparationSite -> {
                for (before in site.expressionsBefore) {
                    appendCodeExpression(before)
                }
                for (after in site.expressionsAfter) {
                    appendCodeExpression(after)
                }
            }

            is CallSite -> {
                if (isFirstInCallSite) tabulate()
                writeSeparated("if (currentDispatch == ${site.graphId})")
                inScopeWithoutSeparator {
                    for (before in site.expressionsBefore) {
                        appendCodeExpression(before)
                    }
                    appendCodeValue(site.invocationExpression)
                    for (after in site.expressionsAfter) {
                        appendCodeExpression(after)
                    }

                }
                if (!isLastInCallSite || hasTerminations) writeSeparated(" else")
            }

            is TerminationSite -> {
                if (hasTerminations) {
                    if (!hasCalls) {
                        write(TAB + TAB)
                    }
                    inScopeWithoutSeparator {
                        for (before in site.expressionsBefore) {
                            appendCodeExpression(before)
                        }
                        for (dereference in site.dereferences) {
                            appendLine {
                                writeCodeValue(dereference)
                                write(".toString()")
                            }
                        }
                        for (after in site.expressionsAfter) {
                            appendCodeExpression(after)
                        }
                    }
                }
            }
        }
    }

    private fun appendLocalsAndSites(callable: CallablePresentation) {
        val localVariables = callable.localVariables
        for (variable in localVariables) {
            appendLocalVariable(variable)
        }
        appendSite(callable.preparationSite)
        val sites = callable.callSites
        val hasDeref = !(callable.terminationSite.expressionsBefore.isEmpty()
                && callable.terminationSite.expressionsAfter.isEmpty() && callable.terminationSite.dereferences.isEmpty())
        for (site in sites) {
            appendSite(site, site.equals(sites.first()), site.equals(sites.last()), hasDeref)
        }
        appendSite(callable.terminationSite, hasTerminations = hasDeref, hasCalls = !sites.isEmpty())
    }

    private fun appendConstructor(constructor: ConstructorPresentation) {
        tabulate()
        writeVisibility(constructor.visibility)
        write(constructor.containingType.shortName)
        writeParametersList(constructor)
        inScope {
            val parentCall = constructor.parentConstructorCall
            if (parentCall != null) {
                appendLine {
                    write("super")
                    writeCallList(parentCall)
                }
            }
            appendLocalsAndSites(constructor)
        }
    }

    private fun appendStaticFunction(function: FunctionPresentation) = appendStartFunction(function.shortName, function)

    private fun classNameForStaticFunction(functionName: String): String {
        return "ClassFor${functionName.capitalize()}"
    }

    private fun appendStartFunction(name: String, function: FunctionPresentation) {
        writeSeparated("public class ${classNameForStaticFunction(name)}")
        inScope {
            tabulate()
            writeVisibility(function.visibility)
            writeSeparated("static")
            writeTypeUsage(function.returnType)
            write(function.shortName)
            writeParametersList(function)
            inScope {
                appendLocalsAndSites(function)
            }
            write("\n\n")
            tabulate()
            writeVisibility(VisibilityModifier.PUBLIC)
            writeSeparated("static")
            writeSeparated("void")
            writeSeparated("main(String[] args)")
            inScope {
                write(TAB + TAB)
                write(function.shortName)
                write("();")
            }
        }
    }

    private fun appendPSVM(name: String, function: FunctionPresentation) {
        writeSeparated("public class ${classNameForStaticFunction(name)}")
        inScope {
            tabulate()
            writeVisibility(function.visibility)
            writeSeparated("static")
            writeTypeUsage(function.returnType)
            write(function.shortName)
            writeParametersList(function)
            inScope {
                appendLocalsAndSites(function)
            }
        }
    }

    private fun appendMethodSignature(methodPresentation: MethodPresentation) {
        tabulate()
        if (methodPresentation.inheritedFrom != null) {
            writeSeparated("@Override\n")
        }
        tabulate()
        writeVisibility(methodPresentation.visibility)
        when (methodPresentation.inheritanceModifier) {
            InheritanceModifier.ABSTRACT -> writeSeparated("abstract")
            InheritanceModifier.FINAL -> writeSeparated("final")
            InheritanceModifier.STATIC -> writeSeparated("static")
            else -> {
                assert(false) { "should be impossible" }
            }
        }
        writeSeparated(methodPresentation.shortName)
        writeParametersList(methodPresentation)
    }

    private fun appendMethod(methodPresentation: MethodPresentation) {
        appendMethodSignature(methodPresentation)
        inScope {
            appendLocalsAndSites(methodPresentation)
        }
    }

    override fun dumpType(type: TypePresentation, pathToSourcesDir: Path) = inFile(type.shortName, pathToSourcesDir) {
        writeTypeSignature(type)
        inScope {
            for (field in type.implementedFields) {
                appendField(field)
            }
            for (constructor in type.constructors) {
                appendConstructor(constructor)
            }
            for (method in type.implementedMethods) {
                appendMethod(method)
            }

            val staticCounterPart = type.staticCounterPart

            for (staticField in staticCounterPart.implementedFields) {
                appendField(staticField)
            }
            for (staticMethod in staticCounterPart.implementedMethods) {
                appendMethod(staticMethod)
            }
        }
    }

    override fun dumpFunction(func: FunctionPresentation, pathToSourcesDir: Path) =
        inFile(classNameForStaticFunction(func.shortName), pathToSourcesDir) {
            appendStaticFunction(func)
        }

    override fun dumpStartFunction(name: String, func: FunctionPresentation, pathToSourcesDir: Path) =
        inFile(classNameForStaticFunction(name), pathToSourcesDir) {
            appendStartFunction(name, func)
        }

    private fun transferTemplateZipToTemp(): Path {
        val pathWhereToUnzipTemplate = Files.createTempFile(null, null)
        val resourcesName = projectZipInResourcesName()

        this.javaClass.classLoader.getResourceAsStream(resourcesName)!!
            .use { templateFromResourceStream ->
                FileOutputStream(pathWhereToUnzipTemplate.toFile()).use { streamToLocationForTemplate ->
                    templateFromResourceStream.copyTo(streamToLocationForTemplate)
                }
            }

        return pathWhereToUnzipTemplate
    }

    override fun unzipTemplateProject(pathToDirectoryWhereToUnzipTemplate: Path, fullClear: Boolean) {
        pathToDirectoryWhereToUnzipTemplate.createDirectories()

        if (fullClear) {
            pathToDirectoryWhereToUnzipTemplate.toFile().deleteRecursively()
            pathToDirectoryWhereToUnzipTemplate.createDirectories()
        }

        val templateZipAtTemp = transferTemplateZipToTemp()

        ZipFile(templateZipAtTemp.toFile()).extractAll(pathToDirectoryWhereToUnzipTemplate.absolutePathString())
    }
}