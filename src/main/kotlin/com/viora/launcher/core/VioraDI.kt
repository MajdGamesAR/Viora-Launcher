package com.viora.launcher.core

import com.viora.launcher.core.auth.AuthManager
import com.viora.launcher.core.mods.CurseForgeService
import com.viora.launcher.core.mods.ModrinthService
import com.viora.launcher.core.storage.AccountRepository
import com.viora.launcher.core.storage.SecureStorage
import com.viora.launcher.core.version.VersionService

/**
 * ✅ حقن تبعيات بسيط (بدون مكتبات خارجية)
 */
object VioraDI {

    val secureStorage: SecureStorage by lazy { SecureStorage() }

    val accountRepository: AccountRepository by lazy {
        AccountRepository(secureStorage)
    }

    val authManager: AuthManager by lazy {
        AuthManager(accountRepository)
    }

    val versionService: VersionService by lazy { VersionService() }

    val modrinthService: ModrinthService by lazy { ModrinthService.shared }

    val curseForgeService: CurseForgeService by lazy {
        CurseForgeService.fromSystemProperty()
    }

    /**
     * ✅ تحقق من صحة التهيئة
     */
    fun validate(): List<String> {
        val warnings = mutableListOf<String>()
        if (!curseForgeService.isConfigured()) {
            warnings.add("CurseForge API key not configured — search will be limited")
        }
        return warnings
    }
}