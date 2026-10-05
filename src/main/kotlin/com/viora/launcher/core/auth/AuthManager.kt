package com.viora.launcher.core.auth

import com.viora.launcher.core.auth.microsoft.MicrosoftAuth
import com.viora.launcher.core.auth.microsoft.MinecraftAuth
import com.viora.launcher.core.auth.microsoft.XboxAuth
import com.viora.launcher.core.auth.model.Account
import com.viora.launcher.core.auth.model.AccountType
import com.viora.launcher.core.auth.viora.VioraAuth
import com.viora.launcher.core.storage.AccountRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class AuthManager(private val repo: AccountRepository) {

    private val msAuth = MicrosoftAuth()
    private val xboxAuth = XboxAuth()
    private val mcAuth = MinecraftAuth()
    private val vioraAuth = VioraAuth()

    // ✅ Mutex لمنع تجديد متزامن لنفس الحساب
    private val refreshMutex = Mutex()

    // ============================================================
    //  MICROSOFT
    // ============================================================

    suspend fun startMicrosoftLogin(): MicrosoftAuth.DeviceCodeResponse {
        return msAuth.requestDeviceCode()
    }

    suspend fun completeMicrosoftLogin(deviceCode: String): Account {
        try {
            println("🔵 [Auth] بدء تسجيل الدخول الكامل...")

            // 1. Microsoft
            val msToken = msAuth.pollForToken(deviceCode)
            val msAccess = msToken.accessToken
                ?: throw Exception("لم يتم استلام MS access token")
            println("✅ [Auth] تم استلام MS token")

            // 2. Xbox Live
            println("🔵 [Auth] مصادقة Xbox Live...")
            val xbl = xboxAuth.authenticateWithXbl(msAccess)
            println("✅ [Auth] تم استلام XBL token")

            // 3. XSTS
            println("🔵 [Auth] مصادقة XSTS...")
            val xsts = xboxAuth.authorizeWithXsts(xbl.Token)
            val userHash = xsts.DisplayClaims.xui.first().uhs
            println("✅ [Auth] تم استلام XSTS token")

            // 4. Minecraft
            println("🔵 [Auth] مصادقة Minecraft...")
            val mcLogin = mcAuth.loginWithXbox(userHash, xsts.Token)
            println("✅ [Auth] تم استلام Minecraft access token")

            // 5. Profile
            println("🔵 [Auth] جلب الملف الشخصي...")
            val profile = mcAuth.getProfile(mcLogin.access_token)
                ?: throw Exception("لا تملك Minecraft على هذا الحساب، أو فشل جلب Profile")
            println("✅ [Auth] مرحباً ${profile.name}!")

            val account = Account(
                id = UUID.randomUUID().toString(),
                username = profile.name,
                uuid = formatUuid(profile.id),
                type = AccountType.MICROSOFT,
                accessToken = mcLogin.access_token,
                refreshToken = msToken.refreshToken,
                expiresAt = System.currentTimeMillis() + mcLogin.expires_in * 1000L,
                skinUrl = profile.skins?.firstOrNull()?.url
            )
            persist(account)
            println("🎉 [Auth] تم حفظ الحساب بنجاح!")
            return account
        } catch (e: Exception) {
            println("═══════════════════════════════════════")
            println("❌ [Auth] خطأ في تسجيل الدخول:")
            println("   النوع: ${e::class.simpleName}")
            println("   الرسالة: ${e.message}")
            println("═══════════════════════════════════════")
            e.printStackTrace()
            throw e
        }
    }

    // ============================================================
    //  VIORA
    // ============================================================

    fun loginVioraOffline(username: String): Account {
        val account = vioraAuth.offlineLogin(username)
        persist(account)
        return account
    }

    suspend fun loginVioraOnline(username: String, password: String): Account {
        val account = vioraAuth.onlineLogin(username, password)
            ?: throw Exception("اسم المستخدم أو كلمة المرور غير صحيحة")
        persist(account)
        return account
    }

    // ============================================================
    //  إدارة الحسابات
    // ============================================================

    fun getAllAccounts(): List<Account> = repo.loadAll()

    fun removeAccount(account: Account) = repo.delete(account)

    /**
     * ✅ تجديد الحساب — يعمل فعلياً الآن!
     * - إذا كان الحساب غير منتهي → يُعاد كما هو
     * - إذا كان Microsoft منتهي → يُجدَّد بالكامل
     * - إذا كان Viora → لا يحتاج تجديد
     */
    suspend fun refresh(account: Account): Account = refreshMutex.withLock {
        // إذا لم ينتهِ، أرجعه
        if (!account.isExpired) {
            return@withLock account
        }

        // Viora لا يحتاج تجديد (offline أو token طويل)
        if (account.type != AccountType.MICROSOFT) {
            println("ℹ️ [Auth] الحساب Viora — لا يحتاج تجديد")
            return@withLock account
        }

        val refreshToken = account.refreshToken
        if (refreshToken.isNullOrBlank()) {
            println("❌ [Auth] لا يوجد refresh token — يجب تسجيل الدخول مجدداً")
            throw Exception("الحساب منتهي — يرجى تسجيل الدخول مجدداً")
        }

        try {
            println("🔄 [Auth] تجديد Microsoft token لـ ${account.username}...")

            // 1. جدّد MS token
            val newMsToken = msAuth.refreshToken(refreshToken)
            val newMsAccess = newMsToken.accessToken
                ?: throw Exception("فشل تجديد MS token")

            // 2. أعد دورة Xbox → XSTS → Minecraft
            val xbl = xboxAuth.authenticateWithXbl(newMsAccess)
            val xsts = xboxAuth.authorizeWithXsts(xbl.Token)
            val userHash = xsts.DisplayClaims.xui.first().uhs
            val mcLogin = mcAuth.loginWithXbox(userHash, xsts.Token)

            // 3. أنشئ حساب محدّث
            val updated = account.copy(
                accessToken = mcLogin.access_token,
                refreshToken = newMsToken.refreshToken ?: account.refreshToken,
                expiresAt = System.currentTimeMillis() + mcLogin.expires_in * 1000L,
                lastUsed = System.currentTimeMillis()
            )

            persist(updated)
            println("✅ [Auth] تم تجديد الحساب بنجاح!")
            updated

        } catch (e: Exception) {
            println("❌ [Auth] فشل تجديد الحساب: ${e.message}")
            throw Exception("فشل تجديد الحساب — يرجى تسجيل الدخول مجدداً: ${e.message}")
        }
    }

    /**
     * ✅ يتحقق من صلاحية الحساب ويجدده إذا لزم
     */
    suspend fun ensureValid(account: Account): Account {
        return if (account.isExpired) refresh(account) else account
    }

    // ============================================================
    //  Helpers
    // ============================================================

    private fun persist(account: Account) {
        val all = repo.loadAll().filterNot { it.uuid == account.uuid } + account
        repo.saveAll(all)
    }

    private fun formatUuid(raw: String): String {
        if (raw.length != 32) return raw
        return "${raw.substring(0, 8)}-${raw.substring(8, 12)}-${raw.substring(12, 16)}-" +
                "${raw.substring(16, 20)}-${raw.substring(20)}"
    }
}