pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BiliDesk"

include(
    ":app",
    ":androidApp",
    ":desktopApp",
    ":core:model",
    ":core:network",
    ":core:data",
    ":core:designsystem",
    ":core:player",
    ":feature:home",
    ":feature:dynamic",
    ":feature:search",
    ":feature:region",
    ":feature:space",
    ":feature:mine",
    ":feature:library",
    ":feature:messages",
    ":feature:settings",
    ":feature:login",
    ":feature:player",
)
