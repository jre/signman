plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
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
    api(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

sourceSets {
    val main by getting {
        kotlin.srcDirs("src/commonMain/kotlin")
    }
    val test by getting {
        kotlin.srcDirs("src/commonTest/kotlin")
    }
}

tasks.test {
    useJUnitPlatform()
}
