class Handle {
    companion object Access {
        fun assignIndex(unassignedHandle: Handle, objIndex: Int) {
            check(!unassignedHandle.isAssigned()) { "Object index has already been assigned: $unassignedHandle" }
            unassignedHandle.objIndex = objIndex
        }

        fun getObjectIndex(assignedHandle: Handle): Int {
            check(assignedHandle.isAssigned()) { "Object index has not been assigned yet" }
            return assignedHandle.objIndex!!
        }
    }

    private var objIndex: Int? = null

    fun isAssigned() = objIndex != null

    override fun toString(): String = "Handle{objIndex=${objIndex ?: "?"}}"
}
