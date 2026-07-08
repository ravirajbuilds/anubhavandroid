package com.anubhav.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.anubhav.app.databinding.ActivityMainBinding
import com.anubhav.app.ui.login.LoginActivity
import com.anubhav.app.utils.CustomerSessionManager
import com.anubhav.app.utils.LanguageManager
import com.anubhav.app.utils.localized
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.razorpay.PaymentResultListener

class MainActivity : AppCompatActivity(), PaymentResultListener {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var languageManager: LanguageManager
    private var paymentListener: PaymentResultListener? = null

    fun setPaymentListener(listener: PaymentResultListener?) {
        paymentListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!CustomerSessionManager.isLoggedIn(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        languageManager = LanguageManager(this)
        setSupportActionBar(binding.appBarMain.toolbar)
        supportActionBar?.title = localized(R.string.app_name)

        setupLanguageToggle()
        setupFabButtons()
        setupProfileBanner()
        setupNavigation()
        updateNavigationHeader()
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?) {
        paymentListener?.onPaymentSuccess(razorpayPaymentId)
    }

    override fun onPaymentError(code: Int, description: String?) {
        paymentListener?.onPaymentError(code, description)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        menu.findItem(R.id.action_settings)?.title = localized(R.string.action_settings)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.nav_settings)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun setupNavigation() {
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home,
                R.id.nav_book_test,
                R.id.nav_my_bookings,
                R.id.nav_my_reports,
                R.id.nav_pending_payments,
                R.id.nav_settings,
            ),
            drawerLayout,
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        localizeDrawerMenu(navView)

        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_logout -> {
                    CustomerSessionManager.clear(this)
                    FirebaseAuth.getInstance().signOut()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                    true
                }
                else -> {
                    val handled = NavigationUI.onNavDestinationSelected(item, navController)
                    if (handled) drawerLayout.closeDrawers()
                    handled
                }
            }
        }
    }

    private fun setupProfileBanner() {
        val banner = findViewById<View>(R.id.profileBanner)
        val phone = CustomerSessionManager.getPhone(this)
        if (phone.isNullOrBlank()) {
            banner.visibility = View.VISIBLE
            banner.findViewById<TextView>(R.id.tvBannerTitle).text =
                localized(R.string.phone_required_title)
            banner.findViewById<TextView>(R.id.tvBannerMessage).text =
                localized(R.string.complete_profile_banner)
            banner.findViewById<View>(R.id.btnBannerWhatsApp).setOnClickListener {
                openWhatsAppChat()
            }
        } else {
            banner.visibility = View.GONE
        }
    }

    private fun setupFabButtons() {
        binding.appBarMain.fab.setOnClickListener { view ->
            Snackbar.make(view, localized(R.string.book_test_subtitle), Snackbar.LENGTH_LONG)
                .setAction(localized(R.string.menu_book_test)) {
                    findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.nav_book_test)
                }
                .setAnchorView(R.id.fab)
                .show()
        }

        binding.appBarMain.whatsappFab.setOnClickListener {
            openWhatsAppChat()
        }
    }

    private fun setupLanguageToggle() {
        binding.appBarMain.languageToggleBtn.setOnClickListener {
            languageManager.toggleLanguage()
            recreate()
        }
        updateLanguageButton()
    }

    private fun updateLanguageButton() {
        binding.appBarMain.languageToggleBtn.text =
            if (languageManager.isBengali()) {
                getString(R.string.language_english)
            } else {
                getString(R.string.language_bengali)
            }
    }

    private fun updateNavigationHeader() {
        val headerView = binding.navView.getHeaderView(0)
        headerView.findViewById<TextView>(R.id.navHeaderTitle)?.text =
            localized(R.string.nav_header_title)
        headerView.findViewById<TextView>(R.id.navHeaderSubtitle)?.text =
            localized(R.string.nav_header_subtitle)

        val name = CustomerSessionManager.getName(this)
            ?: CustomerSessionManager.getPhone(this)
            ?: CustomerSessionManager.getEmail(this)
            ?: ""
        headerView.findViewById<TextView>(R.id.navHeaderUser)?.text =
            localized(R.string.logged_in_as, name)
    }

    private fun localizeDrawerMenu(navView: NavigationView) {
        val menu = navView.menu
        menu.findItem(R.id.nav_home)?.title = localized(R.string.menu_home)
        menu.findItem(R.id.nav_book_test)?.title = localized(R.string.menu_book_test)
        menu.findItem(R.id.nav_my_bookings)?.title = localized(R.string.menu_my_bookings)
        menu.findItem(R.id.nav_my_reports)?.title = localized(R.string.menu_my_reports)
        menu.findItem(R.id.nav_pending_payments)?.title =
            localized(R.string.pending_payments_title)
        menu.findItem(R.id.nav_settings)?.title = localized(R.string.menu_settings)
        menu.findItem(R.id.nav_logout)?.title = localized(R.string.logout)
    }

    private fun openWhatsAppChat() {
        val phoneNumber = "919230755876"
        val message = localized(R.string.whatsapp_booking_message)
        try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://wa.me/$phoneNumber?text=${Uri.encode(message)}"),
                ),
            )
        } catch (_: Exception) {
            Snackbar.make(
                binding.root,
                localized(R.string.whatsapp_not_available),
                Snackbar.LENGTH_LONG,
            ).show()
        }
    }
}
