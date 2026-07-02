package com.example.anubhavlifecare

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.anubhavlifecare.databinding.ActivityMainBinding
import com.example.anubhavlifecare.ui.login.LoginActivity
import com.example.anubhavlifecare.utils.CustomerSessionManager
import com.example.anubhavlifecare.utils.LanguageManager
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

        setupLanguageToggle()
        setupFabButtons()

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
                R.id.nav_profile,
            ),
            drawerLayout,
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_logout -> {
                    CustomerSessionManager.clear(this)
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                    true
                }
                else -> {
                    val handled = androidx.navigation.ui.NavigationUI.onNavDestinationSelected(item, navController)
                    if (handled) drawerLayout.closeDrawers()
                    handled
                }
            }
        }

        updateNavigationHeader()
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?) {
        paymentListener?.onPaymentSuccess(razorpayPaymentId)
    }

    override fun onPaymentError(code: Int, description: String?) {
        paymentListener?.onPaymentError(code, description)
    }

    private fun setupFabButtons() {
        binding.appBarMain.fab.setOnClickListener { view ->
            Snackbar.make(view, R.string.book_test_subtitle, Snackbar.LENGTH_LONG)
                .setAction(R.string.menu_book_test) {
                    findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.nav_book_test)
                }
                .setAnchorView(R.id.fab).show()
        }

        binding.appBarMain.whatsappFab.setOnClickListener {
            openWhatsAppChat()
        }
    }

    private fun openWhatsAppChat() {
        val phoneNumber = "919230755876"
        val message = getString(R.string.whatsapp_booking_message)
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phoneNumber?text=${Uri.encode(message)}")),
            )
        } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+919230755876")))
        }
    }

    private fun setupLanguageToggle() {
        updateLanguageToggleButton()
        binding.appBarMain.languageToggleBtn.setOnClickListener {
            languageManager.toggleLanguage()
            updateLanguageToggleButton()
            recreate()
        }
    }

    private fun updateLanguageToggleButton() {
        val button = binding.appBarMain.languageToggleBtn
        if (languageManager.isBengali()) {
            button.text = getString(R.string.language_english)
        } else {
            button.text = getString(R.string.language_bengali)
        }
    }

    private fun updateNavigationHeader() {
        val headerView = binding.navView.getHeaderView(0)
        val name = CustomerSessionManager.getName(this)
            ?: CustomerSessionManager.getPhone(this)
            ?: CustomerSessionManager.getEmail(this).orEmpty()
        headerView.findViewById<android.widget.TextView>(R.id.navHeaderUser)?.text =
            getString(R.string.logged_in_as, name)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
