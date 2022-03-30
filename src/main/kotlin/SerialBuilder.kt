import java.io.ByteArrayOutputStream
import java.io.ObjectStreamConstants
import java.io.ObjectStreamConstants.TC_CLASS
import java.io.ObjectStreamConstants.TC_ENUM
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class SerialBuilder : Slot, SlotPrimitiveFields {
    private var nestingDepth = 0
    private val nextHandleIndex = AtomicInteger(0)
    private val pendingPostObjectActions: Deque<Block> = LinkedList()

    // todo: 用来收集需要计算数量的写入动作，比如收集数组元素
    private val pendingObjectActions: Deque<MutableList<Block>> = LinkedList()

    private val binOut = ByteArrayOutputStream()
    private val out = UncheckedBlockDataOutputStream(binOut)

    init {
        out.writeShort(ObjectStreamConstants.STREAM_MAGIC.toInt())
        out.writeShort(ObjectStreamConstants.STREAM_VERSION.toInt())
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
            out.writeByte(ObjectStreamConstants.TC_NULL.toInt())
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

    fun enum(constName: String, unassignedHandle: Handle = Handle(), build: DescriptorsBuilder.() -> Unit) {
        nestingDepth++

        val oldMode = AtomicBoolean()
        run {
            val oldModeValue = out.setBlockDataMode(false)
            oldMode.set(oldModeValue)
            out.writeByte(TC_ENUM.toInt())
        }
        pendingPostObjectActions.addLast {
            out.setBlockDataMode(oldMode.get())
        }
        onStartedObject(false)

        initDescriptorHierarchy(unassignedHandle, isEnum = true)
        descriptors(build)

        // write constant name
        nextHandleIndex.getAndIncrement()
        run { out.writeSerialString(constName) }

        // end enum
        nestingDepth--
        run { pendingPostObjectActions.removeLast() }
    }

    fun beginSerializableObject(unassignedHandle: Handle) {
        nestingDepth++

        val oldMode = AtomicBoolean()
        run {
            val oldModeValue = out.setBlockDataMode(false)
            oldMode.set(oldModeValue)
            out.writeByte(ObjectStreamConstants.TC_OBJECT.toInt())
        }
        pendingPostObjectActions.addLast {
            out.setBlockDataMode(oldMode.get())
        }
        onStartedObject(true)

        initDescriptorHierarchy(unassignedHandle)
    }

    private fun initDescriptorHierarchy(postDescriptorHierarchyHandle: Handle, isEnum: Boolean = false) {
        descriptorsBuilder = DescriptorsBuilder(
            this,
            nextHandleIndex,
            postDescriptorHierarchyHandle,
            isEnum
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
