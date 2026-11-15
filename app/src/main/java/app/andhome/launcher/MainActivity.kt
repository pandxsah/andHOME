package app.andhome.launcher

import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    private val model: HomeViewModel by viewModels()
    private var homeSignal by mutableIntStateOf(0)
    private var isDefault by mutableStateOf(false)
    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { checkRole() }
    private val packages = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { model.refresh() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(packages, filter, RECEIVER_EXPORTED)
        else registerReceiver(packages, filter)
        setContent {
            Launcher(model, homeSignal, isDefault, ::requestHome) { dark ->
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }
    override fun onResume() { super.onResume(); checkRole(); model.refresh() }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        homeSignal++
    }
    override fun onDestroy() { unregisterReceiver(packages); super.onDestroy() }
    private fun checkRole() {
        isDefault = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
    }
    private fun requestHome() {
        val manager = getSystemService(RoleManager::class.java)
        val intent = if (manager.isRoleAvailable(RoleManager.ROLE_HOME))
            manager.createRequestRoleIntent(RoleManager.ROLE_HOME) else Intent(Settings.ACTION_HOME_SETTINGS)
        runCatching { roleRequest.launch(intent) }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
        }
    }
}
