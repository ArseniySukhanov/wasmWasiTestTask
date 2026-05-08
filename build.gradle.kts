@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

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

tasks.register<DefaultTask>("runWasm"){
    group="run"
    description = "Compiles and executes .wasm executable on Node.js"
    finalizedBy("wasmWasiNodeProductionRun")
}
