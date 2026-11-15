package app.andhome.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import java.text.Collator

data class InstalledApp(val id: String, val label: String, val icon: Drawable)

class AppsRepository(private val context: Context) {
    fun load(): List<InstalledApp> {
        val pm = context.packageManager
        val collator = Collator.getInstance()
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { entry ->
                InstalledApp(ComponentName(entry.activityInfo.packageName, entry.activityInfo.name).flattenToString(),
                    entry.loadLabel(pm).toString(), entry.loadIcon(pm))
            }.distinctBy { it.id }.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }
    fun launch(id: String): Boolean = runCatching {
        val component = requireNotNull(ComponentName.unflattenFromString(id))
        context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
    }.isSuccess
}
