import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.extension.ExtensionConfigurationException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

fun String.fromHex(): ByteArray {
    check(length % 2 == 0) { "Must have an even length" }

    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

class DslGen : FunSpec({

    data class TestData(val fileName: String, val dlsStr: String, val streamStr: String)

    fun getResources(): List<TestData> {
        val resourceUrl = this::class.java.getResource("data")
        val dslExt = "dsl.txt"
        val streamExt = "stream.txt"

        val dslCodeFiles = linkedMapOf<String, Path>()
        val streamDataFiles = linkedMapOf<String, Path>()

        Files
            .list(Path.of(resourceUrl!!.toURI()))
            .filter(Files::isRegularFile)
            .forEach { file ->
                val fileName = file.fileName.toString()
                if (fileName.endsWith(dslExt)) {
                    dslCodeFiles[fileName.substringBefore(".")] = file
                } else if (fileName.endsWith(streamExt)) {
                    streamDataFiles[fileName.substringBefore(".")] = file
                } else {
                    throw ExtensionConfigurationException("File with unsupported ext: $file")
                }
            }

        if (dslCodeFiles.isEmpty()) throw ExtensionConfigurationException("No data files exist")

        return dslCodeFiles
            .map { (name, path) ->
                val streamFile = streamDataFiles.remove(name)
                    ?: throw ExtensionConfigurationException("Missing expected data file for $name")

                TestData(name, path.readText().trim(), streamFile.readText().trim())
            }
            .toList()
    }

    context("DSL generator") {
        withData(
            nameFn = { it.fileName },
            getResources()
        ) { (_, dslStr, streamStr) ->
            DslGenerator().generate(streamStr.fromHex()) shouldBe dslStr
        }
    }
})
