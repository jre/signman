import org.gradle.jvm.application.tasks.CreateStartScripts as CreateStartScriptsTask

plugins {
    alias(libs.plugins.gradleup.shadow)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":api"))
    implementation(project(":client"))
    implementation(project(":zeroconf"))
    implementation(libs.ajalt.clikt)
    implementation(libs.ajalt.mordant)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.java)
    implementation(libs.slf4j.simple)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("${group}.${project.name}.MainKt")
}

tasks.withType<Jar> {
    manifest { attributes["Main-Class"] = application.mainClass }
}

tasks.shadowJar {
    archiveBaseName = "${rootProject.name}-${project.name}"
    archiveClassifier = null
}

tasks.named("startShadowScripts", CreateStartScriptsTask::class.java) {
    applicationName = "${rootProject.name}-${project.name}"
}

tasks.named("startScripts", CreateStartScriptsTask::class.java) {
    enabled = false
}

tasks.distTar { enabled = false }
tasks.distZip { enabled = false }
tasks.shadowDistTar { enabled = false }
tasks.shadowDistZip { enabled = false }
