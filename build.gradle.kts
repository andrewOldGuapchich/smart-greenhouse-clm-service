plugins {
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "2.0.10"
    kotlin("plugin.spring") version "1.9.20"
    id("pl.allegro.tech.build.axion-release") version "1.15.0"
}

group = "com.andrew.smart_greenhouse.clm"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2023.0.0")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.apache.kafka:kafka-clients")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.2")
    implementation("org.slf4j:slf4j-api:2.0.6")
    implementation("org.springframework.kafka:spring-kafka:3.3.10")
    implementation("com.andrew.smart_greenhouse.api:clm-api:1.0-SNAPSHOT")
    implementation("com.andrew.smart_greenhouse.api:clam-api:1.0-SNAPSHOT")
    implementation("com.andrew.smart_greenhouse.streaming:streaming-api:1.0-SNAPSHOT")
    implementation("com.andrew.smart_greenhouse.util:greenhouse-util:1.0-SNAPSHOT")
    runtimeOnly("org.postgresql:postgresql")
    implementation(kotlin("stdlib-jdk8"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}