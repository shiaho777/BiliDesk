plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
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

    sourceSets {
        val commonMain by getting
        val jvmShared by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.coroutines.core)
                implementation(project(":core:model"))
                implementation(project(":core:network"))
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
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

    }
}

android {
    namespace = "app.bilidesk.core.data"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
