plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

// Sin jvmToolchain: se usa el JVM con el que corre Gradle (el JBR de Android
// Studio, JDK 21). Fijar un toolchain 17 obligaria a instalar ese JDK aparte.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

application {
    mainClass.set("com.bioscanlab.server.MainKt")
}

tasks.named<JavaExec>("run") {
    // Pasar propiedades del sistema y variables de entorno al ejecutar el servidor
    project.findProperty("gemini.apiKey")?.let { systemProperty("gemini.apiKey", it) }
    System.getenv("GEMINI_API_KEY")?.let { environment("GEMINI_API_KEY", it) }
    System.getenv("LLM_PROVIDER")?.let { environment("LLM_PROVIDER", it) }
    System.getenv("GEMINI_MODEL")?.let { environment("GEMINI_MODEL", it) }
    System.getenv("PORT")?.let { environment("PORT", it) }
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.logback.classic)
    testImplementation(kotlin("test"))
}

