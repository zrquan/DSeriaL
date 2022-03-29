class Handle {
    companion object Access {
        /**
         * 为 Handle 实例分配索引
         */
        fun assignIndex(unassignedHandle: Handle, objIndex: Int) {
            check(!unassignedHandle.isAssigned()) { "Object index has already been assigned: $unassignedHandle" }
            unassignedHandle.objIndex = objIndex
        }

        /**
         * 获取 Handle 实例的索引
         */
        fun getObjectIndex(assignedHandle: Handle): Int {
            check(assignedHandle.isAssigned()) { "Object index has not been assigned yet" }
            return assignedHandle.objIndex!!
        }
    }

    private var objIndex: Int? = null

    fun isAssigned() = objIndex != null

    override fun toString(): String = "Handle{objIndex=${objIndex ?: "?"}}"
}
