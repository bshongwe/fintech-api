plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    api("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    
    // Spring Framework
    api("org.springframework:spring-context:6.0.13")
    api("org.springframework:spring-web:6.0.13")
    api("org.springframework.security:spring-security-core:6.1.5")
    api("org.springframework.security:spring-security-web:6.1.5")
    api("org.springframework.security:spring-security-config:6.1.5")
    api("org.springframework.boot:spring-boot-starter-web:3.2.0")
    api("org.springframework.boot:spring-boot-starter-security:3.2.0")
    api("org.springframework.boot:spring-boot-starter-validation:3.2.0")
    
    // Jakarta EE
    api("jakarta.servlet:jakarta.servlet-api:6.0.0")
    api("jakarta.validation:jakarta.validation-api:3.0.2")
    
    // Logging
    api("org.slf4j:slf4j-api:2.0.9")
    api("ch.qos.logback:logback-classic:1.4.14")
    
    // Encryption & Security
    api("org.bouncycastle:bcprov-jdk18on:1.76")
    api("org.apache.commons:commons-lang3:3.12.0")
}
