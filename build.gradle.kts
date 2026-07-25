plugins {
    kotlin("jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.6.0"
}

group = "me.inthefield.gitbutlerforjetbrains"
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
    testImplementation("org.testcontainers:testcontainers:1.21.0")
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

tasks.test {
    // Testcontainers (used by the GitButler CLI integration tests) needs to reach the Docker
    // daemon. On setups where the active daemon is not on the default /var/run/docker.sock
    // (e.g. colima, rootless), the docker-java client fails to auto-detect it. Resolve the
    // socket of the active `docker context` in doFirst — execution time, so no external
    // process runs at configuration time (configuration-cache safe). Under colima only, also
    // disable Ryuk — its reaper container fails to start there. All of it is a no-op when
    // Docker is absent; the tests then self-skip via an Assume on
    // DockerClientFactory.isDockerAvailable, so the build still succeeds.
    // The IntelliJ test runtime injects -Djna.boot.library.path=<ide>/lib/jna and
    // -Djna.noclasspath=true through a JVM argument provider, which breaks the JNA jar
    // Testcontainers loads (native 7.0.0 vs expected 6.1.6) — Docker detection then dies
    // with java.lang.Error and the integration tests silently self-skip. Appending our
    // provider after the plugin's lets these later -D flags win, so JNA unpacks its own
    // matching native from the classpath jar. Plain `systemProperty` loses that race.
    jvmArgumentProviders.add {
        listOf("-Djna.boot.library.path=", "-Djna.noclasspath=false", "-Djna.nosys=true")
    }
    doFirst {
        if (System.getenv("DOCKER_HOST").isNullOrBlank()) {
            val dockerHost = runCatching {
                val proc = ProcessBuilder(
                    "docker", "context", "inspect", "--format", "{{.Endpoints.docker.Host}}",
                ).redirectErrorStream(true).start()
                if (proc.waitFor() == 0) proc.inputStream.bufferedReader().readText().trim() else null
            }.getOrNull()
            if (!dockerHost.isNullOrBlank()) {
                environment("DOCKER_HOST", dockerHost)
                if (dockerHost.contains("colima")) {
                    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
                }
            }
        }
    }
}
