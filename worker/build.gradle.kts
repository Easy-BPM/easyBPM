import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

// Keep the worker's independently managed runtime graph on the same patched versions.
extra["commons-lang3.version"] = "3.18.0"
extra["jackson-bom.version"] = "2.21.6"
extra["log4j2.version"] = "2.25.5"
extra["netty.version"] = "4.1.137.Final"
extra["rabbit-amqp-client.version"] = "5.34.0"
extra["tomcat.version"] = "10.1.60"

group = "com.easy.bpm.worker"
version = "0.1.3-beta.2"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.postgresql:postgresql:42.7.13")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(project(":"))
}

java {
    toolchain {
        languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(21))
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "21"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
