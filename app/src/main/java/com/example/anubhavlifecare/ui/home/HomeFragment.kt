package com.example.anubhavlifecare.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.anubhavlifecare.R
import com.example.anubhavlifecare.databinding.FragmentHomeBinding
import com.example.anubhavlifecare.utils.LanguageManager
import com.google.android.material.button.MaterialButton

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var languageManager: LanguageManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Initialize language manager
        languageManager = LanguageManager(requireContext())

        setupUI()
        observeViewModel(homeViewModel)
        updateTextsBasedOnLanguage()
        return root
    }

    private fun setupUI() {
        setupButtonClickListeners()

        // Remove references to non-existent card elements
        // The cards have been replaced with Material buttons
    }

    private fun observeViewModel(homeViewModel: HomeViewModel) {
        homeViewModel.welcomeMessage.observe(viewLifecycleOwner) { message ->
            // Use localized welcome message instead
            updateWelcomeMessage()
        }

        homeViewModel.popularTests.observe(viewLifecycleOwner) { tests ->
            // You can update UI to show popular tests count or info if needed
            // For now, we keep the welcome message as is
        }
    }

    private fun updateTextsBasedOnLanguage() {
        val languageManager = LanguageManager(requireContext())
        val welcomeTitle = if (languageManager.getCurrentLanguage() == "bn") {
            "অনুভবে স্বাগতম"
        } else {
            "Welcome to Anubhav"
        }

        // Find the welcome texts in the layout and update them
        // The text is now directly in the layout XML, no need to update programmatically
        updateWelcomeMessage()
        updateQuickActions()
        updatePopularTests()
        updateFeatures()
        updateContact()
    }

    private fun updateWelcomeMessage() {
        // Update welcome title
        val welcomeTitle = if (languageManager.isBengali()) {
            getString(R.string.welcome_title_bn)
        } else {
            getString(R.string.welcome_title)
        }

        val welcomeSubtitle = if (languageManager.isBengali()) {
            getString(R.string.welcome_subtitle_bn)
        } else {
            getString(R.string.welcome_subtitle)
        }

        // Find the welcome texts in the layout and update them
        // Removed the text_home reference
    }

    private fun updateQuickActions() {
        val quickActionsTitle = if (languageManager.isBengali()) {
            getString(R.string.quick_actions_bn)
        } else {
            getString(R.string.quick_actions)
        }

        val bookTestTitle = if (languageManager.isBengali()) {
            getString(R.string.book_test_title_bn)
        } else {
            getString(R.string.book_test_title)
        }

        val bookTestSubtitle = if (languageManager.isBengali()) {
            getString(R.string.book_test_subtitle_bn)
        } else {
            getString(R.string.book_test_subtitle)
        }

        val myBookingsTitle = if (languageManager.isBengali()) {
            getString(R.string.my_bookings_title_bn)
        } else {
            getString(R.string.my_bookings_title)
        }

        val myBookingsSubtitle = if (languageManager.isBengali()) {
            getString(R.string.my_bookings_subtitle_bn)
        } else {
            getString(R.string.my_bookings_subtitle)
        }

        // Update text views by finding them in the layout
        updateTextInCardView("Quick Actions", quickActionsTitle)
        updateTextInCardView("Book Test", bookTestTitle)
        updateTextInCardView("Schedule Appointment", bookTestSubtitle)
        updateTextInCardView("My Bookings", myBookingsTitle)
        updateTextInCardView("Track Status", myBookingsSubtitle)
    }

    private fun updatePopularTests() {
        val popularTests = if (languageManager.isBengali()) {
            getString(R.string.popular_tests_bn)
        } else {
            getString(R.string.popular_tests)
        }

        val viewAll = if (languageManager.isBengali()) {
            getString(R.string.view_all_bn)
        } else {
            getString(R.string.view_all)
        }

        updateTextInCardView("Popular Tests", popularTests)
        updateTextInCardView("View All", viewAll)
    }

    private fun updateFeatures() {
        val whyChooseUs = if (languageManager.isBengali()) {
            getString(R.string.why_choose_us_bn)
        } else {
            getString(R.string.why_choose_us)
        }

        val features = if (languageManager.isBengali()) {
            "${getString(R.string.feature_home_collection_bn)}\n${getString(R.string.feature_same_day_bn)}\n${
                getString(
                    R.string.feature_secure_payment_bn
                )
            }\n${getString(R.string.feature_notifications_bn)}"
        } else {
            "${getString(R.string.feature_home_collection)}\n${getString(R.string.feature_same_day)}\n${
                getString(
                    R.string.feature_secure_payment
                )
            }\n${getString(R.string.feature_notifications)}"
        }

        updateTextInCardView("Why Choose Us", whyChooseUs)
    }

    private fun updateContact() {
        val contactTitle = if (languageManager.isBengali()) {
            getString(R.string.contact_us_title_bn)
        } else {
            getString(R.string.contact_us_title)
        }

        val callLabel = if (languageManager.isBengali()) {
            getString(R.string.call_label_bn)
        } else {
            getString(R.string.call_label)
        }

        val whatsappLabel = if (languageManager.isBengali()) {
            getString(R.string.whatsapp_label_bn)
        } else {
            getString(R.string.whatsapp_label)
        }

        updateTextInCardView("Need Help? Contact Us", contactTitle)
        updateTextInCardView("Call: +91-9230755875", callLabel)
        updateTextInCardView("WhatsApp: +91-9230755876", whatsappLabel)
    }

    private fun updateTextViewById(viewId: Int, newText: String) {
        binding.root.findViewById<android.widget.TextView>(viewId)?.text = newText
    }

    private fun updateTextInCardView(oldText: String, newText: String) {
        // Helper function to find and update text views with specific content
        findAndUpdateTextViews(binding.root, oldText, newText)
    }

    private fun findAndUpdateTextViews(view: View, oldText: String, newText: String) {
        if (view is android.widget.TextView && view.text.toString()
                .equals(oldText, ignoreCase = true)
        ) {
            view.text = newText
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findAndUpdateTextViews(view.getChildAt(i), oldText, newText)
            }
        }
    }

    private fun setupButtonClickListeners() {
        // Book Test Button
        binding.root.findViewById<MaterialButton>(R.id.btnBookTest)?.setOnClickListener {
            // Navigate to booking/gallery fragment
            try {
                findNavController().navigate(R.id.nav_book_test)
                android.widget.Toast.makeText(
                    requireContext(),
                    "Navigate to Book Test",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    requireContext(),
                    "Opening booking section...",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }

        // View Reports Button
        binding.root.findViewById<MaterialButton>(R.id.btnViewReports)?.setOnClickListener {
            // Navigate to reports fragment
            try {
                findNavController().navigate(R.id.nav_my_reports)
                android.widget.Toast.makeText(
                    requireContext(),
                    "Navigate to View Reports",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    requireContext(),
                    "Opening reports section...",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }

        // My Bookings Button
        binding.root.findViewById<MaterialButton>(R.id.btnMyBookings)?.setOnClickListener {
            // Navigate to bookings fragment
            try {
                findNavController().navigate(R.id.nav_my_bookings)
                android.widget.Toast.makeText(
                    requireContext(),
                    "Navigate to My Bookings",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    requireContext(),
                    "Opening my bookings...",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }

        // Home Collection Button
        binding.root.findViewById<MaterialButton>(R.id.btnHomeCollection)?.setOnClickListener {
            // Show home collection info
            showHomeCollectionInfo()
        }
    }

    private fun showHomeCollectionInfo() {
        android.widget.Toast.makeText(
            requireContext(),
            "Free home collection available!\nCall +91-9230755876 or use WhatsApp",
            android.widget.Toast.LENGTH_LONG
        ).show()
        
        // Optionally navigate to booking page with home collection pre-selected
        // findNavController().navigate(R.id.nav_gallery)
    }

    override fun onResume() {
        super.onResume()
        // Refresh text when fragment resumes (e.g., after language change)
        updateTextsBasedOnLanguage()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}