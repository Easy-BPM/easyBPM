import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	id("org.springframework.boot") version "3.5.3"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "1.9.25"
}

group = "com.easy"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("com.vladmihalcea:hibernate-types-60:2.21.1")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.postgresql:postgresql:42.7.1")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-amqp")
	implementation("org.flywaydb:flyway-core:9.16.1")
	implementation("com.vladmihalcea:hibernate-types-60:2.21.1")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testImplementation("com.h2database:h2")
	implementation("org.jboss.logging:jboss-logging:3.5.3.Final")
}

kotlin {
	jvmToolchain(17)
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		jvmTarget = "17"
		freeCompilerArgs += "-Xjsr305=strict"
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
