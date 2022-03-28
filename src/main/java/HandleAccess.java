public abstract class HandleAccess {
    public static volatile HandleAccess INSTANCE;

    static {
        // Perform any operation to load Handle class and assign INSTANCE
        new Handle();
    }

    public abstract void assignIndex(Handle unassignedHandle, int objectIndex);

    public abstract int getObjectIndex(Handle assignedHandle);
}
