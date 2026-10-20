plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

val apiBaseUrl = providers.gradleProperty("omni3ApiUrl")
    .orElse(providers.environmentVariable("GESTOR_API_URL"))
    .getOrElse("http://10.0.2.2:8000")
    .trimEnd('/')

android {
    namespace = "com.omni3.gestorhogwartsmovil"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.omni3.gestorhogwartsmovil"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

// Conserva el esquema para preparar y comprobar futuras migraciones.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
