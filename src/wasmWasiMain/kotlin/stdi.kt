@file:OptIn(UnsafeWasmMemoryApi::class)


import kotlin.wasm.unsafe.MemoryAllocator
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

private const val STDIN=0

/**
 * Read from a file descriptor. Note: This is similar to `readv` in POSIX.
 */
@ExperimentalWasmInterop
@WasmImport("wasi_snapshot_preview1", "fd_read")
private external fun wasiRawFdRead(descriptor: Int, scatterPtr: Int, scatterSize: Int, errorPtr: Int): Int

@OptIn(ExperimentalWasmInterop::class)
internal fun wasiReadImpl(
    allocator: MemoryAllocator,
    nullable: Boolean
):ByteArray?{
    val tmpByteList:MutableList<Byte> = mutableListOf()
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
        if (ret!=0) {
            if (nullable){
                return null
            }
            else{
                throw WasiError(WasiErrorCode.entries[ret])
            }
        }
        tmpByteList.add(ptr.loadByte())
    }while(ptr.loadByte().toInt() != 0x0A)
    if(tmpByteList.size>1 ) {
        if(tmpByteList[-2].toInt() != 0x0D)
            return ByteArray(tmpByteList.size - 2) { i -> tmpByteList[i] }
    }
    return ByteArray(tmpByteList.size - 1) { i -> tmpByteList[i] }
}

/**
 * Reads a line of input from the standard input stream and returns it.
 * LF or CRLF is treated as the line terminator. Line terminator is not included in the returned string.
 * The input is interpreted as UTF-8.
 */
fun readln():String{
    return withScopedMemoryAllocator { allocator ->
        wasiReadImpl(allocator=allocator, nullable=false)
    }?.decodeToString() as String
}

fun readlnOrNull():String?{
    return withScopedMemoryAllocator { allocator ->
        wasiReadImpl(allocator=allocator, nullable=true)
    }?.decodeToString()
}
