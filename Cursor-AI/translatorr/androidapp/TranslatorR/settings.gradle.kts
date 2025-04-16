// 插件管理配置塊，用於配置Gradle插件的倉庫源
pluginManagement {
    repositories {
        // 配置Google的Maven倉庫
        google {
            content {
                // 指定只從Google倉庫下載這些包名模式的依賴
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // 配置Maven中央倉庫
        mavenCentral()
        // 配置Gradle插件門戶倉庫
        gradlePluginPortal()
    }
}

// 依賴解析管理配置塊，用於配置項目依賴的倉庫源
dependencyResolutionManagement {
    // 設置倉庫模式為嚴格模式，即只允許在這裡聲明的倉庫
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 添加Google倉庫
        google()
        // 添加Maven中央倉庫
        mavenCentral()
    }
}

// 設置根項目名稱
rootProject.name = "TranslatorR"
// 包含app模塊到項目中
include(":app")
