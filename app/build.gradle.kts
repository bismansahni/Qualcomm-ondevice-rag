import java.nio.file.Paths

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.devtools.ksp")
}

apply(plugin = "io.objectbox")

// Path to local QAIRT SDK
val qnnSDKLocalPath = "/Users/bismansahni/Desktop/qairt/2.29.0.241129"

// List of model assets
val models = listOf("llm")
// Relative asset path for model configuration and binaries
val relAssetsPath = "src/main/assets/models"
val buildDir = project(":app").layout.buildDirectory
val libsDir = buildDir.dir("libs")

android {
    namespace = "bisman.thesis.qualcomm"
    compileSdk = 34

    defaultConfig {
        applicationId = "bisman.thesis.qualcomm"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17")
                abiFilters("arm64-v8a")
                arguments("-DQNN_SDK_ROOT_PATH=$qnnSDKLocalPath")
            }
        }
        
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        getByName("debug") {
            // Use default debug signing config
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
    
    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java", "src/main/kotlin")
        }
    }

    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "META-INF/DEPENDENCIES"
        }
    }

    androidResources {
        noCompress += listOf("bin", "json")
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir(libsDir)
        }
    }
}

ksp {
    arg("KOIN_CONFIG_CHECK", "true")
}

dependencies {
    implementation(fileTree(mapOf("include" to listOf("*.jar"), "dir" to "libs")))
    
    // Original dependencies
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    
    // Kotlin
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    
    // Koin for dependency injection
    implementation("io.insert-koin:koin-android:3.5.6")
    implementation("io.insert-koin:koin-androidx-compose:3.5.6")
    implementation("io.insert-koin:koin-annotations:1.3.1")
    ksp("io.insert-koin:koin-ksp-compiler:1.3.1")
    
    // ObjectBox for database
    implementation("io.objectbox:objectbox-android:4.0.3")
    
    // Document processing
    implementation("com.itextpdf:itextpdf:5.5.13.3")
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    
    // Sentence embeddings
    implementation("com.github.shubham0204:Sentence-Embeddings-Android:0.0.3")
    
    // Markdown support
    implementation("com.github.jeziellago:compose-markdown:0.5.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.05.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Pre-build task to copy QNN libraries
tasks.register("copyQnnLibraries") {
    doLast {
        if (qnnSDKLocalPath.isEmpty()) {
            throw RuntimeException("Please download QAIRT SDK and set `qnnSDKLocalPath` in build.gradle.kts")
        }

        val qnnSDKFile = file(qnnSDKLocalPath)
        if (!qnnSDKFile.exists()) {
            throw RuntimeException("QAIRT SDK does not exist at ${qnnSDKFile.absolutePath}")
        }

        val genieLibFile = file(Paths.get(qnnSDKLocalPath, "lib", "aarch64-android", "libGenie.so"))
        if (!genieLibFile.exists()) {
            throw RuntimeException("libGenie.so does not exist. Expected at ${genieLibFile.absolutePath}")
        }

        models.forEach { model ->
            val configFile = file(Paths.get(relAssetsPath, model, "genie_config.json"))
            if (!configFile.exists()) {
                throw RuntimeException("Missing genie_config.json for $model. Expected at ${configFile.absolutePath}")
            }
            val tokenizerFile = file(Paths.get(relAssetsPath, model, "tokenizer.json"))
            if (!tokenizerFile.exists()) {
                throw RuntimeException("Missing tokenizer.json for $model. Expected at ${tokenizerFile.absolutePath}")
            }
        }

        val libsABIDir = buildDir.dir("libs/arm64-v8a").get().asFile
        libsABIDir.mkdirs()

        copy {
            from(qnnSDKLocalPath)
            include("**/lib/aarch64-android/libQnnHtp.so")
            include("**/lib/aarch64-android/libQnnHtpPrepare.so")
            include("**/lib/aarch64-android/libQnnSystem.so")
            include("**/lib/aarch64-android/libQnnSaver.so")
            include("**/lib/hexagon-v**/unsigned/libQnnHtpV**Skel.so")
            include("**/lib/aarch64-android/libQnnHtpV**Stub.so")
            into(libsABIDir)
            eachFile {
                path = name
            }
            includeEmptyDirs = false
        }
    }
}

tasks.preBuild {
    dependsOn("copyQnnLibraries")
}