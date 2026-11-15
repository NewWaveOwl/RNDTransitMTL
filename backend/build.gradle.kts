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
}
tasks.test { useJUnit() }
