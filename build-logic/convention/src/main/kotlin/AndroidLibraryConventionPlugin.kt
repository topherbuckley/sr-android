import com.android.build.api.dsl.LibraryExtension
import jp.oist.abcvlib.buildTools
import jp.oist.abcvlib.compileSdk
import jp.oist.abcvlib.configureBuildTypes
import jp.oist.abcvlib.configureKotlinAndroid
import jp.oist.abcvlib.minSdk
import jp.oist.abcvlib.plugin
import jp.oist.abcvlib.targetSdk
import jp.oist.abcvlib.versionCatalog
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(versionCatalog.plugin("android-library"))
            pluginManager.apply(versionCatalog.plugin("kotlin-android"))

            extensions.configure<LibraryExtension> {
                compileSdk = versionCatalog.compileSdk
                testOptions.targetSdk = versionCatalog.targetSdk
                buildToolsVersion = versionCatalog.buildTools
                defaultConfig {
                    minSdk = versionCatalog.minSdk
                }

                configureKotlinAndroid(this)
                configureBuildTypes(this)
            }
        }
    }
}