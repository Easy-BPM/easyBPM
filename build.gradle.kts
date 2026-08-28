import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	id("org.springframework.boot") version "3.5.3"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "1.9.25"
}

group = "com.easy"
version = "0.1.1-beta.2"

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
	implementation("org.postgresql:postgresql:42.7.11")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-amqp")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("io.micrometer:micrometer-core")
	implementation("io.micrometer:micrometer-registry-prometheus")
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.2.0")
	implementation("io.jsonwebtoken:jjwt-api:0.12.5")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testImplementation("com.h2database:h2")
	// TestContainers for PostgreSQL integration tests
	testImplementation("org.testcontainers:testcontainers:1.19.7")
	testImplementation("org.testcontainers:postgresql:1.19.7")
	testImplementation("org.testcontainers:junit-jupiter:1.19.7")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	// Kotest and Mockk for unit testing
	testImplementation("io.kotest:kotest-runner-junit5:5.8.1")
	testImplementation("io.kotest:kotest-assertions-core:5.8.1")
	testImplementation("io.mockk:mockk:1.13.8")
}

kotlin {
	jvmToolchain(21)
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
	sourceSets.named("test") {
		kotlin.exclude(
			"com/easy/bpm/integration/ProcessService*AITaskIntegrationTest.kt"
		)
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		jvmTarget = "21"
		freeCompilerArgs += "-Xjsr305=strict"
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.processResources {
	from("easy-bpm-modeler/qa-processes") {
		into("modeler-qa/qa-processes")
	}
}
