plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

kotlin {
    jvmToolchain(17)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    api(libs.asitplus.cidre)
    implementation(libs.asitplus.cidre)
    implementation(libs.dbus.java.core)
    implementation(libs.dbus.java.transport.native.unixsocket)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
}

@Suppress("UNUSED")
sourceSets {
    val main by getting {
        kotlin.srcDirs("src/commonMain/kotlin", "src/jvmMain/kotlin")
    }
    val test by getting {
        kotlin.srcDirs("src/commonTest/kotlin", "src/jvmTest/kotlin")
    }
}

tasks.test {
    useJUnitPlatform()
}
