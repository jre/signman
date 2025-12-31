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
    implementation(project(":zeroconf"))
    implementation(libs.ajalt.clikt)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.properties)
    implementation(libs.faljse.sdnotify)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.caching.headers)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.conditional.headers)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.server.jetty.jakarta)
    implementation(libs.ktor.server.sse)
    implementation(libs.slf4j.simple)
    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.auth)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.client.java)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("${group}.${project.name}.MainKt")
}

tasks.compileJava {
    options.headerOutputDirectory.set(File(projectDir, "native/generated"))
}

distributions {
    create("nativeSource") {
        distributionBaseName = "${rootProject.name}-${project.name}-native"
        contents {
            from("native")
            exclude("**/.so")
        }
    }
}

tasks.named("nativeSourceDistTar") { dependsOn(tasks.compileJava) }
tasks.named("nativeSourceDistZip") { dependsOn(tasks.compileJava) }

tasks.withType<Jar> {
    manifest { attributes["Main-Class"] = application.mainClass }
}

tasks.shadowJar {
    archiveBaseName = "${rootProject.name}-${project.name}"
    archiveClassifier = null
}
