plugins {
    id("com.android.application")
}

android {
    namespace = "app.pathwise.mobile"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.pathwise.mobile"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets["main"].assets.srcDirs("src/main/assets", layout.buildDirectory.dir("generated/webassets"))
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

// The games are the web's own files (same rules, same views), copied in at build time so the two never drift.
val copyWebAssets by tasks.registering(Copy::class) {
    val web = rootProject.file("../pathwise-web/src/main/webapp/static")
    from(web.resolve("js/games")) { into("games") }
    from(web.resolve("css")) {
        include("tokens.css", "base.css", "components.css", "learn.css")
        into("css")
    }
    into(layout.buildDirectory.dir("generated/webassets"))
}
tasks.named("preBuild") { dependsOn(copyWebAssets) }
