import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.masswabroadcast.mwbxkp"
    minSdk = 24
    targetSdk = 36
    versionCode = 5
    versionName = "1.4"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Automated root /apk sync: keeps a single latest versioned APK (e.g. MassWABroadcast-v1.0.apk)
// alongside build-manifest.json on every successful assembleDebug or assembleRelease build.
abstract class SyncApkToRootTask : DefaultTask() {
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val apkSourceDir: DirectoryProperty

  @get:OutputDirectory
  abstract val rootApkDir: DirectoryProperty

  @get:Input
  abstract val appId: Property<String>

  @get:Input
  abstract val variantName: Property<String>

  @get:Input
  abstract val appVersionName: Property<String>

  @get:Input
  abstract val appVersionCode: Property<Int>

  @TaskAction
  fun syncApk() {
    val srcDir = apkSourceDir.get().asFile
    val apkFiles = srcDir.walkTopDown().filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }.toList()
    val sourceApk = apkFiles.maxByOrNull { it.lastModified() } ?: return

    val destDir = rootApkDir.get().asFile
    if (!destDir.exists()) destDir.mkdirs()

    // Replace any prior build APKs so root /apk always contains one clean updatable versioned APK
    destDir.listFiles()?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }?.forEach { it.delete() }

    val targetFileName = "MassWABroadcast-v${appVersionName.get()}.apk"
    val targetApk = File(destDir, targetFileName)
    sourceApk.copyTo(targetApk, overwrite = true)

    val digest = MessageDigest.getInstance("SHA-256")
    targetApk.inputStream().use { input ->
      val buffer = ByteArray(8192)
      var read = input.read(buffer)
      while (read != -1) {
        digest.update(buffer, 0, read)
        read = input.read(buffer)
      }
    }
    val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
    val updatedAtUtc = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

    val manifestJson = """
      {
        "applicationId": "${appId.get()}",
        "variant": "${variantName.get()}",
        "versionName": "${appVersionName.get()}",
        "versionCode": ${appVersionCode.get()},
        "apkFile": "$targetFileName",
        "sizeBytes": ${targetApk.length()},
        "sha256": "$sha256",
        "updatedAtUtc": "$updatedAtUtc"
      }
    """.trimIndent() + "\n"

    File(destDir, "build-manifest.json").writeText(manifestJson)
    logger.lifecycle("Synced root APK -> ${targetApk.absolutePath} (${targetApk.length()} bytes)")
  }
}

val syncDebugApkToRoot = tasks.register<SyncApkToRootTask>("syncDebugApkToRoot") {
  apkSourceDir.set(layout.buildDirectory.dir("outputs/apk/debug"))
  rootApkDir.set(rootProject.layout.projectDirectory.dir("apk"))
  appId.set(android.defaultConfig.applicationId ?: "com.aistudio.masswabroadcast.mwbxkp")
  variantName.set("debug")
  appVersionName.set(android.defaultConfig.versionName ?: "1.0")
  appVersionCode.set(android.defaultConfig.versionCode ?: 1)
}

val syncReleaseApkToRoot = tasks.register<SyncApkToRootTask>("syncReleaseApkToRoot") {
  apkSourceDir.set(layout.buildDirectory.dir("outputs/apk/release"))
  rootApkDir.set(rootProject.layout.projectDirectory.dir("apk"))
  appId.set(android.defaultConfig.applicationId ?: "com.aistudio.masswabroadcast.mwbxkp")
  variantName.set("release")
  appVersionName.set(android.defaultConfig.versionName ?: "1.0")
  appVersionCode.set(android.defaultConfig.versionCode ?: 1)
}

afterEvaluate {
  tasks.findByName("assembleDebug")?.finalizedBy(syncDebugApkToRoot)
  tasks.findByName("packageDebug")?.finalizedBy(syncDebugApkToRoot)
  tasks.findByName("assembleRelease")?.finalizedBy(syncReleaseApkToRoot)
  tasks.findByName("packageRelease")?.finalizedBy(syncReleaseApkToRoot)
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  // implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}
