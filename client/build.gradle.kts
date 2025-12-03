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
    api(project(":api"))
    api(libs.asitplus.cidre)
    api(libs.kotlinx.serialization.json)
    api(libs.ktor.client.core)
    implementation(project(":api"))
    implementation(libs.asitplus.cidre)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

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
