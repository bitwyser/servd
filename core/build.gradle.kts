import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
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
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        // Plain-JVM code (e.g. java.net enumeration) shared by BOTH JVM targets —
        // Android and desktop. This is the "jvmShared" intermediate from the plan.
        val jvmSharedMain by creating {
            dependsOn(commonMain)
        }
        val androidMain by getting {
            dependsOn(jvmSharedMain)
        }
        val desktopMain by getting {
            dependsOn(jvmSharedMain)
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
