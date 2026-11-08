plugins {
    java
}

group = "com.dex"
version = "0.1.0"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://jitpack.io")
}

val paperApi = "io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT"

dependencies {
    compileOnly(paperApi)
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        isTransitive = false
    }
    // Provided by their plugins at runtime, never shaded
    compileOnly(files("libs/MythicMobs-5.13.0.jar"))
    compileOnly(files("libs/FastAsyncWorldEdit-2.13.2.jar"))
    compileOnly(files("libs/boxcore-api-1.4.0.jar"))

    testImplementation(paperApi)
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveFileName = "BoxPhoBan-${project.version}.jar"
}
