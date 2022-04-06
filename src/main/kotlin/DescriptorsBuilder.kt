import java.io.ObjectStreamClass
import java.io.ObjectStreamConstants.*
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger

class Descriptor(val handle: Handle, val nextHandleIndex: AtomicInteger) {
    var fieldActions: MutableList<(UncheckedBlockDataOutputStream) -> Unit> = mutableListOf()

    @DSeriaL
    lateinit var type: Class<*>

    @DSeriaL
    var uid: Long? = null

    @DSeriaL
    var flags: Byte = SC_SERIALIZABLE

    infix fun String.type(type: Class<*>) {
        if (type.isPrimitive) {
            primitiveDesc(this, type.typeName)
        } else {
            objectDesc(this, type.typeName)
        }
    }

    private fun primitiveDesc(name: String, typeName: String) {
        val typeCode = getTypeCode(typeName)
        if (typeCode.length != 1) {
            throw Exception("Invalid primitive type name: $typeName")
        }

        fieldActions.add {
            it.writeByte(typeCode.first().code)
            it.writeUTF(name)
        }
    }

    private fun objectDesc(name: String, typeName: String) {
        val nameBuilder = StringBuilder()
        var elementTypeName = typeName

        while (elementTypeName.endsWith("[]")) {
            nameBuilder.append("[")
            elementTypeName = elementTypeName.substringBeforeLast("[")
        }

        // String containing the field's type, in field descriptor format
        val className1 = nameBuilder.append(getTypeCode(elementTypeName)).toString()

        nextHandleIndex.getAndIncrement()

        fieldActions.add {
            it.writeByte(className1[0].code)
            it.writeUTF(name)
            it.writeSerialString(className1)
        }
    }
}

class DescriptorsBuilder(
    private val parent: Any,
    private val nextHandleIndex: AtomicInteger,
    private val postDescriptorHierarchyHandle: Handle,
    private var isEnum: Boolean = false,
    private val output: ((UncheckedBlockDataOutputStream) -> Unit) -> Unit
) {
    private lateinit var currentDesc: Descriptor

    fun proxy(vararg interfaces: Class<*>) = proxy(Handle(), *interfaces)

    fun proxy(unassignedHandle: Handle, vararg interfaces: Class<*>) {
        val interfaceNames = getInterfaceNames(*interfaces)
        Handle.assignIndex(unassignedHandle, nextHandleIndex.getAndIncrement())

        output {
            it.writeByte(TC_PROXYCLASSDESC.toInt())
            it.writeInt(interfaceNames.size)
            for (name in interfaceNames) {
                it.writeUTF(typeNameToClassGetName(name))
            }

            it.setBlockDataMode(true)
            it.setBlockDataMode(false)
            it.writeByte(TC_ENDBLOCKDATA.toInt())
        }

        this.desc {
            type = Proxy::class.java
            "h" type InvocationHandler::class.java
        }
    }

    private fun getInterfaceNames(vararg interfaces: Class<*>) =
        interfaces.map {
            if (!it.isInterface) throw IllegalArgumentException("Not a interface: ${it.typeName}")
            it.typeName
        }

    fun desc(
        unassignedHandle: Handle = Handle(),
        build: Descriptor.() -> Unit
    ) {
        val handleIndex = nextHandleIndex.getAndIncrement()
        Handle.assignIndex(unassignedHandle, handleIndex)

        output { it.writeByte(TC_CLASSDESC.toInt()) }

        currentDesc = Descriptor(unassignedHandle, nextHandleIndex)
        currentDesc.build()

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
        with(currentDesc) {
            val descriptorName = typeNameToClassGetName(type.typeName)
            val uid = if (this@DescriptorsBuilder.isEnum) {
                check(type.isEnum || type == Enum::class.java) { "Not an Enum class: ${type.typeName}" }
                0  // Enum uses 0 as UID
            } else this.uid ?: getUidByType(type)

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
    }

    fun finish() {
        output { it.writeByte(TC_NULL.toInt()) }
        finishAndGetParent()
    }

    private fun finishAndGetParent() {
        Handle.assignIndex(postDescriptorHierarchyHandle, nextHandleIndex.getAndIncrement())
    }
}

fun getTypeCode(name: String) =
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
