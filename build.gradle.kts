import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.github.ben-manes.versions") version Plugin.VERSIONS
    kotlin("jvm") version Plugin.KOTLIN
    idea

    id("com.github.spotbugs") version Plugin.SPOTBUGS_PLUGIN

    id("com.github.johnrengelman.shadow") version Plugin.SHADOW_JAR
}

group = "com.github.bjoernpetersen"
version = "0.13.0-SNAPSHOT"

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
    }
}

configurations {
    "runtime" {
        exclude("org.slf4j")
        exclude("org.jetbrains")
        exclude("org.jetbrains.kotlin")
        exclude("com.google.guava")
        exclude("com.google.inject")
    }
}

dependencies {
    implementation(
        group = "io.github.microutils",
        name = "kotlin-logging",
        version = Lib.KOTLIN_LOGGING
    )

    compileOnly(
        group = "com.github.bjoernpetersen",
        name = "musicbot",
        version = Lib.MUSICBOT
    )

    implementation(
        group = "se.michaelthelin.spotify",
        name = "spotify-web-api-java",
        version = Lib.SPOTIFY
    )
    implementation(
        group = "com.google.oauth-client",
        name = "google-oauth-client",
        version = Lib.OAUTHCLIENT
    )
    implementation(
        group = "com.google.oauth-client",
        name = "google-oauth-client-jetty",
        version = Lib.OAUTHCLIENT
    )
    implementation(
        group = "com.google.http-client",
        name = "google-http-client",
        version = Lib.OAUTHCLIENT
    )
    implementation(
        group = "com.google.http-client",
        name = "google-http-client-jackson2",
        version = Lib.OAUTHCLIENT
    )

    testImplementation(kotlin("stdlib-jdk8"))
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
