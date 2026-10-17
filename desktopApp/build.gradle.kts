plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
        )
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(project(":app"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(libs.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "app.bilidesk.MainKt"
        nativeDistributions {
            packageName = "BiliDesk"
            packageVersion = "1.2.4"
            description = "BiliDesk"
            copyright = "BiliDesk"
            vendor = "BiliDesk"
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
            )
            macOS {
                bundleID = "app.bilidesk"
            }
            windows {
                menuGroup = "BiliDesk"
                upgradeUuid = "8f3c1e6a-4b2d-4f91-9c5a-7e1d2b8a6c40"
            }
            linux {
                packageName = "bilidesk"
            }
        }
    }
}
