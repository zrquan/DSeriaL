import java.io.ObjectStreamClass
import java.io.ObjectStreamConstants.*
import java.util.concurrent.atomic.AtomicInteger

class DescriptorsBuilder(
    private val parent: Any,
    private val nextHandleIndex: AtomicInteger,
    private val postDescriptorHierarchyHandle: Handle,
    private var isEnum: Boolean = false,
    private val output: ((UncheckedBlockDataOutputStream) -> Unit) -> Unit
) : Descriptor, DescriptorPrimitiveFields, DescriptorObjectFields {
    private lateinit var fieldActions: MutableList<(UncheckedBlockDataOutputStream) -> Unit>

    private var descriptorName: String = ""
    private var uid: Long = 0L
    private var flags: Byte = SC_SERIALIZABLE

    fun desc(
        unassignedHandle: Handle = Handle(),
        type: Class<*>,
        uid: Long? = null,
        flags: Byte = SC_SERIALIZABLE,
        build: Descriptor.() -> Unit
    ) {
        val handleIndex = nextHandleIndex.getAndIncrement()
        Handle.assignIndex(unassignedHandle, handleIndex)

        output { it.writeByte(TC_CLASSDESC.toInt()) }
        fieldActions = mutableListOf()

        this.uid = if (this.isEnum) {
            check(type.isEnum || type == Enum::class.java) { "Not an Enum class: ${type.typeName}" }
            0  // Enum uses 0 as UID
        } else uid ?: getUidByType(type)

        this.descriptorName = typeNameToClassGetName(type.typeName)
        this.flags = flags
        this.build()
        endDesc()
    }

    /**
     * 查找可被序列化的目标类型，返回它的 UID 或抛出 NPE 异常
     */
    private fun getUidByType(type: Class<*>): Long = ObjectStreamClass.lookup(type)!!.serialVersionUID

    private fun typeNameToClassGetName(typeName: String): String {
        if (!typeName.endsWith("[]")) {
            return typeName
        } else {
            val classGetName = StringBuilder()
            var elementTypeName = typeName

            while (elementTypeName.endsWith("[]")) {
                classGetName.append("[")
                elementTypeName = elementTypeName.substringBeforeLast("[")
            }

            return classGetName.append(
                when (elementTypeName) {
                    "int" -> "I"
                    "byte" -> "B"
                    "char" -> "C"
                    "long" -> "J"
                    "float" -> "F"
                    "short" -> "S"
                    "double" -> "D"
                    "boolean" -> "Z"
                    // Note: Class.getName() does not replace '.' with '/'
                    else -> "L$elementTypeName;"
                }
            ).toString()
        }
    }

    private fun endDesc() {
        val fieldsCount = fieldActions.size
        output {
            it.writeUTF(descriptorName)
            it.writeLong(uid)
            it.writeByte(flags.toInt())
            it.writeShort(fieldsCount)
        }
        fieldActions.forEach(output)
        output {
            it.setBlockDataMode(true)
            it.setBlockDataMode(false)
            it.writeByte(TC_ENDBLOCKDATA.toInt())
        }
        fieldActions.clear()
    }

    fun finish() {
        output { it.writeByte(TC_NULL.toInt()) }
        finishAndGetParent()
    }

    private fun finishAndGetParent() {
        Handle.assignIndex(postDescriptorHierarchyHandle, nextHandleIndex.getAndIncrement())
    }

    /**
     * Switch the context to [DescriptorPrimitiveFields]
     */
    override fun primitiveFields(build: DescriptorPrimitiveFields.() -> Unit) = this.build()

    override fun objectFields(build: DescriptorObjectFields.() -> Unit) = this.build()

    private fun getJvmTypeName(name: String) =
        when (name) {
            "int" -> "I"
            "byte" -> "B"
            "char" -> "C"
            "long" -> "J"
            "float" -> "F"
            "short" -> "S"
            "double" -> "D"
            "boolean" -> "Z"
            else -> "L${name.replace(".", "/")};"
        }

    override fun primitiveField(name: String, typeName: String) {
        val jvmTypeName = getJvmTypeName(typeName)
        if (jvmTypeName.length != 1) {
            throw Exception("Invalid primitive type name: $typeName")
        }

        fieldActions.add {
            it.writeByte(jvmTypeName.first().code)
            it.writeUTF(name)
        }
    }

    override fun objectField(name: String, typeName: String) {
        val nameBuilder = StringBuilder()
        var elementTypeName = typeName

        while (elementTypeName.endsWith("[]")) {
            nameBuilder.append("[")
            elementTypeName = elementTypeName.substringBeforeLast("[")
        }

        val jvmTypeName = nameBuilder.append(getJvmTypeName(elementTypeName)).toString()

        nextHandleIndex.getAndIncrement()

        fieldActions.add {
            it.writeByte(jvmTypeName[0].code)
            it.writeUTF(name)
            it.writeSerialString(jvmTypeName)
        }
    }
}
