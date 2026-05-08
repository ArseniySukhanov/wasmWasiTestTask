@file:OptIn(UnsafeWasmMemoryApi::class)


import readImpl
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

@ExperimentalWasmInterop
@WasmImport("wasi_snapshot_preview1", "fd_fdstat_get")
private external fun wasiRawFdStatGet(descriptor: Int, metadataPtr: Int): Int

@OptIn(ExperimentalWasmInterop::class)
internal fun wasiCheckSeekIn(allocator: MemoryAllocator): Boolean{
    val metadataPtr=allocator.allocate(24)
    val ret=wasiRawFdStatGet(
        descriptor=STDIN,
        metadataPtr=metadataPtr.address.toInt()
    )
    if (ret != 0){
        throw WasiError(WasiErrorCode.entries[ret])
    }
    // Here one checks if second bit is 1. It represents a right to use fd_seek() and fd_pread()
    return (((metadataPtr+8).loadInt() shr 1) and 1)!=0
}

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
                throw WasiError(WasiErrorCode.entries[ret])
        }
        if(rp0.loadInt()==0){
            if(tmpByteList.isEmpty())
            {
                if(nullable) {
                    return null
                }
                throw RuntimeException("Tried to read from the end of file")
            }
            return ByteArray(tmpByteList.size - 1) { i -> tmpByteList[i] }
        }
        tmpByteList.add(ptr.loadByte())
    }while(ptr.loadByte().toInt() != 0x0A)
    if(tmpByteList.size>1 ) {
       if(tmpByteList[tmpByteList.size-2].toInt() == 0x0D)
            return ByteArray(tmpByteList.size - 2) { i -> tmpByteList[i] }
    }
    return ByteArray(tmpByteList.size - 1) { i -> tmpByteList[i] }
}

internal fun readImpl(nullable: Boolean):String?{
    println(withScopedMemoryAllocator { allocator -> wasiCheckSeekIn(allocator=allocator)})
    return withScopedMemoryAllocator { allocator ->
        wasiReadImpl(allocator=allocator, nullable=nullable)
    }?.decodeToString()
}

/**
 * Reads a line of input from the standard input stream and returns it, or throws a RuntimeException if EOF has already
 * been reached when `readln` is called.
 *
 * LF or CRLF is treated as the line terminator. Line terminator is not included in the returned string.
 *
 * The input is interpreted as UTF-8.
 */
fun readln():String{
     return readImpl(nullable = false) as String
}

/**
 * Reads a line of input from the standard input stream and returns it, or return null if EOF has already been reached
 * when `readlnOrNull` is called.
 *
 * LF or CRLF is treated as the line terminator. Line terminator is not included in the returned string.
 */
fun readlnOrNull():String?{
    return readImpl(nullable = true)
}
