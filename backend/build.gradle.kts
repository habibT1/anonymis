plugins {
	java
	id("org.springframework.boot") version "3.5.5"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.google.cloud.tools.jib") version "3.5.1"
}

group = "com.inf5190"
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
	implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	implementation("org.springframework.security:spring-security-crypto")
	implementation("com.google.firebase:firebase-admin:9.7.0")
	implementation("io.jsonwebtoken:jjwt-api:0.13.0")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")
}

tasks.withType<Test> {
	useJUnitPlatform()
	testLogging {
		events("PASSED", "FAILED", "SKIPPED")
	}
}

jib {
	to {
		image = "northamerica-northeast1-docker.pkg.dev/inf-5190-chat-prod-4a971/inf-5190/chat-app"
		val keyFile = file("firebase-production-key.json")
		if (keyFile.exists()) {
			auth {
				username = "_json_key"
				password = keyFile.readText(Charsets.UTF_8)
			}
		}
	}
}
