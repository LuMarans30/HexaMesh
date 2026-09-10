plugins {
    id("com.android.application")
}

val llamaCppPkg = layout.projectDirectory.dir("../llama.cpp/pkg-adb/llama.cpp")
val rustCoreDir = layout.projectDirectory.dir("../hexa_mesh_core")
val nativeStagingDir = layout.buildDirectory.dir("jniLibs")
val arm64StagingDir = nativeStagingDir.map { it.dir("arm64-v8a") }
val skipRust = providers.gradleProperty("skipRustBuild").isPresent
val updateRustDeps = providers.gradleProperty("updateRustDeps").isPresent

android {
    namespace = "com.lumarans30.hexamesh"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lumarans30.hexamesh"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.2.0"
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    sourceSets.getByName("main") {
        jniLibs.directories.add(nativeStagingDir.get().asFile.absolutePath)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}

val copyLlamaServer = tasks.register<Copy>("copyLlamaServer") {
    from(llamaCppPkg.file("bin/llama-server"))
    rename { "libllamaserver.so" }
    into(arm64StagingDir)
}

val copyLlamaLibs = tasks.register<Copy>("copyLlamaLibs") {
    from(llamaCppPkg.dir("lib")) {
        include("*.so")
    }
    into(arm64StagingDir)
}

val cargoUpdate = if (rustCoreDir.asFile.exists() && updateRustDeps) {
    tasks.register<Exec>("cargoUpdate") {
        group = "rust"
        description = "Runs `cargo update` in hexa_mesh_core"

        workingDir = rustCoreDir.asFile
        commandLine("cargo", "update")

        outputs.upToDateWhen { false }
    }
} else null

val buildRustCore = if (rustCoreDir.asFile.exists() && !skipRust) {
    tasks.register<Exec>("buildRustCore") {
        group = "build"
        description = "Builds the Rust core for aarch64-linux-android"
        workingDir = rustCoreDir.asFile

        if (cargoUpdate != null) {
            dependsOn(cargoUpdate)
        }

        inputs.files(
            fileTree(rustCoreDir) {
                include("Cargo.toml", "Cargo.lock", "build.rs", "src/**/*.rs")
            }
        )
        outputs.file(arm64StagingDir.map { it.file("libhexa_mesh_core.so") })

        commandLine(
            "cargo", "ndk",
            "-t", "arm64-v8a",
            "-o", nativeStagingDir.get().asFile.absolutePath,
            "build", "--release"
        )

        providers.environmentVariable("ANDROID_NDK_HOME").orNull?.let { ndk ->
            environment("ANDROID_NDK_HOME", ndk)
        }
    }
} else null

tasks.named("preBuild") {
    dependsOn(copyLlamaServer, copyLlamaLibs)
    if (buildRustCore != null) {
        dependsOn(buildRustCore)
    }
}