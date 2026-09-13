plugins {
    id("java")
}

group = "net.boneai"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Provided by the server at runtime - not shaded into the jar.
        compileOnly("io.papermc.paper:paper-api:26.2.build.+")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.jar {
    archiveBaseName.set("BoneAI")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
