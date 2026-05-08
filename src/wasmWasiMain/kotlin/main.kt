private const val STDIN=0

@ExperimentalWasmInterop
@WasmImport("wasi_snapshot_preview1", "fd_read")
external fun wasiRawFdRead(descriptor: Int, scatterPtr: Int, scatterSize: Int, errorPtr: Int): Int

fun readln2():String{
    return ""
}

fun main(){
    var s: String
    while (true){
        s=readln2()
        println(s)
    }
}