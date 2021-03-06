package org.mapdb

import org.eclipse.collections.api.list.primitive.MutableLongList
import org.eclipse.collections.impl.list.mutable.primitive.LongArrayList
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap
import org.eclipse.collections.impl.set.mutable.primitive.LongHashSet
import org.fest.reflect.core.Reflection
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import org.mapdb.StoreDirectJava.*
import org.mapdb.DataIO.*
import org.mapdb.volume.Volume
import org.mapdb.volume.VolumeFactory
import java.util.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReadWriteLock

class StoreDirectTest:StoreDirectAbstractTest(){


    override fun openStore(file: File): StoreDirect {
        return StoreDirect.make(file.path)
    }

    override fun openStore(): StoreDirect {
        return StoreDirect.make()
    }

    @Test fun constants(){
        assertEquals(0, MAX_RECORD_SIZE%16)
        assertEquals(3*8, DATA_TAIL_OFFSET)
        assertEquals(4*8, INDEX_TAIL_OFFSET)
        assertEquals(5*8, FILE_TAIL_OFFSET)
        assertEquals(8*8, RECID_LONG_STACK)
        assertEquals(8*(8+4095+1), UNUSED1_LONG_STACK)

        assertEquals(8*(8+4095+4+1), HEAD_END)
    }


    @Test fun linked_getSet(){
        fun test(size:Int) {
            val b = TT.randomByteArray(size, 1)
            val s = openStore()
            val indexVal = s.linkedRecordPut(b, b.size)
            assertTrue(s.indexValFlagLinked(indexVal))
            assertTrue(indexValToSize(indexVal) > 0)
            assertTrue(indexValToOffset(indexVal) != 0L)

            val b2 = s.linkedRecordGet(indexVal)
            assertArrayEquals(b, b2)
        }
        test(100000)
        test(1000000)
        test(10000000)
    }

    @Test fun freeSpace(){
        val count = 100000
        val arraySize = 1024
        val div = count * arraySize / 100

        val s = openStore()
        val recids = LongHashSet()
        for(i in 0..count){
            val recid = s.put(ByteArray(arraySize), Serializer.BYTE_ARRAY_NOSIZE)
            recids.add(recid)
        }

        recids.forEach { recid->
            s.delete(recid, Serializer.BYTE_ARRAY_NOSIZE)
        }

        assertTrue( Math.abs(count*arraySize - s.getFreeSize())<div)
        s.structuralLock!!.lock()
        assertEquals(s.getFreeSize(), s.calculateFreeSize())
    }


    @Test fun freeSpace2() {
        val count = 100000
        val arraySize = 1024
        val div = count * arraySize / 100

        val s = openStore()
        val recids = LongHashSet()
        s.getFreeSize()
        for (i in 0..count) {
            val recid = s.put(ByteArray(arraySize), Serializer.BYTE_ARRAY_NOSIZE)
            recids.add(recid)
        }

        recids.forEach { recid ->
            s.delete(recid, Serializer.BYTE_ARRAY_NOSIZE)
        }

        assertTrue(Math.abs(count * arraySize - s.getFreeSize()) < div)
        s.structuralLock!!.lock()
        assertEquals(s.getFreeSize(), s.calculateFreeSize())
    }
}

abstract class StoreDirectAbstractTest:StoreReopenTest() {
    abstract override fun openStore(file: File): StoreDirectAbstract

    abstract override fun openStore(): StoreDirectAbstract

    override val headerType: Long = CC.FILE_TYPE_STOREDIRECT

    val StoreDirectAbstract.maxRecid:Long
        get() =  Reflection.method("getMaxRecid").withReturnType(Long::class.java).`in`(this).invoke()

    val StoreDirectAbstract.dataTail:Long
        get() =  Reflection.method("getDataTail").withReturnType(Long::class.java).`in`(this).invoke()

    val StoreDirectAbstract.volume: Volume
        get() = Reflection.method("getVolume").withReturnType(Volume::class.java).`in`(this).invoke()

    val StoreDirectAbstract.indexPages: MutableLongList
        get() = Reflection.method("getIndexPages").withReturnType(MutableLongList::class.java).`in`(this).invoke()

    val StoreDirectAbstract.structuralLock: Lock?
        get() = Reflection.method("getStructuralLock").`in`(this).invoke() as Lock?


    val StoreDirectAbstract.locks: Array<ReadWriteLock?>
        get() = Reflection.method("getLocks").`in`(this).invoke() as Array<ReadWriteLock?>

    fun StoreDirectAbstract.indexValCompose(size:Long,
                                            offset:Long,
                                            linked:Int,
                                            unused:Int,
                                            archive:Int
    ):Long = Reflection.method("indexValCompose")
            .withParameterTypes(size.javaClass, offset.javaClass, linked.javaClass, unused.javaClass, archive.javaClass)
            .`in`(this)
            .invoke(size, offset, linked, unused, archive) as Long


    fun StoreDirectAbstract.allocateNewPage():Long =
            Reflection.method("allocateNewPage")
                    .`in`(this)
                    .invoke() as Long

    fun StoreDirectAbstract.allocateRecid():Long =
            Reflection.method("allocateRecid")
                    .`in`(this)
                    .invoke() as Long


    fun StoreDirectAbstract.calculateFreeSize():Long =
            Reflection.method("calculateFreeSize")
                    .`in`(this)
                    .invoke() as Long

    fun StoreDirectAbstract.allocateNewIndexPage():Long =
            Reflection.method("allocateNewIndexPage")
                    .`in`(this)
                    .invoke() as Long


    fun StoreDirectAbstract.getIndexVal(recid:Long):Long =
            Reflection.method("getIndexVal")
                    .withParameterTypes(recid.javaClass)
                    .`in`(this)
                    .invoke(recid) as Long

    fun StoreDirectAbstract.recidToOffset(recid:Long):Long =
            Reflection.method("recidToOffset")
                    .withParameterTypes(recid.javaClass)
                    .`in`(this)
                    .invoke(recid) as Long

    fun StoreDirectAbstract.allocateData(size:Int, recursive:Boolean):Long =
            Reflection.method("allocateData")
                    .withParameterTypes(size.javaClass, recursive.javaClass)
                    .`in`(this)
                    .invoke(size, recursive) as Long

    fun StoreDirectAbstract.longStackTake(masterLinkOffset:Long, recursive:Boolean):Long =
            Reflection.method("longStackTake")
                    .withParameterTypes(masterLinkOffset.javaClass, recursive.javaClass)
                    .`in`(this)
                    .invoke(masterLinkOffset, recursive) as Long

    fun StoreDirectAbstract.longStackPut(masterLinkOffset:Long, value:Long, recursive:Boolean) {
        Reflection.method("longStackPut")
                .withParameterTypes(masterLinkOffset.javaClass, value.javaClass, recursive.javaClass)
                .`in`(this)
                .invoke(masterLinkOffset, value, recursive)
    }

    fun StoreDirectAbstract.linkedRecordPut(output:ByteArray, size:Int):Long =
            Reflection.method("linkedRecordPut")
                    .withParameterTypes(output.javaClass, size.javaClass)
                    .`in`(this)
                    .invoke(output, size) as Long

    fun StoreDirectAbstract.indexValFlagLinked(indexValue:Long):Boolean =
            Reflection.method("indexValFlagLinked")
                    .withParameterTypes(indexValue.javaClass)
                    .`in`(this)
                    .invoke(indexValue) as Boolean

    fun StoreDirectAbstract.linkedRecordGet(indexValue:Long):ByteArray =
            Reflection.method("linkedRecordGet")
                    .withParameterTypes(indexValue.javaClass)
                    .`in`(this)
                    .invoke(indexValue) as ByteArray


    @Test fun init_values(){
        val s = openStore()
        assertEquals(CC.PAGE_SIZE, s.fileTail)
        assertEquals(0L, s.maxRecid)
        assertEquals(0L, s.dataTail)
        assertEquals(CC.PAGE_SIZE, s.volume.length())

        for(masterLinkOffset in RECID_LONG_STACK until HEAD_END step 8){
            assertEquals(0L, parity4Get(s.volume.getLong(masterLinkOffset)))
        }

        //zero index page is set to zero
        assertEquals(0L, parity16Get(s.volume.getLong(HEAD_END)))
    }


    @Test fun prealloc1(){
        val s = openStore()
        val recid = s.preallocate()
        assertEquals(1L, recid)
        assertTrue(s.indexPages.isEmpty)

        assertEquals(1, s.maxRecid)
        assertEquals(0L, s.dataTail)
        assertEquals(1L * CC.PAGE_SIZE, s.volume.length())
        s.verify()
        s.locks.forEach { it?.readLock()?.lock() }
        assertEquals(
                s.indexValCompose(size=NULL_RECORD_SIZE, offset=0L, linked=0, archive=1, unused=1),
                s.getIndexVal(1L))
    }

    @Test fun indexValCompose(){
        val s = openStore()
        assertEquals(32L.shl(48), s.indexValCompose(size=32L, offset=0L, linked=0, unused=0, archive=0))
        assertEquals(1024, s.indexValCompose(size=0, offset=1024L, linked=0, unused=0, archive=0))
        assertEquals(1.shl(3), s.indexValCompose(size=0L, offset=0L, linked=1, unused=0, archive=0))
        assertEquals(1.shl(1), s.indexValCompose(size=0L, offset=0L, linked=0, unused=0, archive=1))
        assertEquals(1.shl(2), s.indexValCompose(size=0L, offset=0L, linked=0, unused=1, archive=0))
    }

    @Test fun allocate_new_page(){
        val s = openStore()
        s.structuralLock?.lock()
        for(i in 1L until 16) {
            assertEquals(i * CC.PAGE_SIZE, s.volume.length())
            assertEquals(i * CC.PAGE_SIZE, s.allocateNewPage())
            s.commit()
        }
    }

    @Test fun recidToOffset_convert(){
        val s = openStore()
        s.structuralLock?.lock()
        s.allocateNewIndexPage();
        s.allocateNewIndexPage();
        s.allocateNewIndexPage();
        var oldOffset = 0L
        for(recid in 1L .. 3*1024*1024/10){
            val offset = s.recidToOffset(recid)
            assertTrue(offset%CC.PAGE_SIZE>=16)
            assertTrue(offset>oldOffset)
            oldOffset=offset
        }
    }

    @Test fun recid2Offset() {
        val e = openStore()

        //create 2 fake index pages
        e.volume.ensureAvailable(CC.PAGE_SIZE * 12)
        e.indexPages.add(CC.PAGE_SIZE * 3)
        e.indexPages.add(CC.PAGE_SIZE * 6)
        e.indexPages.add(CC.PAGE_SIZE * 11)


        //control bitset with expected recid layout
        val b = BitSet((CC.PAGE_SIZE * 7).toInt())
        //fill bitset at places where recids should be
        b.set(HEAD_END.toInt() + 16, CC.PAGE_SIZE.toInt())
        b.set(CC.PAGE_SIZE.toInt() * 3 + 16, CC.PAGE_SIZE.toInt() * 4)
        b.set(CC.PAGE_SIZE.toInt() * 6 + 16, CC.PAGE_SIZE.toInt() * 7)
        b.set(CC.PAGE_SIZE.toInt() * 11 + 16, CC.PAGE_SIZE.toInt() * 12)

        //bitset with recid layout generated by recid2Offset
        val b2 = BitSet((CC.PAGE_SIZE * 7).toInt())
        var oldOffset: Long = 0
        var recid: Long = 1
        recidLoop@ while (true) {
            val offset = e.recidToOffset(recid)

            assertTrue(b.get(offset.toInt()))
            assertTrue(oldOffset < offset)
            oldOffset = offset
            b2.set(offset.toInt(), offset.toInt() + 8)
            if (offset == CC.PAGE_SIZE * 12 - 8)
                break@recidLoop
            recid++
        }

        for (offset in 0..b.length() - 1) {
            if (b.get(offset) != b2.get(offset))
                throw AssertionError("error at offset " + offset)
        }
    }


    @Test fun allocate_index_page(){
        val s = openStore()
        s.structuralLock?.lock()
        val c = LongArrayList()
        var prevOffset = ZERO_PAGE_LINK
        assertEquals(0, parity16Get(s.volume.getLong(prevOffset)))
        for(i in 1L until 16) {
            assertEquals(c, s.indexPages)
            assertEquals(i * CC.PAGE_SIZE, s.volume.length())
            val indexPage = s.allocateNewIndexPage();
            s.commit()
            assertEquals(i * CC.PAGE_SIZE, indexPage)
            c.add(indexPage)
            assertEquals(c, s.indexPages)
            assertEquals(indexPage, parity16Get(s.volume.getLong(prevOffset)))
            prevOffset = indexPage+8
            assertEquals(0, parity16Get(s.volume.getLong(prevOffset)))
        }
        s.commit()

        //open new engine over the same volune, check it has the same index pages
        val s2 = StoreDirect.make(volumeFactory = VolumeFactory.wrap(s.volume,true))
        assertEquals(c, s2.indexPages)
    }

    @Test fun allocate_recid(){
        val s = openStore()
        s.structuralLock?.lock()
        for(recid in 1L .. 100000L){
            assertEquals(recid, s.allocateRecid())
        }
    }

    @Test fun alloc_data(){
        val s = openStore()
        s.structuralLock?.lock()
        for(offset in CC.PAGE_SIZE until CC.PAGE_SIZE*2 step 1024){
            assertEquals(offset, s.allocateData(1024,false))
            var expectedTail = offset+1024
            if(expectedTail == CC.PAGE_SIZE*2)
                expectedTail = 0L
            assertEquals(expectedTail, s.dataTail)
            assertEquals(CC.PAGE_SIZE*2, s.fileTail)
        }
        assertEquals(CC.PAGE_SIZE*2, s.fileTail)
    }

    @Test fun alloc_data_overflow(){
        val s = openStore()
        s.structuralLock?.lock()
        //fill until rim
        for(offset in CC.PAGE_SIZE until CC.PAGE_SIZE*2-1024 step 1024){
            assertEquals(offset, s.allocateData(1024, false))
        }
        assertEquals(CC.PAGE_SIZE*2, s.fileTail)

        //allocate smaller
        assertEquals(CC.PAGE_SIZE*2-1024, s.allocateData(16, false))
        assertEquals(CC.PAGE_SIZE*2-1024+16, s.dataTail)

        //allocate which will cause overflow
        assertEquals(CC.PAGE_SIZE*2+LONG_STACK_PREF_SIZE, s.allocateData(1024,false))
        assertEquals(CC.PAGE_SIZE*2+1024+LONG_STACK_PREF_SIZE, s.dataTail)
        assertEquals(CC.PAGE_SIZE*3, s.fileTail)
        //TODO once free space works, make sure that `CC.PAGE_SIZE*2-1024+16` is free
    }


    @Test fun longStack_putTake(){
        val s = openStore()
        s.structuralLock?.lock()
        assertEquals(0, s.longStackTake(UNUSED1_LONG_STACK,false))
        s.longStackPut(UNUSED1_LONG_STACK, 160,false)
        assertEquals(160, s.longStackTake(UNUSED1_LONG_STACK,false))
        assertEquals(0, s.longStackTake(UNUSED1_LONG_STACK,false))
    }

    @Test fun longStack_putTake2(){
        val s = openStore()
        s.structuralLock?.lock()
        assertEquals(0, s.longStackTake(UNUSED1_LONG_STACK,false))
        s.longStackPut(UNUSED1_LONG_STACK, 160L,false)
        s.longStackPut(UNUSED1_LONG_STACK, 320L,false)
        assertEquals(320L, s.longStackTake(UNUSED1_LONG_STACK,false))
        assertEquals(160L, s.longStackTake(UNUSED1_LONG_STACK,false))
        assertEquals(0, s.longStackTake(UNUSED1_LONG_STACK,false))
    }

    @Test fun longStack_putTake_many() {
        val max2 = 10000L
        val min2 = if(TT.shortTest()) max2 else 1
        val s = openStore()
        s.structuralLock?.lock()
        for(a in 1 .. 10) {
            for(max in min2..max2) {
                for (i in 1L..max) {
                    s.longStackPut(UNUSED1_LONG_STACK, i * 16, false)
                }
                for (i in max downTo  1L) {
                    val t = s.longStackTake(UNUSED1_LONG_STACK, false)
                    assertEquals(i * 16, t)
                }
                assertEquals(0L, s.longStackTake(UNUSED1_LONG_STACK, false))
            }
        }
    }

    @Test fun longStack_triple(){
        val vals = longArrayOf(16L, 160L, 32000L) //various packed sizes
        val s = openStore()
        s.structuralLock?.lock()

        for(v1 in vals) for (v2 in vals) for(v3 in vals){
            s.longStackPut(UNUSED1_LONG_STACK, v1, false)
            s.longStackPut(UNUSED1_LONG_STACK, v2, false)
            s.longStackPut(UNUSED1_LONG_STACK, v3, false)
            assertEquals(v3, s.longStackTake(UNUSED1_LONG_STACK, false))
            assertEquals(v2, s.longStackTake(UNUSED1_LONG_STACK, false))
            assertEquals(v1, s.longStackTake(UNUSED1_LONG_STACK, false))
            assertEquals(0L, s.longStackTake(UNUSED1_LONG_STACK, false))
        }
    }




    @Test fun freeSpace3(){
        val db = DBMaker.memoryDB().make()
        val store = db.store as StoreDirect
        val map = db.hashMap("map",Serializer.LONG, Serializer.BYTE_ARRAY).create()

        val random = Random()
        for(i in 0..10) for(key in 1L .. 10000){
            map.put(key, ByteArray(800))
            assertEquals( Utils.lock(store.structuralLock) {store.calculateFreeSize()}, store.getFreeSize() )
        }
    }

    @Test fun compact(){
        val store = openStore();

        val ref = LongObjectHashMap<ByteArray>()
        //insert random records
        val random = Random()
        for(i in 1..1000){
            val string = TT.randomByteArray(size = random.nextInt(100000), seed=random.nextInt())
            val recid = store.put(string, Serializer.BYTE_ARRAY_NOSIZE)
            ref.put(recid,string)
        }
        val nullRecid = store.put(null, Serializer.BYTE_ARRAY_NOSIZE);

        store.compact()
        store.verify()

        assertEquals(ref.size()+1, store.getAllRecids().asSequence().count())
        store.getAllRecids().asSequence().forEach { recid->
            assertTrue(ref.containsKey(recid)|| recid==nullRecid)
        }

        ref.forEachKeyValue { key, value ->
            val value2 = store.get(key, Serializer.BYTE_ARRAY_NOSIZE)
            assertTrue(Arrays.equals(value,value))
        }

        assertNull(store.get(nullRecid,Serializer.BYTE_ARRAY_NOSIZE))
    }
}
