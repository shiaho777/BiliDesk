plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
        )
    }

    sourceSets {
        val commonMain by getting
        val jvmShared by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.coroutines.core)
                implementation(project(":core:model"))
                implementation(project(":core:data"))
                implementation(project(":core:network"))
                implementation(project(":core:designsystem"))

                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(libs.material3)
                implementation(compose.ui)
                implementation(compose.materialIconsExtended)
                implementation(libs.lifecycle.viewmodel)
                implementation(libs.lifecycle.runtime)
                implementation(libs.coil.compose)
                implementation(libs.ui.backhandler)

            }
        }
        val androidMain by getting {
            dependsOn(jvmShared)
            dependencies {
                implementation(libs.coroutines.android)
            }
        }
        val jvmMain by getting {
            dependsOn(jvmShared)
            dependencies {
                implementation(libs.coroutines.swing)
            }
        }

    }
}

android {
    namespace = "app.bilidesk.feature.region"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
