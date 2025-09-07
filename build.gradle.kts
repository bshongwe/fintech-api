plugins {
    `java`
}

allprojects {
    group = "com.fintech"
    version = "0.1.0"
}

subprojects {
    apply(plugin = "java")
    repositories {
        mavenCentral()
    }
}
