typealias Block = () -> Unit

@DslMarker
annotation class DSeriaL

@DSeriaL
interface Descriptor {
    fun primitiveFields(build: DescriptorPrimitiveFields.() -> Unit)
    fun objectFields(build: DescriptorObjectFields.() -> Unit)
}

@DSeriaL
interface DescriptorPrimitiveFields {
    infix fun String.ptype(type: Class<*>) {
        check(type.isPrimitive) { "Not a primitive type: ${type.typeName}" }
        primitiveField(this, type.typeName)
    }

    fun primitiveField(name: String, typeName: String)
}

@DSeriaL
interface DescriptorObjectFields {
    infix fun String.otype(type: Class<*>) {
        check(!type.isPrimitive) { "Not an object type: ${type.typeName}" }
        objectField(this, type.typeName)
    }

    fun objectField(name: String, typeName: String)
}

@DSeriaL
interface TopLevel {
    fun descriptors(build: DescriptorsBuilder.() -> Unit)
    fun slot(build: Slot.() -> Unit)
}

@DSeriaL
interface SlotPrimitiveFields {
    fun intVal(i: Int)
    fun byteVal(b: Byte)
    fun charVal(c: Char)
    fun longVal(j: Long)
    fun floatVal(f: Float)
    fun shortVal(s: Short)
    fun doubleVal(d: Double)
    fun booleanVal(z: Boolean)
}

@DSeriaL
interface Slot {
    fun primitiveFields(build: SlotPrimitiveFields.() -> Unit)
    fun objectFields(build: SerialBuilder.() -> Unit)
}
