plugins {
    alias(libs.plugins.kotlinJvm)
    application
}
kotlin { jvmToolchain(21) }
application { mainClass.set("transit.launcher.LauncherKt") }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation(libs.junit)
}
tasks.test { useJUnit() }
// jpackage starts the main JAR; its sibling dependency JARs must be discoverable too.
tasks.jar {
    manifest.attributes["Class-Path"] = configurations.runtimeClasspath.get().files.joinToString(" ") { it.name }
}
