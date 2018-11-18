import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version Version.KOTLIN
    idea

    id("com.github.spotbugs") version Version.SPOTBUGS

    id("com.github.johnrengelman.shadow") version Version.SHADOW_JAR
}

group = "com.github.bjoernpetersen"
version = "0.11.0-SNAPSHOT"

repositories {
    jcenter()
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

idea {
    module {
        isDownloadJavadoc = true
    }
}

spotbugs {
    isIgnoreFailures = true
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

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(
        group = "io.github.microutils",
        name = "kotlin-logging",
        version = Version.KOTLIN_LOGGING)
    compileOnly(
        group = "com.github.bjoernpetersen",
        name = "musicbot",
        version = Version.MUSICBOT) {
        isChanging = true
    }

    implementation(
        group = "se.michaelthelin.spotify",
        name = "spotify-web-api-java",
        version = Version.SPOTIFY)
    implementation(
        group = "com.google.oauth-client",
        name = "google-oauth-client",
        version = Version.OAUTHCLIENT)
    implementation(
        group = "com.google.oauth-client",
        name = "google-oauth-client-jetty",
        version = Version.OAUTHCLIENT)
    implementation(
        group = "com.google.http-client",
        name = "google-http-client",
        version = Version.OAUTHCLIENT)
    implementation(
        group = "com.google.http-client",
        name = "google-http-client-jackson2",
        version = Version.OAUTHCLIENT)
    implementation(
        group = "com.squareup.retrofit2",
        name = "retrofit",
        version = Version.RETROFIT)
    implementation(
        group = "com.squareup.retrofit2",
        name = "converter-jackson",
        version = Version.RETROFIT)


    testImplementation(
        group = "org.junit.jupiter",
        name = "junit-jupiter-api",
        version = Version.JUNIT)
    testImplementation(
        group = "org.junit.jupiter",
        name = "junit-jupiter-engine",
        version = Version.JUNIT)
    testImplementation(group = "io.mockk", name = "mockk", version = Version.MOCK_K)
    testImplementation(group = "org.assertj", name = "assertj-core", version = Version.ASSERT_J)
}
