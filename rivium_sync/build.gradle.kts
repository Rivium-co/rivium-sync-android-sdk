plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.vanniktech.maven.publish") version "0.30.0"
}

android {
    namespace = "co.rivium.sync.sdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 21

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // PN Protocol - Rivium messaging protocol layer
    // Use project dependency for local development, Maven dependency for published builds
    val useLocalProtocol = findProject(":pn-protocol") != null
    if (useLocalProtocol) {
        implementation(project(":pn-protocol"))
    } else {
        implementation("co.rivium:pn-protocol:0.2.0")
    }

    // HTTP Client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // AndroidX
    implementation("androidx.core:core-ktx:1.12.0")

    // Room Database for offline persistence
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // ==================== Test Dependencies ====================
    // Unit Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.google.truth:truth:1.1.5")

    // Android Instrumentation Testing
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("com.google.truth:truth:1.1.5")
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()

    coordinates("co.rivium", "rivium-sync-android", project.findProperty("VERSION_NAME") as String? ?: "1.0.0")

    pom {
        name.set("RiviumSync Android SDK")
        description.set("Realtime database SDK for Android - Firebase alternative with offline-first MQTT sync")
        inceptionYear.set("2024")
        url.set("https://rivium.co")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("rivium")
                name.set("Rivium")
                email.set("founder@rivium.co")
                url.set("https://rivium.co")
            }
        }

        scm {
            url.set("https://github.com/Rivium-co/rivium-sync-android-sdk")
            connection.set("scm:git:git://github.com/Rivium-co/rivium-sync-android-sdk.git")
            developerConnection.set("scm:git:ssh://git@github.com/Rivium-co/rivium-sync-android-sdk.git")
        }
    }
}
