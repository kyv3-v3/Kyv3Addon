import net.fabricmc.loom.task.RemapJarTask
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.fabric.loom)
}

val proguard by configurations.creating

base {
    archivesName = properties["archives_base_name"] as String
    version = libs.versions.mod.version.get()
    group = properties["maven_group"] as String
}

repositories {
    mavenCentral()
    maven { url = uri("file://${projectDir}/localMaven") }
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
    flatDir { dirs("libs") }
}

dependencies {
    // Fabric
    minecraft(libs.minecraft)
    mappings(variantOf(libs.yarn) { classifier("v2") })
    modImplementation(libs.fabric.loader)
    modCompileOnly("net.fabricmc.fabric-api:fabric-networking-api-v1:5.1.5+ae1e07683e")

    // Meteor
    modImplementation(libs.meteor.client)

    // Release obfuscation
    proguard("com.guardsquare:proguard-base:7.7.0")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to libs.versions.minecraft.get()
        )

        inputs.properties(propertyMap)

        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        inputs.property("archivesName", project.base.archivesName.get())

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 21
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
    }

    withType<Test> {
        useJUnitPlatform()
    }
}

val remapJarTask = tasks.named<RemapJarTask>("remapJar")
val releaseObfJar = layout.buildDirectory.file(
    providers.provider { "libs/${base.archivesName.get()}-${project.version}-release-obf.jar" }
)

val obfuscateReleaseJar = tasks.register<JavaExec>("obfuscateReleaseJar") {
    group = "build"
    description = "Obfuscates the remapped jar for release distribution."
    dependsOn(remapJarTask)

    classpath = proguard
    mainClass.set("proguard.ProGuard")

    val rulesFile = file("proguard-release.pro")
    inputs.file(remapJarTask.flatMap { it.archiveFile })
    inputs.file(rulesFile)
    outputs.file(releaseObfJar)

    doFirst {
        val inJar = remapJarTask.get().archiveFile.get().asFile
        val outJar = releaseObfJar.get().asFile
        outJar.parentFile.mkdirs()

        val libraryJars = linkedSetOf<File>().apply {
            addAll(configurations.compileClasspath.get().files)
            addAll(configurations.runtimeClasspath.get().files)
        }

        val pgArgs = mutableListOf(
            "@${rulesFile.absolutePath}",
            "-injars", inJar.absolutePath,
            "-outjars", outJar.absolutePath
        )

        libraryJars.forEach { libraryJar ->
            pgArgs += listOf("-libraryjars", libraryJar.absolutePath)
        }

        setArgs(pgArgs)
    }
}

tasks.register("buildRelease") {
    group = "build"
    description = "Builds an obfuscated release jar after passing quality checks."
    dependsOn("qualityGate", obfuscateReleaseJar)
}

tasks.register("qualityGate") {
    group = "verification"
    description = "Runs the baseline quality gate used by CI and release workflows."
    dependsOn("compileJava", "test")
}
