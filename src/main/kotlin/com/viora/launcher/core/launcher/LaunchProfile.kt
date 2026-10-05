package com.viora.launcher.core.launcher

import com.viora.launcher.core.auth.model.Account
import java.io.File

/**
 * ✅ LaunchProfile — يحتوي على كل ما يحتاجه GameLauncher لتشغيل نسخة
 *
 * @property account حساب اللاعب
 * @property versionId معرف الإصدار (مثلاً Slimes-Adventure أو 1.21.1)
 * @property minecraftDir مجلد .minecraft الرئيسي (للمكتبات والـ assets)
 * @property javaPath مسار java.exe
 * @property ramMB الذاكرة القصوى (Xmx)
 * @property minRamMB الذاكرة الدنيا (Xms)
 * @property width عرض النافذة
 * @property height ارتفاع النافذة
 * @property customJvmArgs وسائط JVM إضافية
 * @property customGameArgs وسائط اللعبة الإضافية
 * @property gameDir مجلد اللعب — للعزل (اختياري)
 *
 * ⚠️ إذا كان [gameDir] = null → يُستخدم [minecraftDir]
 * ✅ للمودباكس: gameDir = .minecraft/instances/<name>
 */
data class LaunchProfile(
    val account: Account,
    val versionId: String,
    val minecraftDir: File,
    val javaPath: String,
    val ramMB: Int = 4096,
    val minRamMB: Int = 1024,
    val width: Int = 1280,
    val height: Int = 720,
    val customJvmArgs: List<String> = emptyList(),
    val customGameArgs: List<String> = emptyList(),
    // ✅ مجلد اللعب المعزول (اختياري)
    val gameDir: File? = null
) {
    /**
     * ✅ مجلد اللعب الفعّال:
     * - إذا كان [gameDir] محددًا → استخدمه (للـ instances المعزولة)
     * - وإلا → استخدم [minecraftDir] (للتشغيل العادي)
     */
    val effectiveGameDir: File
        get() = gameDir ?: minecraftDir

    /**
     * ✅ مجلد الـ assets — دائمًا في .minecraft الرئيسي
     * (لأن الأصول مشتركة بين كل الإصدارات)
     */
    val assetsDir: File
        get() = File(minecraftDir, "assets")

    /**
     * ✅ مجلد الـ natives — مشترك أيضًا
     * (يُحمَّل عبر -Djava.library.path)
     */
    val nativesDir: File
        get() = File(minecraftDir, "natives")

    /**
     * ✅ مسار ملف .jar الخاص بالإصدار (client.jar)
     */
    val clientJar: File
        get() = File(minecraftDir, "versions/$versionId/$versionId.jar")

    /**
     * ✅ مسار ملف .json الخاص بالإصدار
     */
    val versionJson: File
        get() = File(minecraftDir, "versions/$versionId/$versionId.json")

    /**
     * ✅ هل هذا الإصدار معزول (له instance folder)؟
     */
    val isIsolated: Boolean
        get() = gameDir != null && gameDir.exists()

    /**
     * ✅ ملخص للتشخيص
     */
    override fun toString(): String {
        return """
        LaunchProfile:
          Version:        $versionId
          Account:        ${account.username}
          Java:           $javaPath
          RAM:            ${ramMB}MB (min: ${minRamMB}MB)
          Minecraft Dir:  ${minecraftDir.absolutePath}
          Game Dir:       ${effectiveGameDir.absolutePath}
          Isolated:       $isIsolated
          Assets:         ${assetsDir.absolutePath}
          Natives:        ${nativesDir.absolutePath}
          Resolution:     ${width}x$height
        """.trimIndent()
    }
}