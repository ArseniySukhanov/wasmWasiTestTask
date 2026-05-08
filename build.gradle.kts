@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsExec

plugins {
    alias(libs.plugins.multiPlatform)
}

repositories {
    mavenCentral()
}


kotlin{
    wasmWasi {
        nodejs()
        binaries.executable()
    }
}

tasks.named<NodeJsExec>("wasmWasiNodeProductionRun"){
    standardInput=System.`in`
}

tasks.register<DefaultTask>("runWasm"){
    group="run"
    description = "Compiles and executes .wasm executable on Node.js"
    finalizedBy("wasmWasiNodeProductionRun")
}
