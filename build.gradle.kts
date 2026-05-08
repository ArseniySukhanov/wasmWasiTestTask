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
