package br.com.morefocus.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

data class SelectableApp(
    val packageName: String,
    val displayName: String
)

interface InstalledAppsRepository {
    fun getSelectableApps(): List<SelectableApp>
}

class AndroidInstalledAppsRepository(context: Context) : InstalledAppsRepository {
    private val applicationContext = context.applicationContext
    private val packageManager = applicationContext.packageManager

    override fun getSelectableApps(): List<SelectableApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launcherActivities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, 0)
        }

        return launcherActivities
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                if (packageName == applicationContext.packageName) return@mapNotNull null

                SelectableApp(
                    packageName = packageName,
                    displayName = resolveInfo.loadLabel(packageManager)
                        ?.toString()
                        ?.takeIf { it.isNotBlank() }
                        ?: packageName
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(
                compareBy<SelectableApp> { it.displayName.lowercase() }
                    .thenBy { it.packageName }
            )
    }
}
