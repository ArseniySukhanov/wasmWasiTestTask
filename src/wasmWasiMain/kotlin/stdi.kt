@file:OptIn(UnsafeWasmMemoryApi::class)


import kotlin.wasm.unsafe.MemoryAllocator
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

private const val STDIN=0

private const val PREADSIZE=32
private const val PREADSCATER=1

private const val WHENCECUR=1 // Don't know for sure but `WasmImport` works only for functions.

/**
 * Read from a file descriptor. Note: This is similar to `readv` in POSIX.
 */
@ExperimentalWasmInterop
@WasmImport("wasi_snapshot_preview1", "fd_read")
private external fun wasiRawFdRead(descriptor: Int, scatterPtr: Int, scatterSize: Int, errorPtr: Int): Int

/**
 * Read from a file descriptor with offset without moving file descriptor offset. Note:
 * This is similar to `preadv` in POSIX.
 */
@ExperimentalWasmInterop
@WasmImport("wasi_snapshot_preview1", "fd_pread")
private external fun wasiRawFdPRead(descriptor: Int, scatterPtr: Int, scatterSize: Int, offset:Int, errorPtr: Int): Int

/**
 * Move the offset of a file descriptor. Note: This is similar to `lseek` in POSIX.
 */
@ExperimentalWasmInterop
@WasmImport("wasi_snapshot_preview1", "fd_seek")
private external fun wasiRawFdSeek(descriptor: Int, offset:Int, whence:Int, newOffset:Int):Int

/**
 * Get the attributes of a file descriptor. Note: This returns similar flags to `fcntl(fd, F_GETFL)` in POSIX, as well
 * as additional fields.
 */
@ExperimentalWasmInterop
@WasmImport("wasi_snapshot_preview1", "fd_fdstat_get")
private external fun wasiRawFdStatGet(descriptor: Int, metadataPtr: Int): Int

/**
 * Checks if standard input file descriptor has a right to use `fd_seek`
 */
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
    return (((metadataPtr+8).loadInt() shr 2) and 1)!=0
}

/**
 * Moves offset for file descriptor by `offset` from a current position
 */
@OptIn(ExperimentalWasmInterop::class)
internal fun wasiSeekImpl(allocator: MemoryAllocator,offset:Int){
    val newOffset=allocator.allocate(4)
    val ret = wasiRawFdSeek(
        descriptor = STDIN,
        offset=offset,
        whence=WHENCECUR,
        newOffset=newOffset.address.toInt()
    )
    if (ret != 0) {
        throw WasiError(WasiErrorCode.entries[ret])
    }
}

/**
 * Function to read from standard input byte by byte until /n or EOF
 */
@OptIn(ExperimentalWasmInterop::class)
internal fun wasiPReadImpl(
    allocator: MemoryAllocator,
    nullable: Boolean
):ByteArray?{
    var pos=0
    var tmpByteArray:ByteArray = emptyArray<Byte>().toByteArray()
    val ptr=allocator.allocate(PREADSIZE)
    val scatterPtr=allocator.allocate(8)
    (scatterPtr+0).storeInt(ptr.address.toInt())
    (scatterPtr+4).storeInt(PREADSIZE)

    val rp0 = allocator.allocate(4)
    do {
        val ret = wasiRawFdPRead(
            descriptor = STDIN,
            scatterPtr = scatterPtr.address.toInt(),
            scatterSize = PREADSCATER,
            offset = pos,
            errorPtr = rp0.address.toInt()
        )
        if (ret != 0) {
            throw WasiError(WasiErrorCode.entries[ret])
        }
        val readByteArray = ByteArray(rp0.loadInt()){i -> (ptr+i).loadByte()}
        if (0x0A.toByte() in readByteArray){
            val lnPos=readByteArray.indexOf(0x0A.toByte())
            pos+=lnPos
            tmpByteArray+=readByteArray.sliceArray(0 until lnPos)
            if(tmpByteArray.last()==0x0D.toByte()) {
                wasiSeekImpl(allocator=allocator,offset=--pos)
                return tmpByteArray.sliceArray(0 until tmpByteArray.lastIndex)
            }
            wasiSeekImpl(allocator=allocator,offset=pos)
                return tmpByteArray
        }
        tmpByteArray+=readByteArray
        pos+=rp0.loadInt()
    }while ( rp0.loadInt()==PREADSIZE )
    if (rp0.loadInt() == 0 && tmpByteArray.isEmpty()) {
        if (nullable) {
            return null
        }
        throw RuntimeException("Tried to read from the end of file")
    }
    wasiSeekImpl(allocator=allocator,offset=pos)
    return tmpByteArray
}

/**
 * Function to read from standard input, using fd_seek and fd_pread to read in chunks of multiple bytes until /n or EOF
 * NOT TESTED!!!
 */
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
    if(withScopedMemoryAllocator { allocator -> wasiCheckSeekIn(allocator=allocator)}) {
        return withScopedMemoryAllocator { allocator ->
            wasiPReadImpl(allocator=allocator, nullable=nullable)
        }?.decodeToString()
    }
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
