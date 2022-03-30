import java.io.ByteArrayOutputStream
import java.io.ObjectStreamClass
import java.io.ObjectStreamConstants.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class DescriptorsBuilder(
    private val parent: Any,
    private val nextHandleIndex: AtomicInteger,
    private val postDescriptorHierarchyHandle: Handle,
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
        flags: Byte,
        build: Descriptor.() -> Unit
    ) {
        val handleIndex = nextHandleIndex.getAndIncrement()
        Handle.assignIndex(unassignedHandle, handleIndex)

        output { it.writeByte(TC_CLASSDESC.toInt()) }
        fieldActions = mutableListOf()

        this.descriptorName = typeNameToClassGetName(type.typeName)
        this.uid = uid ?: getUidByType(type)
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
            TODO("handle array type name")
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
            // 还没搞懂 BlockDataMode
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

@DSeriaL
class SerialBuilder : Slot, SlotPrimitiveFields {
    private var nestingDepth = 0
    private val nextHandleIndex = AtomicInteger(0)
    private val pendingPostObjectActions: Deque<Block> = LinkedList()

    // todo: 用来收集需要计算数量的写入动作，比如收集数组元素
    private val pendingObjectActions: Deque<MutableList<Block>> = LinkedList()

    private val binOut = ByteArrayOutputStream()
    private val out = UncheckedBlockDataOutputStream(binOut)

    init {
        out.writeShort(STREAM_MAGIC.toInt())
        out.writeShort(STREAM_VERSION.toInt())
    }

    private lateinit var descriptorsBuilder: DescriptorsBuilder

    fun serialObj(unassignedHandle: Handle = Handle(), build: SerialBuilder.() -> Unit) {
        beginSerializableObject(unassignedHandle)
        this.build()
        finish()
    }

    fun jclass(unassignedHandle: Handle = Handle(), build: DescriptorsBuilder.() -> Unit) {
        nestingDepth++

        val oldMode = AtomicBoolean()
        run {
            val oldModeValue = out.setBlockDataMode(false)
            oldMode.set(oldModeValue)
            out.writeByte(TC_CLASS.toInt())
        }
        pendingPostObjectActions.addLast {
            out.setBlockDataMode(oldMode.get())
        }
        onStartedObject(false)

        initDescriptorHierarchy(unassignedHandle)
        descriptors(build)

        // end class
        nestingDepth--
        run { pendingPostObjectActions.removeLast() }
    }

    fun nil() {
        run {
            val oldMode = out.setBlockDataMode(false)
            out.writeByte(TC_NULL.toInt())
            out.setBlockDataMode(oldMode)
        }
        onStartedObject(false)
    }

    fun string(text: String, unassignedHandle: Handle = Handle()) {
        val handleIndex = nextHandleIndex.getAndIncrement()
        Handle.assignIndex(unassignedHandle, handleIndex)

        run {
            val oldMode = out.setBlockDataMode(false)
            out.writeSerialString(text)
            out.setBlockDataMode(oldMode)
        }
        onStartedObject(false)
    }

    fun beginSerializableObject(unassignedHandle: Handle) {
        nestingDepth++

        val oldMode = AtomicBoolean()
        run {
            val oldModeValue = out.setBlockDataMode(false)
            oldMode.set(oldModeValue)
            out.writeByte(TC_OBJECT.toInt())
        }
        pendingPostObjectActions.addLast {
            out.setBlockDataMode(oldMode.get())
        }
        onStartedObject(true)

        initDescriptorHierarchy(unassignedHandle)
    }

    private fun initDescriptorHierarchy(postDescriptorHierarchyHandle: Handle) {
        descriptorsBuilder = DescriptorsBuilder(
            this,
            nextHandleIndex,
            postDescriptorHierarchyHandle
        ) { action: (UncheckedBlockDataOutputStream) -> Unit ->
            run { action(out) }
        }
    }

    private val objectArrayElementCounts: Deque<AtomicInteger> = LinkedList()

    private fun onStartedObject(canBeNested: Boolean) {
        objectArrayElementCounts.peekLast()?.incrementAndGet()

        if (canBeNested) {
            objectArrayElementCounts.addLast(null)
        }
    }

    private fun run(block: Block) {
        val actionsQueue = pendingObjectActions.peekLast()
        if (actionsQueue == null) {
            block()
        } else {
            actionsQueue.add(block)
        }
    }

    fun descriptors(build: DescriptorsBuilder.() -> Unit) {
        descriptorsBuilder.build()
        descriptorsBuilder.finish()
    }

    private val hasWrittenSlot: Deque<AtomicBoolean> = LinkedList()
    private val fieldActions: Deque<Queue<Block>> = LinkedList()  // ?

    fun slot(build: Slot.() -> Unit) {
        hasWrittenSlot.addLast(AtomicBoolean(false))
        fieldActions.addLast(LinkedList())
        this.build()

        if (!hasWrittenSlot.removeLast().get()) {
            this.fieldActions.removeLast().forEach(::run)
        }
    }

    private lateinit var primitiveFieldsActions: MutableList<(UncheckedBlockDataOutputStream) -> Unit>

    override fun primitiveFields(build: SlotPrimitiveFields.() -> Unit) {
        primitiveFieldsActions = mutableListOf()
        this.build()

        // Write primitive field values to output stream
        primitiveFieldsActions.forEach { it(out) }
    }

    override fun objectFields(build: SerialBuilder.() -> Unit) = this.build()

    override fun charVal(c: Char) {
        primitiveFieldsActions.add {
            it.writeChar(c.code)
        }
    }

    override fun longVal(j: Long) {
        primitiveFieldsActions.add {
            it.writeLong(j)
        }
    }

    override fun floatVal(f: Float) {
        primitiveFieldsActions.add {
            it.writeFloat(f)
        }
    }

    override fun shortVal(s: Short) {
        primitiveFieldsActions.add {
            it.writeShort(s.toInt())
        }
    }

    override fun doubleVal(d: Double) {
        primitiveFieldsActions.add {
            it.writeDouble(d)
        }
    }

    override fun booleanVal(z: Boolean) {
        primitiveFieldsActions.add {
            primitiveFieldsActions.add {
                it.writeBoolean(z)
            }
        }
    }

    override fun intVal(i: Int) {
        primitiveFieldsActions.add {
            it.writeInt(i)
        }
    }

    override fun byteVal(b: Byte) {
        primitiveFieldsActions.add {
            it.writeByte(b.toInt())
        }
    }

    fun finish(): ByteArray? {
        nestingDepth--
        run { pendingPostObjectActions.removeLast() }

        val dummyElementCount = objectArrayElementCounts.removeLast()
        check(dummyElementCount == null) { "Unexpected element count: $dummyElementCount" }

        if (nestingDepth == 0) {
            if (!objectArrayElementCounts.isEmpty()) {
                throw AssertionError("Unprocessed element counts: $objectArrayElementCounts")
            }
            if (!pendingPostObjectActions.isEmpty()) {
                throw AssertionError("Unprocessed post object actions: ${pendingPostObjectActions.size}")
            }

            return getSerialData()
        } else {
            return null
        }
    }

    private fun getSerialData(): ByteArray {
        out.close()
        return binOut.toByteArray()
    }
}

fun serial(
    unassignedHandle: Handle = Handle(),
    build: SerialBuilder.() -> Unit
): ByteArray? =
    with(SerialBuilder()) {
        beginSerializableObject(unassignedHandle)
        build()
        finish()
    }
