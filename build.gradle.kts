plugins {
    kotlin("jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.6.0"
}

group = "com.enapi.gitbutler"
// CalVer (YYYY.M.D.BUILD) is injected by CI via -PpluginVersion; local builds use the fallback.
version = (findProperty("pluginVersion") as String?) ?: "0.1.0-dev"

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.1.4.1")
        bundledPlugin("Git4Idea")
    }

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
            untilBuild = provider { null }
        }
    }
}

kotlin {
    jvmToolchain(21)
}
