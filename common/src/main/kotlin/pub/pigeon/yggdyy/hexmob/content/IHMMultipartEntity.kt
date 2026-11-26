package pub.pigeon.yggdyy.hexmob.content

import net.minecraft.world.phys.AABB
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

interface IHMMultipartEntity<T: HMEntityPart> {
    fun getAllParts(): List<T>
    fun getPartsBoundingBox(): AABB? = null
    fun shouldRecord(): Boolean
    companion object {
        val instances: MutableSet<IHMMultipartEntity<*>> =
            Collections.newSetFromMap(ConcurrentHashMap())
        fun updateInstances() {
            // 线程安全迭代清理：ConcurrentHashMap 视图的 removeIf 避免跨线程并发修改异常
            instances.removeIf { !it.shouldRecord() }
        }
    }
}