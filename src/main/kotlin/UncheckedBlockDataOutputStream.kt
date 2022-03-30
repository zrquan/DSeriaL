import java.io.*
import java.io.ObjectStreamConstants.*

class UncheckedBlockDataOutputStream(out: OutputStream) : DataOutput, Closeable {
    private val dataOut: DataOutputStream
    private var pendingBlockData: ByteArrayOutputStream?

    /**
     * 引用当前输出流，用于切换普通输出流和 data-block 模式下的输出流
     */
    private var currentDataOut: DataOutputStream

    init {
        dataOut = DataOutputStream(out)
        currentDataOut = dataOut
        pendingBlockData = null
    }

    override fun write(b: Int) {
        try {
            currentDataOut.write(b)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    override fun write(b: ByteArray) {
        try {
            currentDataOut.write(b)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        try {
            currentDataOut.write(b, off, len)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    override fun writeBoolean(v: Boolean) {
        try {
            currentDataOut.writeBoolean(v)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    override fun writeByte(v: Int) {
        try {
            currentDataOut.writeByte(v)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    override fun writeShort(v: Int) {
        try {
            currentDataOut.writeShort(v)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    override fun writeChar(v: Int) {
        try {
            currentDataOut.writeChar(v)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    override fun writeInt(v: Int) {
        try {
            currentDataOut.writeInt(v)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    override fun writeLong(v: Long) {
        try {
            currentDataOut.writeLong(v)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    override fun writeFloat(v: Float) {
        try {
            currentDataOut.writeFloat(v)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    override fun writeDouble(v: Double) {
        try {
            currentDataOut.writeDouble(v)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    @Deprecated("Method is error-prone because it only supports ASCII.")
    override fun writeBytes(s: String) {
        try {
            currentDataOut.writeBytes(s)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    override fun writeChars(s: String) {
        try {
            currentDataOut.writeChars(s)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    override fun writeUTF(s: String) {
        try {
            currentDataOut.writeUTF(s)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    /**
     * 按照 Java 序列化规范对字符串进行特殊编码，并写到输出流
     * @param s 待序列化的字符串
     */
    fun writeSerialString(s: String) {
        val tempOut = ByteArrayOutputStream(s.length)

        // See https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataInput.html#modified-utf-8
        s.toCharArray().forEach {
            val c = it.code
            if (c in 0x0001..0x007F) {
                tempOut.write(c)
            }
            // Covers 0x0000 || (0x0080 - 0x07FF)
            else if (c <= 0x07FF) {
                tempOut.write(192 or (c shr 6) and 31)
                tempOut.write(128 or c and 63)
            }
            // Covers 0x0800 - 0xFFFF
            else {
                tempOut.write(224 or (c shr 12) and 15)
                tempOut.write(128 or (c shr 6) and 63)
                tempOut.write(128 or c and 63)
            }
        }

        val strBytes = tempOut.toByteArray()
        val size = strBytes.size
        if (size <= 65535) {
            writeByte(TC_STRING.toInt())
            writeShort(size)
        } else {
            writeByte(TC_LONGSTRING.toInt())
            writeLong(size.toLong())
        }
        write(strBytes)
    }

    fun writeSerialArray(array: BooleanArray) {
        try {
            currentDataOut.writeInt(array.size)
            array.forEach { currentDataOut.writeBoolean(it) }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    fun writeSerialArray(array: ByteArray) {
        try {
            currentDataOut.writeInt(array.size)
            array.forEach { currentDataOut.writeByte(it.toInt()) }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    fun writeSerialArray(array: CharArray) {
        try {
            currentDataOut.writeInt(array.size)
            array.forEach { currentDataOut.writeChar(it.code) }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    fun writeSerialArray(array: ShortArray) {
        try {
            currentDataOut.writeInt(array.size)
            array.forEach { currentDataOut.writeShort(it.toInt()) }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    fun writeSerialArray(array: IntArray) {
        try {
            currentDataOut.writeInt(array.size)
            array.forEach { currentDataOut.writeInt(it) }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    fun writeSerialArray(array: LongArray) {
        try {
            currentDataOut.writeInt(array.size)
            array.forEach { currentDataOut.writeLong(it) }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    fun writeSerialArray(array: FloatArray) {
        try {
            currentDataOut.writeInt(array.size)
            array.forEach { currentDataOut.writeFloat(it) }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    fun writeSerialArray(array: DoubleArray) {
        try {
            currentDataOut.writeInt(array.size)
            array.forEach { currentDataOut.writeDouble(it) }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    fun isBlockDataModeActive() = pendingBlockData != null

    fun setBlockDataMode(active: Boolean): Boolean {
        val wasActive = isBlockDataModeActive()

        if (active) {
            check(!wasActive) { "Block data mode is already active" }
            pendingBlockData = ByteArrayOutputStream()
            currentDataOut = DataOutputStream(pendingBlockData)
        } else {
            if (!wasActive) return false

            // 退出 block-data 模式
            try {
                currentDataOut.flush()
            } catch (e: IOException) {
                throw UncheckedIOException(e)
            }
            val blockData = pendingBlockData!!.toByteArray()
            pendingBlockData = null
            currentDataOut = dataOut

            val size = blockData.size
            if (size > 0) {
                if (size <= 255) {
                    writeByte(TC_BLOCKDATA.toInt())
                    writeByte(size)
                } else {
                    writeByte(TC_BLOCKDATALONG.toInt())
                    writeInt(size)
                }
                write(blockData)
            }
        }

        return wasActive
    }

    override fun close() {
        try {
            dataOut.close()
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }

        // Check for pending block data after stream has been closed, in case close() was called as
        // part of exception handling (e.g. try-with-resources)
        check(!isBlockDataModeActive()) { "Stream has pending block data" }
    }
}
