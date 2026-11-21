package com.example.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.data.model.OemFamily

data class OemActionRecipe(
    val title: String,
    val description: String,
    val intents: List<Intent>
)

object OemDetector {

    fun detectOem(
        manufacturer: String = Build.MANUFACTURER.orEmpty(),
        brand: String = Build.BRAND.orEmpty()
    ): OemFamily {
        val combined = "$manufacturer $brand".lowercase()
        return when {
            combined.contains("xiaomi") || combined.contains("redmi") || combined.contains("poco") -> {
                OemFamily.XIAOMI
            }
            combined.contains("oppo") || combined.contains("realme") || combined.contains("oneplus") -> {
                OemFamily.OPPO_REALME_ONEPLUS
            }
            combined.contains("vivo") || combined.contains("iqoo") -> {
                OemFamily.VIVO_IQOO
            }
            combined.contains("samsung") -> {
                OemFamily.SAMSUNG
            }
            combined.contains("tecno") || combined.contains("infinix") || combined.contains("itel") || combined.contains("transsion") -> {
                OemFamily.TRANSSION
            }
            else -> OemFamily.OTHER
        }
    }

    fun getOemSurvivalRecipes(context: Context, oem: OemFamily = detectOem()): List<OemActionRecipe> {
        val pkg = context.packageName
        val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", pkg, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val recipes = mutableListOf<OemActionRecipe>()

        when (oem) {
            OemFamily.XIAOMI -> {
                recipes.add(
                    OemActionRecipe(
                        title = "MIUI / HyperOS Autostart",
                        description = "Allow Mass WA Broadcast to start automatically after reboot and schedule triggers.",
                        intents = listOf(
                            Intent().setComponent(
                                ComponentName(
                                    "com.miui.securitycenter",
                                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                                )
                            ),
                            appDetailsIntent
                        )
                    )
                )
                recipes.add(
                    OemActionRecipe(
                        title = "Display Pop-up Windows & Lock Screen",
                        description = "Enable 'Display pop-up windows while running in the background' and 'Show on Lock screen'.",
                        intents = listOf(
                            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                                setClassName(
                                    "com.miui.securitycenter",
                                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                                )
                                putExtra("extra_pkgname", pkg)
                            },
                            appDetailsIntent
                        )
                    )
                )
            }

            OemFamily.OPPO_REALME_ONEPLUS -> {
                recipes.add(
                    OemActionRecipe(
                        title = "ColorOS / OxygenOS Auto-Launch",
                        description = "Enable Auto-launch and Associated launch so scheduled alarms never freeze in standby.",
                        intents = listOf(
                            Intent().setComponent(
                                ComponentName(
                                    "com.coloros.safecenter",
                                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                                )
                            ),
                            Intent().setComponent(
                                ComponentName(
                                    "com.oplus.battery",
                                    "com.oplus.powermanager.fuelgaue.PowerUsageModelActivity"
                                )
                            ),
                            appDetailsIntent
                        )
                    )
                )
            }

            OemFamily.VIVO_IQOO -> {
                recipes.add(
                    OemActionRecipe(
                        title = "Funtouch / OriginOS Autostart & High Background Power",
                        description = "Enable Autostart and Allow High Background Power Consumption for scheduled broadcasts.",
                        intents = listOf(
                            Intent().setComponent(
                                ComponentName(
                                    "com.iqoo.secure",
                                    "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
                                )
                            ),
                            Intent().setComponent(
                                ComponentName(
                                    "com.vivo.permissionmanager",
                                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                                )
                            ),
                            appDetailsIntent
                        )
                    )
                )
            }

            OemFamily.SAMSUNG -> {
                recipes.add(
                    OemActionRecipe(
                        title = "Samsung Unrestricted Battery & Sleeping Apps",
                        description = "Set Battery to 'Unrestricted' and remove Mass WA Broadcast from Deep Sleeping Apps.",
                        intents = listOf(appDetailsIntent)
                    )
                )
            }

            OemFamily.TRANSSION -> {
                recipes.add(
                    OemActionRecipe(
                        title = "HiOS / XOS PhoneMaster Auto-Start",
                        description = "Enable Auto-Start & Battery Whitelist in PhoneMaster so scheduled broadcasts survive Tecno/Infinix standby.",
                        intents = listOf(
                            Intent().setComponent(
                                ComponentName(
                                    "com.transsion.phonemaster",
                                    "com.cyin.himgr.autostart.AutoStartActivity"
                                )
                            ),
                            appDetailsIntent
                        )
                    )
                )
            }

            OemFamily.OTHER -> {
                recipes.add(
                    OemActionRecipe(
                        title = "App Battery Unrestricted",
                        description = "Verify Battery Usage is set to Unrestricted in App Info for reliable scheduled runs.",
                        intents = listOf(appDetailsIntent)
                    )
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            recipes.add(
                OemActionRecipe(
                    title = "Android 13+ Allow Restricted Settings (Sideloaded APK)",
                    description = "If Accessibility is greyed out: tap Open App Info -> top-right 3-dot menu -> 'Allow restricted settings'.",
                    intents = listOf(appDetailsIntent)
                )
            )
        }

        return recipes
    }

    fun launchFirstAvailableIntent(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            try {
                val copy = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (copy.resolveActivity(context.packageManager) != null) {
                    context.startActivity(copy)
                    return true
                }
            } catch (_: Throwable) {
                // Try next fallback
            }
        }
        return runCatching {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
            true
        }.getOrDefault(false)
    }
}
