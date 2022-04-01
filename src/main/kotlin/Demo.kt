/**
 * Start creating a new object implementing [java.io.Serializable].
 */
fun Serial(
    unassignedHandle: Handle = Handle(),
    build: SerialTopLevel.() -> Unit
): ByteArray? =
    with(SerialBuilder()) {
        beginSerializableObject(unassignedHandle)
        build()
        finish()
    }

/**
 * Start creating a new object implementing [java.io.Externalizable].
 */
fun External(
    unassignedHandle: Handle = Handle(),
    build: ExternalTopLevel.() -> Unit
): ByteArray? =
    with(SerialBuilder()) {
        beginSerializableObject(unassignedHandle)
        build()
        finish()
    }
