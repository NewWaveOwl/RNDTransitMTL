// Allows this server to build independently of Android, Desktop and Web.
rootProject.name = "backend"
include(":launcher")
pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositories { mavenCentral() }
    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}
