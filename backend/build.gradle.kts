plugins {
    alias(libs.plugins.kotlinJvm)
    application
}
kotlin { jvmToolchain(21) }
application { mainClass.set("transit.ServerKt") }
dependencies {
    implementation("io.ktor:ktor-server-cio:3.4.3")
    implementation("io.ktor:ktor-client-okhttp:3.4.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("com.squareup.okio:okio:3.18.1")
    implementation(libs.kotlinx.coroutinesCore)
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.3.21")
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutinesTest)
}
tasks.test { useJUnit() }

val routingEvidenceDirectory = providers.gradleProperty("routingEvidenceDirectory")
tasks.register<JavaExec>("verifyRoutingEvidence") {
    group = "verification"
    description = "Replay explicitly selected real OTP evidence through the TravelTracker preview adapter without network access."
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("transit.RoutingEvidenceCheckKt")
    doFirst {
        args(routingEvidenceDirectory.orNull ?: error("Set -ProutingEvidenceDirectory to an explicit Test-TravelTrackerRouting.ps1 evidence directory."))
    }
}
