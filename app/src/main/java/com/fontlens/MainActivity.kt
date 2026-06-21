package com.fontlens

import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.navigation.fragment.NavHostFragment
import com.fontlens.data.FontRepository
import com.fontlens.databinding.ActivityMainBinding
import com.fontlens.databinding.ItemDrawerFolderBinding
import android.view.ContextThemeWrapper
import com.fontlens.utils.FolderScanner
import com.fontlens.utils.ThemeManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var backPressedOnce = false
    private val backToastHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun attachBaseContext(newBase: Context) {
        FontRepository.load(newBase)
        val s = FontRepository.settings
        ThemeManager.applyTheme(s)
        ThemeManager.applyNightMode(s)
        val themeResId = ThemeManager.themeResId(s)
        super.attachBaseContext(android.view.ContextThemeWrapper(newBase, themeResId))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val s = FontRepository.settings
        setTheme(ThemeManager.themeResId(s))
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyBottomNavTint()

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        binding.bottomNav.setOnItemSelectedListener { item ->
            val destId = when (item.itemId) {
                R.id.nav_library   -> R.id.fontListFragment
                R.id.nav_favorites -> R.id.favoritesFragment
                R.id.nav_settings  -> R.id.settingsFragment
                else -> return@setOnItemSelectedListener false
            }
            navController.navigate(destId, null,
                androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(R.id.fontListFragment, false)
                    .setLaunchSingleTop(true).build()
            )
            true
        }

        val topDests = setOf(R.id.fontListFragment, R.id.favoritesFragment, R.id.settingsFragment)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.visibility =
                if (destination.id in topDests) View.VISIBLE else View.GONE
            when (destination.id) {
                R.id.fontListFragment  -> binding.bottomNav.menu.findItem(R.id.nav_library)?.isChecked   = true
                R.id.favoritesFragment -> binding.bottomNav.menu.findItem(R.id.nav_favorites)?.isChecked = true
                R.id.settingsFragment  -> binding.bottomNav.menu.findItem(R.id.nav_settings)?.isChecked  = true
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START); return
                }
                val currentDest = navController.currentDestination?.id
                if (currentDest !in topDests) { navController.popBackStack(); return }
                if (currentDest == R.id.settingsFragment || currentDest == R.id.favoritesFragment) {
                    navController.navigate(R.id.fontListFragment, null,
                        androidx.navigation.NavOptions.Builder()
                            .setPopUpTo(R.id.fontListFragment, true)
                            .setLaunchSingleTop(true).build()
                    )
                    binding.bottomNav.menu.findItem(R.id.nav_library)?.isChecked = true
                    return
                }
                if (backPressedOnce) { backToastHandler.removeCallbacksAndMessages(null); finish(); return }
                backPressedOnce = true
                Toast.makeText(this@MainActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
                backToastHandler.postDelayed({ backPressedOnce = false }, 2000)
            }
        })
    }

    private fun applyBottomNavTint() {
        val p      = ThemeManager.activePalette
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val tint   = ColorStateList(states, intArrayOf(p.accent, p.textMuted))
        binding.bottomNav.itemIconTintList = tint
        binding.bottomNav.itemTextColor    = tint
    }

    fun openDrawer()  { refreshDrawer(); binding.drawerLayout.openDrawer(GravityCompat.START) }
    fun closeDrawer() { binding.drawerLayout.closeDrawer(GravityCompat.START) }

    fun refreshDrawer() {
        val container = binding.folderListContainer
        val tvEmpty   = binding.tvDrawerEmpty
        container.removeAllViews()
        val folders   = FontRepository.getSavedFolderUris()
        tvEmpty.visibility = if (folders.isEmpty()) View.VISIBLE else View.GONE
        if (folders.isEmpty()) return

        val inflater  = LayoutInflater.from(this)
        val recursive = FontRepository.settings.folderRecursive

        // PHASE 1: Draw all parent folder rows immediately on Main thread.
        val bindingMap = mutableMapOf<android.net.Uri, ItemDrawerFolderBinding>()
        folders.forEach { uri ->
            val fb = ItemDrawerFolderBinding.inflate(inflater, container, false)
            fb.tvFolderPath.text              = getFolderDisplayName(uri)
            fb.btnExpand.visibility           = View.GONE
            fb.containerSubfolders.visibility = View.GONE
            bindFolderRowButtons(fb, uri)
            container.addView(fb.root)
            bindingMap[uri] = fb
        }

        // PHASE 2: Scan sub-folders on IO; attach expand arrows when done.
        if (!recursive) return
        lifecycleScope.launch(Dispatchers.IO) {
            val subFolderMap = folders.associateWith { uri ->
                FolderScanner.collectSubFolders(this@MainActivity, uri)
            }
            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext
                bindingMap.forEach { (uri, fb) ->
                    val subs = subFolderMap[uri] ?: return@forEach
                    if (subs.isNotEmpty()) attachSubFolders(fb, inflater, subs)
                }
            }
        }
    }

    private fun bindFolderRowButtons(fb: ItemDrawerFolderBinding, uri: android.net.Uri) {
        val name = getFolderDisplayName(uri)
        fb.btnReload.setOnClickListener {
            closeDrawer()
            FontRepository.unmarkFolderLoaded(uri)
            getLibraryFragment()?.reloadFolder(uri)
        }
        fb.btnRemoveFolder.setOnClickListener {
            android.app.AlertDialog.Builder(
                ContextThemeWrapper(this, ThemeManager.currentThemeResId(this)))
                .setTitle("Remove Folder")
                .setMessage("Remove \"$name\" and all its fonts from the library?")
                .setPositiveButton("Remove") { _, _ ->
                    FontRepository.removeSavedFolder(uri, this)
                    refreshDrawer()
                    getLibraryFragment()?.refresh()
                }
                .setNegativeButton("Cancel", null).show()
        }
    }

    private fun attachSubFolders(
        fb: ItemDrawerFolderBinding,
        inflater: LayoutInflater,
        subFolders: List<Pair<String, android.net.Uri>>
    ) {
        fb.btnExpand.visibility = View.VISIBLE
        var expanded = false
        subFolders.forEach { (subName, subUri) ->
            val sb = com.fontlens.databinding.ItemDrawerSubfolderBinding
                .inflate(inflater, fb.containerSubfolders, false)
            sb.tvSubfolderName.text = subName
            sb.btnRemoveSubfolder.setOnClickListener {
                android.app.AlertDialog.Builder(
                    ContextThemeWrapper(this, ThemeManager.currentThemeResId(this)))
                    .setTitle("Remove Sub-folder")
                    .setMessage("Remove \"$subName\" fonts from the library?")
                    .setPositiveButton("Remove") { _, _ ->
                        FontRepository.removeFontsByFolder(FolderScanner.uriToPath(subUri), this)
                        FontRepository.save(this)
                        fb.containerSubfolders.removeView(sb.root)
                        if (fb.containerSubfolders.childCount == 0) {
                            fb.containerSubfolders.visibility = View.GONE
                            fb.btnExpand.visibility = View.GONE
                        }
                        getLibraryFragment()?.refresh()
                    }
                    .setNegativeButton("Cancel", null).show()
            }
            fb.containerSubfolders.addView(sb.root)
        }
        fb.btnExpand.setOnClickListener {
            expanded = !expanded
            fb.btnExpand.text = if (expanded) "⌃" else "⌄"
            fb.containerSubfolders.visibility = if (expanded) View.VISIBLE else View.GONE
        }
    }

    fun getLibraryFragment(): com.fontlens.ui.list.FontListFragment? =
        supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
            ?.childFragmentManager?.fragments?.firstOrNull()
            as? com.fontlens.ui.list.FontListFragment

    private fun getFolderDisplayName(uri: Uri): String =
        try { "/" + (uri.lastPathSegment ?: uri.toString()).substringAfter(":") }
        catch (_: Exception) { uri.toString() }
}
