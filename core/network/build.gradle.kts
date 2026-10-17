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
            }
        }
        val jvmHttp by creating {
            dependsOn(jvmShared)
            dependencies {
                implementation(libs.okhttp)
            }
        }
        val androidMain by getting {
            dependsOn(jvmHttp)
            dependencies {
                implementation(libs.coroutines.android)
            }
        }
        val jvmMain by getting {
            dependsOn(jvmHttp)
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
    namespace = "app.bilidesk.core.network"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
