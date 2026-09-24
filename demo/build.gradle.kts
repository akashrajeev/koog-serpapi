plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":koog-serpapi"))
    implementation("ai.koog:prompt-executor-llms-all:1.3.0-beta")
    implementation("ai.koog:http-client-ktor:1.3.0")
    implementation("io.ktor:ktor-client-cio:3.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}

application {
    mainClass.set("dev.serpapi.koog.demo.MainKt")
}
