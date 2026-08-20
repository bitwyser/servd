import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Match the Java toolchain's target to Kotlin's (JDK 21 is installed; we emit 17 bytecode).
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":core"))
    // Desktop server engine - Netty supports HTTPS (Ktor CIO does not).
    implementation(libs.ktor.server.netty)
    runtimeOnly(libs.slf4j.simple)
}

application {
    applicationName = "servd"
    mainClass.set("dev.servd.host.MainKt")
}
