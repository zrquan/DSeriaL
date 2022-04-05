/**
 * Start creating a new object implementing [java.io.Serializable].
 */
@DSeriaL
fun Serial(
    unassignedHandle: Handle = Handle(),
    build: SerialTopLevel.() -> Unit
): ByteArray? =
    with(StreamBuilder()) {
        beginSerializableObject(unassignedHandle)
        build()
        finish()
    }

/**
 * Start creating a new object implementing [java.io.Externalizable].
 */
@DSeriaL
fun External(
    unassignedHandle: Handle = Handle(),
    build: ExternalTopLevel.() -> Unit
): ByteArray? =
    with(StreamBuilder()) {
        beginSerializableObject(unassignedHandle)
        build()
        finish()
    }
