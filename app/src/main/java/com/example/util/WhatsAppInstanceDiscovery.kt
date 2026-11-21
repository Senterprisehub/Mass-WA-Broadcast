package com.example.util

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserManager
import com.example.data.model.WhatsAppInstance

object WhatsAppInstanceDiscovery {

    const val PKG_WHATSAPP = "com.whatsapp"
    const val PKG_WHATSAPP_BUSINESS = "com.whatsapp.w4b"
    const val DEFAULT_CLONE_SERIAL = 999L

    val OFFICIAL_WHATSAPP_PACKAGES = setOf(PKG_WHATSAPP, PKG_WHATSAPP_BUSINESS)

    /**
     * Enumerates official WhatsApp and WhatsApp Business across primary and OEM clone/work
     * profiles (Vivo/iQOO II·WhatsApp, Xiaomi Dual Apps #999, Oppo/Realme/OnePlus App Cloner,
     * Samsung Dual Messenger #95, Android Work Profile #10) without requiring QUERY_ALL_PACKAGES.
     *
     * Always exposes both Primary and 2nd Clone / Dual App choices for installed packages because
     * several Indian OEMs (notably Vivo Funtouch OS) hide clone profiles from UserManager APIs
     * while still presenting a "Complete action using" chooser between Primary and 2nd Clone.
     */
    fun discoverInstances(context: Context): List<WhatsAppInstance> {
        val discovered = mutableListOf<WhatsAppInstance>()
        val seenKeys = mutableSetOf<String>()

        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        val primarySerial = userManager?.getSerialNumberForUser(Process.myUserHandle()) ?: 0L

        if (userManager != null && launcherApps != null) {
            val profiles = runCatching { userManager.userProfiles }.getOrElse { listOf(Process.myUserHandle()) }
            for (profile in profiles) {
                val serial = runCatching { userManager.getSerialNumberForUser(profile) }.getOrDefault(0L)
                val isCloneProfile = serial != primarySerial && serial > 0L

                for (pkg in OFFICIAL_WHATSAPP_PACKAGES) {
                    val activities = runCatching {
                        launcherApps.getActivityList(pkg, profile)
                    }.getOrDefault(emptyList())

                    if (activities.isNotEmpty()) {
                        val key = "$pkg#$isCloneProfile"
                        if (seenKeys.add(key)) {
                            val isBusiness = pkg == PKG_WHATSAPP_BUSINESS
                            val baseLabel = if (isBusiness) "WhatsApp Business" else "WhatsApp"
                            val profileTag = when {
                                !isCloneProfile -> "Primary"
                                serial == 999L -> "2nd Clone / Dual App"
                                serial == 95L -> "2nd Clone (Dual Messenger)"
                                else -> "2nd Clone / Work #$serial"
                            }
                            discovered.add(
                                WhatsAppInstance(
                                    packageName = pkg,
                                    label = "$baseLabel ($profileTag)",
                                    userSerial = if (isCloneProfile) serial else 0L,
                                    isBusiness = isBusiness,
                                    isClone = isCloneProfile
                                )
                            )
                        }
                    }
                }
            }
        }

        // Fallback check via PackageManager (protected by manifest <queries>)
        val pm = context.packageManager
        for (pkg in OFFICIAL_WHATSAPP_PACKAGES) {
            val installed = runCatching { pm.getPackageInfo(pkg, 0) != null }.getOrDefault(false)
            if (installed && seenKeys.add("$pkg#false")) {
                val isBusiness = pkg == PKG_WHATSAPP_BUSINESS
                discovered.add(
                    WhatsAppInstance(
                        packageName = pkg,
                        label = if (isBusiness) "WhatsApp Business (Primary)" else "WhatsApp (Primary)",
                        userSerial = 0L,
                        isBusiness = isBusiness,
                        isClone = false
                    )
                )
            }
        }

        return ensurePrimaryAndCloneOptions(discovered)
    }

    /**
     * Pure helper guaranteeing every installed WhatsApp / Business app exposes both Primary
     * and 2nd Clone / Dual App choices (and standard selectable defaults when running on a
     * test device without WhatsApp installed).
     */
    fun ensurePrimaryAndCloneOptions(detected: List<WhatsAppInstance>): List<WhatsAppInstance> {
        val result = detected.toMutableList()
        val packagesToInclude = if (result.isEmpty()) {
            listOf(PKG_WHATSAPP, PKG_WHATSAPP_BUSINESS)
        } else {
            result.map { it.packageName }.distinct()
        }

        for (pkg in packagesToInclude) {
            val isBusiness = pkg == PKG_WHATSAPP_BUSINESS
            val baseLabel = if (isBusiness) "WhatsApp Business" else "WhatsApp"

            if (result.none { it.packageName == pkg && !it.isClone }) {
                result.add(
                    WhatsAppInstance(
                        packageName = pkg,
                        label = "$baseLabel (Primary)",
                        userSerial = 0L,
                        isBusiness = isBusiness,
                        isClone = false
                    )
                )
            }
            if (result.none { it.packageName == pkg && it.isClone }) {
                result.add(
                    WhatsAppInstance(
                        packageName = pkg,
                        label = "$baseLabel (2nd Clone / Dual App)",
                        userSerial = DEFAULT_CLONE_SERIAL,
                        isBusiness = isBusiness,
                        isClone = true
                    )
                )
            }
        }

        return result.sortedWith(
            compareBy<WhatsAppInstance> { it.isBusiness }
                .thenBy { it.isClone }
                .thenBy { it.userSerial }
        )
    }
}
