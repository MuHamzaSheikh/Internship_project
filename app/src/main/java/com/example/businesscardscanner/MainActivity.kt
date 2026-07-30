package com.example.businesscardscanner

import android.Manifest
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.businesscardscanner.dialogs.CameraPermissionDialog
import com.example.businesscardscanner.repository.BusinessCardRepository
import com.example.businesscardscanner.utils.PermissionUtils
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var tabLayout: TabLayout
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private var currentTabPosition = 0
    private var searchView: SearchView? = null
    private var currentFragment: Fragment? = null
    private val repository by lazy { BusinessCardRepository.getInstance(applicationContext) }
    private var dynamicCategories: List<String> = emptyList()

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startActivity(CardWorkflowActivity.createIntent(this))
            } else {
                showPermissionDeniedDialog("Camera permission is required to scan cards.") { requestCameraFlow() }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupToolbar()
        setupTabs()
        observeCategories()
        setupClickListeners()
        setupDrawer()

        if (savedInstanceState == null) {
            loadAllCards()
        }
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        tabLayout = findViewById(R.id.tabCategories)
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
    }

    private fun setupToolbar() {

        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        toolbar.setOnMenuItemClickListener { item ->

            when (item.itemId) {

                R.id.action_search -> {
                    val searchItem = item
                    searchView = searchItem.actionView as? SearchView
                    searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                        override fun onQueryTextSubmit(query: String?): Boolean = false

                        override fun onQueryTextChange(newText: String?): Boolean {
                            currentFragment?.let { fragment ->
                                when (fragment) {
                                    is AllFragment -> fragment.updateSearch(newText.orEmpty())
                                    is RecentFragment -> fragment.updateSearch(newText.orEmpty())
                                    is ColleagueFragment -> fragment.updateSearch(newText.orEmpty())
                                    is VipFragment -> fragment.updateSearch(newText.orEmpty())
                                    is FamilyFragment -> fragment.updateSearch(newText.orEmpty())
                                }
                            }
                            return true
                        }
                    })
                    true
                }

                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }

                else -> false
            }
        }
    }

    private fun setupTabs() {
        renderTabs(emptyList())
    }

    private fun observeCategories() {
        lifecycleScope.launch {
            repository.observeCategories().collectLatest { categories ->
                dynamicCategories = categories.filterNot { it.equals("VIP", true) || it.equals("Family", true) || it.equals("Colleague", true) || it.equals("Recent", true) }
                renderTabs(categories)
            }
        }
    }

    private fun renderTabs(categories: List<String>) {
        val selectedLabel = tabLayout.getTabAt(currentTabPosition)?.text?.toString()
        tabLayout.clearOnTabSelectedListeners()
        tabLayout.removeAllTabs()
        val tabs = mutableListOf("All", "Recents", "Colleague", "VIP", "Family")
        categories.filterNot { tabs.any { default -> default.equals(it, true) } }.forEach { tabs.add(it) }
        tabs.forEach { tabLayout.addTab(tabLayout.newTab().setText(it)) }
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTabPosition = tab.position
                when (tab.position) {
                    0 -> loadAllCards()
                    1 -> loadRecentCards()
                    2 -> loadColleagueCards()
                    3 -> loadVipCards()
                    4 -> loadFamilyCards()
                    else -> loadCategoryCards(tab.text?.toString().orEmpty())
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        val restoreIndex = tabs.indexOf(selectedLabel).takeIf { it >= 0 } ?: 0
        tabLayout.getTabAt(restoreIndex)?.select()
    }

    private fun setupClickListeners() {

        findViewById<CardView>(R.id.cardScan).setOnClickListener {
            requestCameraFlow()
        }
    }

    private fun setupDrawer() {

        navigationView.setNavigationItemSelectedListener { item ->

            when (item.itemId) {

                R.id.nav_privacy -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                }

                R.id.nav_share -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Try the Business Card Scanner app.")
                    }
                    startActivity(Intent.createChooser(intent, "Share"))
                }

                R.id.nav_rate -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                    try {
                        startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
                    }
                }
            }

            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun requestCameraFlow() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                startActivity(CardWorkflowActivity.createIntent(this))
            }
            else -> {
                showCameraPermissionDialog()
            }
        }
    }

    private fun showCameraPermissionDialog() {
        CameraPermissionDialog(
            this,
            onAllow = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
            onSkip = { Toast.makeText(this, "Permission Skipped", Toast.LENGTH_SHORT).show() }
        ).show()
    }

    private fun loadAllCards() {
        replaceFragment(AllFragment.newInstance())
    }

    private fun loadRecentCards() {
        replaceFragment(RecentFragment.newInstance())
    }

    private fun loadColleagueCards() {
        replaceFragment(ColleagueFragment.newInstance())
    }

    private fun loadVipCards() {
        replaceFragment(VipFragment.newInstance())
    }

    private fun loadFamilyCards() {
        replaceFragment(FamilyFragment.newInstance())
    }

    private fun loadCategoryCards(category: String) {
        replaceFragment(AllFragment.newInstance(category))
    }

    private fun replaceFragment(fragment: Fragment) {
        currentFragment = fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.contentContainer, fragment)
            .commit()
    }

    private fun showPermissionDeniedDialog(message: String, onRetry: () -> Unit) {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            onRetry()
        } else {
            openAppSettings()
        }
    }

    private fun openAppSettings() {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Open app settings from system settings.", Toast.LENGTH_SHORT).show()
        }
    }
}
