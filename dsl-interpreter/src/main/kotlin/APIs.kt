import java.io.DataOutput

typealias Block = () -> Unit

@DslMarker
annotation class DSeriaL

@DSeriaL
interface TopLevel {
    fun desc(build: DescriptorsBuilder.() -> Unit)
}

interface SerialTopLevel : TopLevel {
    fun values(build: ClassData.() -> Unit)
    fun writeObject(build: StreamBuilder.(DataOutput) -> Unit)
}

interface ExternalContent : TopLevel {
    fun writeExternal(build: StreamBuilder.(DataOutput) -> Unit)
}

@DSeriaL
interface ArrayElements {
    // Use + for adding primitive element, e.g. +10L
    operator fun Any.unaryPlus() = primitiveElements(this)

    fun primitiveElements(elements: Any)

    /**
     * 添加 Object 对象到数组中
     */
    fun elements(build: StreamBuilder.() -> Unit)
}

@DSeriaL
interface ClassData {
    fun int(i: Int)
    fun byte(b: Byte)
    fun char(c: Char)
    fun long(j: Long)
    fun float(f: Float)
    fun short(s: Short)
    fun double(d: Double)
    fun boolean(z: Boolean)

    fun obj(build: StreamBuilder.() -> Unit)
}
