import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    kotlin("jvm") version "1.6.10"
    application
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

group = "com.rainyseason"
val projectVersion = "1.0.0-SNAPSHOT"
version = projectVersion

repositories {
    mavenCentral()
}

val vertxVersion = "4.2.5"
val junitJupiterVersion = "5.7.0"

val mainVerticleName = "com.rainyseason.backend.MainVerticle"
val launcherClassName = "io.vertx.core.Launcher"

val watchForChange = "src/**/*"
val doOnChange = "gradle classes"

application {
    mainClass.set(launcherClassName)
}

dependencies {
    implementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))
    implementation("io.vertx:vertx-web")
    implementation("io.vertx:vertx-lang-kotlin")
    implementation("io.vertx:vertx-web-client")
    implementation(kotlin("stdlib-jdk8"))
    testImplementation("io.vertx:vertx-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
//    implementation(files("./build/proguard.jar"))
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
    val fatFile = File(outputDir, "${project.name}-${projectVersion}-fat.jar")
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

tasks.withType<JavaExec> {
    args = listOf(
        "run",
        mainVerticleName,
//        "--redeploy=$watchForChange",
        "--launcher-class=$launcherClassName",
//        "--on-redeploy=$doOnChange"
    )
}
