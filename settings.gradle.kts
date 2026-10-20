pluginManagement {
    repositories {
        google { content { excludeGroupByRegex("org[.]jetbrains.*") } }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google { content { excludeGroupByRegex("org[.]jetbrains.*") } }
        mavenCentral()
    }
}

rootProject.name = "SkyCanvas"

include(":app", ":core:weather", ":core:data")
