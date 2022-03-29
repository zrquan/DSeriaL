import java.io.*
import java.io.ObjectStreamConstants.SC_SERIALIZABLE

fun serialize(obj: Serializable): ByteArray {
    val out = ByteArrayOutputStream()
    try {
        ObjectOutputStream(out).writeObject(obj)
    } catch (e: IOException) {
        throw UncheckedIOException(e)
    }
    return out.toByteArray()
}

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

fun main() {
    val actualData =
        serial {
            descriptors {
                desc(
                    type = SimpleSerializableClass::class.java,
                    uid = SimpleSerializableClass.serialVersionUID,
                    flags = SC_SERIALIZABLE
                ) {
                    primitiveFields {
                        "i" type Int::class.java
                    }
                }
                // more descriptors...
            }
            slot {
                primitiveFields {
                    intVal(1)
                }
            }
            // more slots...
        }

    val expectedData = serialize(SimpleSerializableClass(1))

    println("Work as expected: ${actualData.contentEquals(expectedData)}")
    println("Actual data: ${actualData?.toHex()}")
}
