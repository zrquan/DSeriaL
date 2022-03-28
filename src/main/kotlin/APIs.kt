typealias Block = () -> Unit

@DslMarker
annotation class DSeriaL

/**
 * NonProxyDescriptor
 */
interface Descriptor {
    fun primitiveFields(build: DescriptorPrimitiveFields.() -> Unit)
}

interface DescriptorPrimitiveFields {
    infix fun String.type(type: Class<*>) {
        val typeName = if (type.isPrimitive)
            type.typeName
        else
            throw Exception("Not a primitive type: ${type.typeName}")

        primitiveField(this, typeName)
    }

    fun primitiveField(name: String, typeName: String)
}

interface SlotPrimitiveFields {
    fun intVal(i: Int)
    fun charVal(c: Char)
    // boolean, byte, short...
}

interface Slot : SlotPrimitiveFields {
    fun primitiveFields(build: SlotPrimitiveFields.() -> Unit)
    // todo: fun objectField()
}

interface Slots {
    fun slot(build: Slot.() -> Unit)
}
