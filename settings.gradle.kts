
// settings.gradle.kts
pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }  // 阿里云镜像
        maven { url = uri("https://maven.aliyun.com/repository/google") }  // Google 镜像
        maven {
            url = uri("https://maven.oalite.com/nexus/repository/maven-dev/")
        }
        maven { url = uri("https://jitpack.io") }
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)  // 强制优先使用 settings 仓库
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven {
            url = uri("https://maven.oalite.com/nexus/repository/maven-dev/")
        }
        maven { url = uri("https://jitpack.io") }
        google()
        mavenCentral()
    }
}

rootProject.name = "GPTMobile"
include(":app")
