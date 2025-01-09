plugins {
    java
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "ca.ryanmorrison"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.hibernate.orm:hibernate-community-dialects")
    implementation("org.liquibase:liquibase-core")
    implementation("net.dv8tion:JDA:5.2.2") {
        exclude(module = "opus-java")
    }
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("org.xerial:sqlite-jdbc:3.47.1.0")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    exclude("**/*")
}
