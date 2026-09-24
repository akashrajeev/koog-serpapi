plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":koog-serpapi"))
    implementation("ai.koog:prompt-executor-llms-all:1.3.0-beta")
    implementation("ai.koog:http-client-ktor:1.3.0")
    implementation("io.ktor:ktor-client-cio:3.6.0")
    implementation("io.ktor:ktor-server-core:3.6.0")
    implementation("io.ktor:ktor-server-netty:3.6.0")
    implementation("io.ktor:ktor-server-content-negotiation:3.6.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("ch.qos.logback:logback-classic:1.5.18")
}

application {
    mainClass.set("dev.serpapi.koog.demo.ServerKt")
}

tasks.register<JavaExec>("runCli") {
    group = "application"
    description = "Run the CLI demo agent instead of the web demo"
    mainClass.set("dev.serpapi.koog.demo.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
}
