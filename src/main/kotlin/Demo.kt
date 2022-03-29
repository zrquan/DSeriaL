import java.io.ByteArrayOutputStream
import java.io.ObjectStreamConstants.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@DSeriaL
class DescriptorsBuilder(
    private val parent: Any,
    private val nextHandleIndex: AtomicInteger,
    private val postDescriptorHierarchyHandle: Handle,
    private val output: ((UncheckedBlockDataOutputStream) -> Unit) -> Unit
) : Descriptor, DescriptorPrimitiveFields {
    private lateinit var fieldActions: MutableList<(UncheckedBlockDataOutputStream) -> Unit>

    private var descriptorName: String = ""
    private var uid: Long = 0L
    private var flags: Byte = SC_SERIALIZABLE

    fun desc(
        unassignedHandle: Handle = Handle(),
        type: Class<*>,
        uid: Long,
        flags: Byte,
        build: Descriptor.() -> Unit
    ) {
        val handleIndex = nextHandleIndex.getAndIncrement()
        Handle.assignIndex(unassignedHandle, handleIndex)

        output { it.writeByte(TC_CLASSDESC.toInt()) }
        fieldActions = mutableListOf()

        this.descriptorName = typeNameToClassGetName(type.typeName)
        this.uid = uid
        this.flags = flags
        this.build()
        endDesc()
    }

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
}

@DSeriaL
class SerialBuilder : Slot {
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

    override fun charVal(c: Char) {
        primitiveFieldsActions.add {
            it.writeChar(c.code)
        }
    }

    override fun intVal(i: Int) {
        primitiveFieldsActions.add {
            it.writeInt(i)
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
