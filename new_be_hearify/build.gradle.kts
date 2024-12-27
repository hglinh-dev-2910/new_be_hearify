val exposed_version: String by project
val h2_version: String by project
val kotlin_version: String by project
val logback_version: String by project


plugins {
    kotlin("jvm") version "2.0.21"
    id("io.ktor.plugin") version "3.0.1"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}

group = "com.example"
version = "0.0.1"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    //imple cua ktor, JWT
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-auth-jvm")
    implementation("io.ktor:ktor-server-auth-jwt-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-serialization-jackson-jvm")
    implementation("io.ktor:ktor-server-websockets-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")

    //imple cho db va ORM
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation("org.xerial:sqlite-jdbc:3.47.0.0")
    implementation("com.h2database:h2:$h2_version")

    //server va log
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-config-yaml-jvm")
    implementation("io.ktor:ktor-client-cio:3.0.0")

    //imple cho bao mat(bam password truoc khi luu vao db)
    implementation("org.mindrot:jbcrypt:0.4")

    //imple cho OAuth2 (auth bang gmail)
    implementation("io.ktor:ktor-server-auth-jvm:3.0.0") //update tren https://repo.maven.apache.org/maven2/io/ktor/ktor-server-auth-jvm/
    implementation("io.ktor:ktor-client-core-jvm:3.0.0") //https://repo.maven.apache.org/maven2/io/ktor/ktor-client-core-jvm/
    implementation("io.ktor:ktor-client-apache-jvm:3.0.0") //https://repo.maven.apache.org/maven2/io/ktor/ktor-client-apache-jvm/
    implementation("com.auth0:java-jwt:4.x.x")
    implementation("io.ktor:ktor-server-status-pages:2.x.x")
    implementation("io.ktor:ktor-server-cors:2.x.x")

    //imple cho server test
    testImplementation("io.ktor:ktor-server-test-host-jvm")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    implementation(kotlin("test"))
}

