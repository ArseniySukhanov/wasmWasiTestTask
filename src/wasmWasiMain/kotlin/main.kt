@file:OptIn(UnsafeWasmMemoryApi::class)

import kotlin.wasm.unsafe.MemoryAllocator
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

private const val STDIN=0

@ExperimentalWasmInterop
@WasmImport("wasi_snapshot_preview1", "fd_read")
external fun wasiRawFdRead(descriptor: Int, scatterPtr: Int, scatterSize: Int, errorPtr: Int): Int

@OptIn(ExperimentalWasmInterop::class)
internal fun wasiReadImpl(
    allocator: MemoryAllocator,
    nullable: Boolean
):ByteArray?{
    val res:MutableList<Byte> = mutableListOf()
    val ptr=allocator.allocate(1)
    val scatterPtr=allocator.allocate(8)
    (scatterPtr+0).storeInt(ptr.address.toInt())
    (scatterPtr+4).storeInt(1)

    val rp0 = allocator.allocate(4)
    do{
        val ret = wasiRawFdRead(
            descriptor = STDIN,
            scatterPtr = scatterPtr.address.toInt(),
            scatterSize = 1,
            errorPtr = rp0.address.toInt()
        )
        res.add(ptr.loadByte())
    }while(ptr.loadByte().toInt() !=0x0A)
    res.removeLast()
    return ByteArray(res.size){i -> res[i]}
}

fun readln():String{
    return withScopedMemoryAllocator { allocator ->
        wasiReadImpl(allocator=allocator, nullable=false)
    }?.decodeToString() as String
}

fun main(){
    var s: String
    while (true){
        s=readln()
        println("Wasm received: $s")
    }
}