package ets

import analysis.type.EtsApplicationGraphWithExplicitEntryPoint
import analysis.type.TypeInferenceManager
import org.jacodb.analysis.util.EtsTraits
import org.jacodb.panda.dynamic.ets.dto.EtsFileDto
import org.jacodb.panda.dynamic.ets.dto.convertToEtsFile
import org.jacodb.panda.dynamic.ets.graph.EtsApplicationGraph
import org.jacodb.panda.dynamic.ets.model.EtsFile
import org.junit.jupiter.api.Test

class EtsTypeInferenceTest {

    companion object : EtsTraits {
        private fun loadArkFile(name: String): EtsFile {
            val path = "etsir/$name.ts.json"
            val stream = object {}::class.java.getResourceAsStream("/$path")
                ?: error("Resource not found: $path")
            val arkDto = EtsFileDto.loadFromJson(stream)
            // println("arkDto = $arkDto")
            val ark = convertToEtsFile(arkDto)
            // println("ark = $ark")
            return ark
        }
    }

    @Test
    fun `test type inference`() {
        val arkFile = loadArkFile("types")
        val graph = EtsApplicationGraph(arkFile)

        val graphWithExplicitEntryPoint = EtsApplicationGraphWithExplicitEntryPoint(graph)

        val entrypoints = arkFile.classes.flatMap { it.methods }.filter { it.name.startsWith("entrypoint") }

        val manager = TypeInferenceManager(graphWithExplicitEntryPoint)
        manager.analyze(entrypoints)


    }
}