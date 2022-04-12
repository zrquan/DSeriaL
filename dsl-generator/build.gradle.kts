import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    val kotlinVersion: String by System.getProperties()
    kotlin("jvm") version kotlinVersion
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("io.kotest:kotest-runner-junit5:5.2.2")
    testImplementation("io.kotest:kotest-framework-datatest:5.2.2")
}

tasks.withType<KotlinCompile> {
    val javaVersion: String by System.getProperties()
    kotlinOptions.jvmTarget = javaVersion
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
