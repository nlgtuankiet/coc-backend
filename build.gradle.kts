import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    kotlin("jvm") version "1.6.10"
    kotlin("kapt") version "1.6.10"
    application
    id("com.github.johnrengelman.shadow") version "7.0.0"
    id("org.jlleitschuh.gradle.ktlint") version "10.2.1"
}

group = "com.rainyseason"
val projectVersion = "1.0.0"
version = projectVersion

repositories {
    mavenCentral()
}

val vertxVersion = "4.2.5"
val junitJupiterVersion = "5.7.0"

val mainVerticleName = "com.rainyseason.coc.backend.MainVerticle"
val launcherClassName = "io.vertx.core.Launcher"

val watchForChange = "src/**/*"
val doOnChange = "gradle classes"

application {
    mainClass.set("com.rainyseason.coc.backend.Main")
}

dependencies {
    implementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))
    implementation("io.vertx:vertx-web")
    implementation("io.vertx:vertx-lang-kotlin")
    implementation("io.vertx:vertx-web-client")
    implementation("io.vertx:vertx-auth-jwt")
    implementation("io.vertx:vertx-lang-kotlin-coroutines")
    implementation(kotlin("stdlib-jdk8"))
    testImplementation("io.vertx:vertx-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")

    implementation("com.google.dagger:dagger:2.41")
    kapt("com.google.dagger:dagger-compiler:2.41")

    implementation(platform("org.apache.logging.log4j:log4j-bom:2.17.2"))
    implementation("org.apache.logging.log4j:log4j-api")
    implementation("org.apache.logging.log4j:log4j-core")

    implementation("com.google.firebase:firebase-admin:8.1.0")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions.jvmTarget = "11"

val shadowJarTask: ShadowJar = tasks.withType<ShadowJar> {
    archiveClassifier.set("fat")
    manifest {
        attributes(mapOf("Main-Verticle" to mainVerticleName))
    }
    mergeServiceFiles()
}.first()

shadowJarTask.doLast {
    val outputDir = File(buildDir, "libs")
    val fatFile = File(outputDir, "${project.name}-$projectVersion-fat.jar")
    require(fatFile.exists())
    fatFile.copyTo(File(buildDir, "app.jar"), true)
    val sizeInMb = fatFile.length().toDouble() / 1024 / 1024
    project.logger.info("Fat file is $sizeInMb Mb")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events = setOf(PASSED, SKIPPED, FAILED)
    }
}
