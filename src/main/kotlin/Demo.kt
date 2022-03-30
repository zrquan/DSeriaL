fun serial(
    unassignedHandle: Handle = Handle(),
    build: TopLevel.() -> Unit
): ByteArray? =
    with(SerialBuilder()) {
        beginSerializableObject(unassignedHandle)
        build()
        finish()
    }
