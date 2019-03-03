import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.github.ben-manes.versions") version Plugin.VERSIONS
    kotlin("jvm") version Plugin.KOTLIN
    idea

    id("com.github.spotbugs") version Plugin.SPOTBUGS_PLUGIN

    id("com.github.johnrengelman.shadow") version Plugin.SHADOW_JAR
}

group = "com.github.bjoernpetersen"
version = "0.16.0"

repositories {
    jcenter()
    // maven("https://oss.sonatype.org/content/repositories/snapshots")
}

idea {
    module {
        isDownloadJavadoc = true
    }
}

spotbugs {
    isIgnoreFailures = true
    toolVersion = Plugin.SPOTBUGS_TOOL
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks {
    "compileKotlin"(KotlinCompile::class) {
        kotlinOptions.jvmTarget = "1.8"
    }

    "compileTestKotlin"(KotlinCompile::class) {
        kotlinOptions.jvmTarget = "1.8"
    }

    "test"(Test::class) {
        useJUnitPlatform()
        systemProperties["junit.jupiter.execution.parallel.enabled"] = true
    }
}

dependencies {
    implementation(
        group = "io.github.microutils",
        name = "kotlin-logging",
        version = Lib.KOTLIN_LOGGING
    ) {
        exclude("org.slf4j")
        exclude("org.jetbrains")
        exclude("org.jetbrains.kotlin")
    }
    compileOnly(
        group = "com.github.bjoernpetersen",
        name = "musicbot",
        version = Lib.MUSICBOT
    )

    implementation(
        group = "se.michaelthelin.spotify",
        name = "spotify-web-api-java",
        version = Lib.SPOTIFY
    ) {
        exclude("org.slf4j")
        exclude("com.google.guava")
        exclude("com.google.inject")
    }
    implementation(
        group = "com.google.oauth-client",
        name = "google-oauth-client",
        version = Lib.OAUTHCLIENT
    ) {
        exclude("org.slf4j")
        exclude("org.jetbrains")
        exclude("org.jetbrains.kotlin")
        exclude("com.google.guava")
        exclude("com.google.inject")
    }

    // Vertx for OAuth callback
    implementation(group = "io.vertx", name = "vertx-web", version = Lib.VERTX) {
        exclude("org.slf4j")
        exclude("org.jetbrains")
        exclude("org.jetbrains.kotlin")
        exclude("com.google.guava")
        exclude("com.google.inject")
    }
    implementation(group = "io.vertx", name = "vertx-lang-kotlin", version = Lib.VERTX) {
        exclude("org.slf4j")
        exclude("org.jetbrains")
        exclude("org.jetbrains.kotlin")
        exclude("com.google.guava")
        exclude("com.google.inject")
    }

    testImplementation(
        group = "org.slf4j",
        name = "slf4j-simple",
        version = Lib.SLF4J
    )
    testImplementation(
        group = "com.github.bjoernpetersen",
        name = "musicbot",
        version = Lib.MUSICBOT
    )
    testImplementation(
        group = "org.junit.jupiter",
        name = "junit-jupiter-api",
        version = Lib.JUNIT
    )
    testRuntime(
        group = "org.junit.jupiter",
        name = "junit-jupiter-engine",
        version = Lib.JUNIT
    )
}
