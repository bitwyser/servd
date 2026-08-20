import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        // Plain-JVM code (e.g. java.net enumeration, the Ktor server) shared by BOTH
        // JVM targets - Android and desktop. This is the "jvmShared" intermediate.
        val jvmSharedMain by creating {
            dependsOn(commonMain)
            dependencies {
                // Engine-agnostic Ktor server (routing, TLS material). The concrete engine
                // (Netty on desktop, TBD on Android) is provided by each platform module.
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.websockets)
                implementation(libs.ktor.network.tls.certificates)
            }
        }
        val androidMain by getting {
            dependsOn(jvmSharedMain)
        }
        val desktopMain by getting {
            dependsOn(jvmSharedMain)
            dependencies {
                // mDNS advertisement/discovery on desktop (Android uses NsdManager in Phase 8).
                implementation(libs.jmdns)
                // SSH/SFTP server on desktop (heavy; kept off the Android target).
                implementation(libs.sshd.core)
                implementation(libs.sshd.sftp)
            }
        }
    }
}

android {
    namespace = "dev.servd.core"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
