import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
        // cinterop shim exposing NSURLProtectionSpace.serverTrust + [NSURLCredential
        // credentialForTrust:], which the Kotlin/Native Foundation binding omits (they return
        // CoreFoundation/SecTrust types). Needed for TLS certificate pinning in the Darwin HTTP
        // engine — see shared/src/.../core/network/Engine.*.kt + CertPinning.ios.kt.
        iosTarget.compilations.getByName("main").cinterops.create("beaconSecTrust") {
            defFile(project.file("src/nativeInterop/cinterop/beaconSecTrust.def"))
        }
    }

    // TEST-ONLY: the published maplibre-compose carries CI-baked framework search paths, so the
    // iosSimulatorArm64 TEST executable can't find MapLibre.framework (the real app build gets it
    // from Xcode/SPM). Point the test linker + runtime at the SPM-resolved simulator slice from
    // Xcode's DerivedData (populated once the iosApp has been built in Xcode) so on-simulator unit
    // tests (e.g. ImageRedactorIosTest) can link and run. Guarded: if the slice isn't present the
    // opts are simply skipped. Affects ONLY the iosSimulatorArm64 test binary — every other build
    // (the shipped framework, the Xcode app, Android) is untouched.
    run {
        val derivedData = file("${System.getProperty("user.home")}/Library/Developer/Xcode/DerivedData")
        val mlSlice = derivedData.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("iosApp-") }
            ?.map {
                file(
                    "$it/SourcePackages/artifacts/maplibre-gl-native-distribution/MapLibre/" +
                        "MapLibre.xcframework/ios-arm64_x86_64-simulator",
                )
            }
            ?.firstOrNull { it.exists() }
        if (mlSlice != null) {
            iosSimulatorArm64().binaries.getTest("DEBUG")
                .linkerOpts("-F", mlSlice.path, "-framework", "MapLibre", "-rpath", mlSlice.path)
        }
    }

    androidLibrary {
       namespace = "com.stepanok.undp.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()

       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.koin.android)
            implementation(libs.ktor.client.okhttp)
            // Runtime permission + camera/photo activity-result launchers from Compose.
            implementation(libs.androidx.activity.compose)
            // FileProvider for full-resolution camera capture.
            implementation(libs.androidx.core.ktx)
            // Strip GPS / timestamp / device EXIF tags from captured photos (privacy).
            implementation(libs.androidx.exifinterface)
            // MapLibre Android SDK (bundled by maplibre-compose) for real offline-region downloads.
            implementation("org.maplibre.gl:android-sdk:13.0.2")
            // CameraX — live in-app camera preview + capture (CameraXViewfinder composable).
            implementation(libs.androidx.camera.core)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.compose)
            // On-device, offline redaction (B1): ML Kit face detection + text recognition (bundled models).
            implementation(libs.mlkit.face.detection)
            implementation(libs.mlkit.text.recognition)
            implementation(libs.gms.tasks)
            // On-device, offline advisory damage classifier (B2): LiteRT runtime (the TF 2.17-era
            // rebrand of TFLite — same org.tensorflow.lite.Interpreter API; the older 2.16.1 runtime
            // can't run our model's FULLY_CONNECTED v12 op).
            implementation("com.google.ai.edge.litert:litert:1.0.1")
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            // DI
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.composeViewmodel)

            // Navigation (Voyager)
            implementation(libs.voyager.navigator)
            implementation(libs.voyager.screenModel)
            implementation(libs.voyager.tabNavigator)
            implementation(libs.voyager.transitions)
            implementation(libs.voyager.koin)

            // Concurrency / serialization / immutable collections
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.collections.immutable)

            // Ktor client (live backend)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.json)

            // Maps (MapLibre Compose — KMP)
            implementation(libs.maplibre.compose)
            implementation(libs.maplibre.compose.material3)

            // Icons
            implementation(libs.icons.lucide)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
