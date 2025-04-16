// 這是頂層構建文件，你可以在這裡添加所有子項目/模塊通用的配置選項

// apply false 的作用是在頂層構建文件中聲明插件但不立即應用它
// 這樣做的原因是:
// 1. 頂層build.gradle.kts主要用於項目級配置，不需要直接應用這些插件
// 2. 這些插件實際上應該在各個子模塊(如app模塊)中應用，而不是在根項目
// 3. 如果在這裡直接應用(不加apply false)，可能會導致構建錯誤
// 4. 這種方式允許子模塊按需使用這些插件，提供了更好的模塊化和靈活性
plugins {
    // 聲明Android應用程序插件，但不立即應用
    // 實際應用將在app/build.gradle.kts等子模塊中進行
    alias(libs.plugins.android.application) apply false

    // 聲明Kotlin Android插件，但不立即應用
    // 同樣，具體的應用會在需要使用Kotlin的子模塊中進行
    alias(libs.plugins.kotlin.android) apply false
}