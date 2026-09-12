plugins {
    id("com.diffplug.spotless") version "8.10.2"
}

spotless {
    kotlin {
        target("app/src/**/*.kt")
        ktlint()
    }

    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktlint()
    }
}
