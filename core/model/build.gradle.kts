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
            }
        }
        val androidMain by getting {
            dependsOn(jvmShared)
        }
        val jvmMain by getting {
            dependsOn(jvmShared)
        }

    }
}

android {
    namespace = "app.bilidesk.core.model"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
