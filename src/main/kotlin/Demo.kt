fun serial(
    unassignedHandle: Handle = Handle(),
    build: SerialBuilder.() -> Unit
): ByteArray? =
    with(SerialBuilder()) {
        beginSerializableObject(unassignedHandle)
        build()
        finish()
    }
