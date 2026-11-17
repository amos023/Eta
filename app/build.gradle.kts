import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// 签名六件套：环境变量（CI 走这条）优先，缺省回落仓库根 keystore.properties。
// keystore.properties 已在 .gitignore 中，只留在本机，不进版本库。
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasKeystoreProperties = keystorePropertiesFile.isFile
if (hasKeystoreProperties) {
    keystorePropertiesFile.inputStream().use { input ->
        keystoreProperties.load(input)
    }
}

fun signingValue(envName: String, propName: String): String? {
    val fromEnv = System.getenv(envName)
    if (fromEnv != null && fromEnv.isNotBlank()) return fromEnv
    val fromFile = keystoreProperties.getProperty(propName)
    if (fromFile != null && fromFile.isNotBlank()) return fromFile
    return null
}

val releaseStoreFile = signingValue("ETA_RELEASE_STORE_FILE", "storeFile")
val releaseStorePassword = signingValue("ETA_RELEASE_STORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("ETA_RELEASE_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("ETA_RELEASE_KEY_PASSWORD", "keyPassword")

val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// CI 对非发版构建传入提交号，按 SemVer 构建元数据追加为 +<sha>，不影响版本先后；本地与发版构建不传
val buildMetadata = providers.gradleProperty("eta.buildMetadata").orNull?.takeIf { it.isNotBlank() }
require(buildMetadata == null || buildMetadata.matches(Regex("[0-9A-Za-z.-]+"))) {
    "eta.buildMetadata 只能包含字母、数字、点和连字符"
}
val buildMetadataSuffix = buildMetadata?.let { "+$it" }.orEmpty()

android {
    namespace = "io.github.mangi.eta"
    compileSdk = 37
    ndkVersion = libs.versions.ndk.get()

    // 正式版需要与测试版共存：两者签名不同，同 applicationId 必然互相覆盖。
    // 构建正式版时用 -Peta.appIdSuffix=.stable 派生独立安装标识，
    // 代码包名（namespace）保持不变，类名与 JNI 符号都不受影响。
    val appIdSuffix = providers.gradleProperty("eta.appIdSuffix").orNull.orEmpty()

    defaultConfig {
        applicationId = "io.github.mangi.eta$appIdSuffix"
        minSdk = 33
        targetSdk = 37
        // versionCode 规则：yyyyMMdd + 两位当日序号（01 起），发版时随 versionName 一起手动递增。
        versionCode = 2026111701
        versionName = "3.6.1"

        ndk {
            // 同时过滤本地与依赖 AAR 的原生库，确保 APK 仅包含 ARM64。
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // 版本名带构建类型标记，设置页与 APK 文件名据此区分 Debug 包
            versionNameSuffix = "-debug" + buildMetadataSuffix
            isMinifyEnabled = false
            isPseudoLocalesEnabled = true
        }
        release {
            versionNameSuffix = buildMetadataSuffix
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    androidResources {
        localeFilters += listOf("en", "b+zh+Hans", "b+zh+Hant")
    }

    packaging {
        dex {
            useLegacyPackaging = true
        }
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += setOf("**/libproot_exec.so", "**/libproot_loader.so", "**/libeta_pty.so")
        }
        resources {
            // 合并 Xposed 模块声明，避免 release 裁剪后模块入口失效
            merges += "META-INF/xposed/*"
            // 仅排除会引发打包冲突的签名/版本元数据，避免误伤 Compose 资源
            excludes += "META-INF/*.kotlin_module"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        // versionCode 采用日期编码，已发布值只能递增；上限 2100000000 足够覆盖到 2099 年。
        disable += "HighAppVersionCode"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric 在高版本 JDK 下需要访问内部 API，参数只作用于测试 JVM。
        unitTests.all {
            it.jvmArgs(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.security=ALL-UNNAMED",
                "--add-opens=java.base/java.text=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                "--enable-native-access=ALL-UNNAMED",
            )
            // 显式给出上限：此前依赖默认值（CI 上偏小），大字段用例会稳定 OOM。
            it.maxHeapSize = "2g"
        }
    }
}

androidComponents {
    onVariants { variant ->
        // 文件名只取 versionName：构建类型标记与 CI 构建元数据都由 versionNameSuffix 带出
        variant.outputs.forEach { output ->
            output.outputFileName.set(output.versionName.map { "Eta-v$it.apk" })
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
    // 内置离线语音识别：sherpa-onnx 官方 AAR 的 arm64-v8a 子集，推理全部在本机完成。
    implementation(files("libs/sherpa-onnx-2.0.1-arm64.aar"))
    implementation(libs.jsoup)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    compileOnly(libs.libxposed.api)
    // UI 侧 RemotePreferences 写入桥：通过 XposedService 将配置提交到 LSPosed 数据库；
    // Hook 侧用 XposedInterface.getRemotePreferences 读取当前进程持有的配置缓存。
    implementation(libs.libxposed.service)
    implementation(libs.dexkit)
    implementation(libs.coui)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.nav)
    implementation(libs.miuix.preference)
    implementation(libs.material.icons.extended)
    implementation(libs.androidx.navigationevent)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    // 只使用 GFM 解析器；聊天渲染层由 ui/markdown 自建，按块冻结并接入逐字显现。
    implementation(libs.intellij.markdown)
    implementation(libs.hidden.api.bypass)

    // DataStore：Provider / Model 结构化 JSON 与当前选中 ID 等键值
    implementation(libs.datastore.preferences)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // OkHttp：替代 HttpURLConnection，支持 SSE
    implementation(libs.okhttp)
    implementation(libs.nimbus.jose.jwt)
    implementation(libs.okhttp.sse)

    // Kotlinx Serialization：Provider 设置与运行时配置 JSON
    implementation(libs.kotlinx.serialization.json)

    // Coroutines：显式引入，避免依赖传递版本不确定
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
}
