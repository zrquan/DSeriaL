import java.io.*
import java.io.ObjectStreamConstants.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.ArrayList

class StreamBuilder : SerialTopLevel, ExternalTopLevel, Slot, SlotPrimitiveFields, ArrayElements {
    private var nestingDepth = 0
    private val nextHandleIndex = AtomicInteger(0)
    private val pendingPostObjectActions: Deque<Block> = LinkedList()

    private val pendingObjectActions: Deque<MutableList<Block>> = LinkedList()

    private val binOut = ByteArrayOutputStream()
    private val out = UncheckedBlockDataOutputStream(binOut)

    init {
        out.writeShort(STREAM_MAGIC.toInt())
        out.writeShort(STREAM_VERSION.toInt())
    }

    private lateinit var descriptorsBuilder: DescriptorsBuilder

    fun serialObj(unassignedHandle: Handle = Handle(), build: StreamBuilder.() -> Unit) {
        beginSerializableObject(unassignedHandle)
        this.build()
        finish()
    }

    fun jclass(unassignedHandle: Handle = Handle(), build: Descriptor.() -> Unit) {
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
        descriptors { desc(build = build) }

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

    fun array(
        unassignedHandle: Handle = Handle(),
        type: Class<*>,
        uid: Long? = null,
        flags: Byte = SC_SERIALIZABLE,
        build: ArrayElements.() -> Unit
    ) {
        nestingDepth++

        val oldMode = AtomicBoolean()
        run {
            val oldModeValue = out.setBlockDataMode(false)
            oldMode.set(oldModeValue)
            out.writeByte(TC_ARRAY.toInt())
        }
        pendingPostObjectActions.addLast {
            out.setBlockDataMode(oldMode.get())
        }
        onStartedObject(true)

        initDescriptorHierarchy(unassignedHandle)
        descriptors {
            desc {
                this.type = type
                this.uid = uid
                this.flags = flags
            }
        }

        this.build()

        // end array
        nestingDepth--
        run { pendingPostObjectActions.removeLast() }

        val dummyElementCount = objectArrayElementCounts.removeLast()
        check(dummyElementCount == null) { "Unexpected element count: $dummyElementCount" }
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

    /**
     * 如果当前的动作需要挂起，则添加到 actions 队列，否则直接执行
     * @param block 需要执行的方法体
     */
    private fun run(block: Block) {
        val actionsQueue = pendingObjectActions.peekLast()
        if (actionsQueue == null) {
            block()
        } else {
            actionsQueue.add(block)
        }
    }

    override fun writeObject(build: StreamBuilder.(DataOutput) -> Unit) {
//        hasWrittenSlot.last.set(true)
        run { out.setBlockDataMode(true) }

        writeData(build)

//        val fieldActions = this.fieldActions.removeLast()
//        if (fieldActions.isNotEmpty()) {
//            out.setBlockDataMode(false)
//            fieldActions.forEach(::run)
//            out.setBlockDataMode(true)
//        }

        run {
            out.setBlockDataMode(false)
            out.writeByte(TC_ENDBLOCKDATA.toInt())
        }
    }

    /**
     * 目前只实现 V2 版本的序列化协议，所以默认开启 BlockData 模式
     * @see java.io.ObjectStreamConstants.PROTOCOL_VERSION_2
     */
    override fun writeExternal(build: StreamBuilder.(DataOutput) -> Unit) {
        run { out.setBlockDataMode(true) }

        writeData(build)

        run {
            out.setBlockDataMode(false)
            out.writeByte(TC_ENDBLOCKDATA.toInt())
        }
    }

    /**
     * 用于计算 Externalizable 对象的嵌套层数，在调用 [writeData] 时递增
     */
    private var currentScopeIndex = -1

    private fun writeData(build: StreamBuilder.(DataOutput) -> Unit) {
        val originNestingDepth = nestingDepth
        val scopeIndex = ++currentScopeIndex

        val dataOutput = object : DataOutput {
            private fun verify() {
                check(scopeIndex == currentScopeIndex) { "Other output is currently active." }
                check(originNestingDepth == nestingDepth) { "Previous builder call is incomplete." }
            }

            private fun run(block: Block) {
                verify()
                this@StreamBuilder.run(block)
            }

            override fun write(b: Int) = run { out.writeInt(b) }

            override fun write(b: ByteArray) = run { out.write(b.clone()) }

            override fun write(b: ByteArray, off: Int, len: Int) {
                Objects.checkFromIndexSize(off, len, b.size)  // 边界检查
                run { out.write(b.clone(), off, len) }
            }

            override fun writeBoolean(v: Boolean) = run { out.writeBoolean(v) }

            override fun writeByte(v: Int) = run { out.writeByte(v) }

            override fun writeShort(v: Int) = run { out.writeShort(v) }

            override fun writeChar(v: Int) = run { out.writeChar(v) }
            fun writeChar(c: Char) = writeChar(c.code)

            override fun writeInt(v: Int) = run { out.writeInt(v) }

            override fun writeLong(v: Long) = run { out.writeLong(v) }

            override fun writeFloat(v: Float) = run { out.writeFloat(v) }

            override fun writeDouble(v: Double) = run { out.writeDouble(v) }

            override fun writeBytes(s: String) = run { out.writeBytes(s) }

            override fun writeChars(s: String) = run { out.writeChars(s) }

            override fun writeUTF(s: String) = run { out.writeUTF(s) }
        }

        try {
            build(dataOutput)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }

        check(nestingDepth == originNestingDepth) { "Builder call did not complete." }

        currentScopeIndex--
    }

    override fun descriptors(build: DescriptorsBuilder.() -> Unit) {
        descriptorsBuilder.build()
        descriptorsBuilder.finish()
    }

//    private val hasWrittenSlot: Deque<AtomicBoolean> = LinkedList()
//    private val fieldActions: Deque<Queue<Block>> = LinkedList()  // ?

    override fun slot(build: Slot.() -> Unit) {
//        hasWrittenSlot.addLast(AtomicBoolean(false))
//        fieldActions.addLast(LinkedList())
        this.build()

//        if (!hasWrittenSlot.removeLast().get()) {
//            this.fieldActions.removeLast().forEach(::run)
//        }
    }

    private lateinit var primitiveFieldsActions: MutableList<(DataOutputStream) -> Unit>

    override fun primitiveFields(build: SlotPrimitiveFields.() -> Unit) {
        primitiveFieldsActions = mutableListOf()
        this.build()

        // Write primitive field values to output stream
        val tempOut = ByteArrayOutputStream()
        val dataOut = DataOutputStream(tempOut)

        primitiveFieldsActions.forEach { it(dataOut) }
        primitiveFieldsActions.clear()

        try {
            dataOut.close()
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }

        run { out.write(tempOut.toByteArray()) }
    }

    override fun objectFields(build: StreamBuilder.() -> Unit) = this.build()

    override fun prims(build: SlotPrimitiveFields.() -> Unit) = primitiveFields(build)

    override fun objs(build: StreamBuilder.() -> Unit) = objectFields(build)

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

    override fun primitiveElements(elements: Any) {
        when (elements) {
            is IntArray -> run { out.writeSerialArray(elements) }
            is BooleanArray -> run { out.writeSerialArray(elements) }
            is ByteArray -> run { out.writeSerialArray(elements) }
            is FloatArray -> run { out.writeSerialArray(elements) }
            is ShortArray -> run { out.writeSerialArray(elements) }
            is LongArray -> run { out.writeSerialArray(elements) }
            is CharArray -> run { out.writeSerialArray(elements) }
            is DoubleArray -> run { out.writeSerialArray(elements) }
            else -> throw IllegalStateException("Not a primitive array: ${elements::class.java}")
        }
    }

    override fun elements(build: StreamBuilder.() -> Unit) {
        pendingObjectActions.addLast(ArrayList())
        objectArrayElementCounts.addLast(AtomicInteger(0))
        this.build()

        // end elements
        val actions = pendingObjectActions.removeLast()
        val elementsCount = objectArrayElementCounts.removeLast().get()
        run {
            out.writeInt(elementsCount)
            actions.forEach { it() }
        }
    }
}
