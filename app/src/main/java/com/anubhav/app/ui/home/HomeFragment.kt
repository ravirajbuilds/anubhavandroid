package com.anubhav.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.anubhav.app.R
import com.anubhav.app.databinding.FragmentHomeBinding
import com.anubhav.app.utils.LanguageManager
import com.anubhav.app.utils.localized
import com.google.android.material.button.MaterialButton

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var languageManager: LanguageManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        languageManager = LanguageManager(requireContext())
        setupButtonClickListeners()
        updateTextsBasedOnLanguage()
        return binding.root
    }

    private fun updateTextsBasedOnLanguage() {
        val ctx = requireContext()
        updateTextInCardView("Welcome to Anubhav", ctx.localized(R.string.welcome_title))
        updateTextInCardView("Your trusted diagnostic partner", ctx.localized(R.string.welcome_subtitle))
        updateTextInCardView("Book Test", ctx.localized(R.string.book_test_title))
        updateTextInCardView("Schedule Appointment", ctx.localized(R.string.book_test_subtitle))
        updateTextInCardView("View Reports", ctx.localized(R.string.view_reports_title))
        updateTextInCardView("My Bookings", ctx.localized(R.string.my_bookings_title))
        updateTextInCardView("Track Status", ctx.localized(R.string.my_bookings_subtitle))
        updateTextInCardView("Home Collection", ctx.localized(R.string.home_collection_title))
        updateTextInCardView("Quick Actions", ctx.localized(R.string.quick_actions))
        updateTextInCardView("Popular Tests", ctx.localized(R.string.popular_tests))
        updateTextInCardView("View All", ctx.localized(R.string.view_all))
        updateTextInCardView("Why Choose Us", ctx.localized(R.string.why_choose_us))
        updateTextInCardView("Need Help? Contact Us", ctx.localized(R.string.contact_us_title))
        updateTextInCardView("Call: +91-9230755875", ctx.localized(R.string.call_label))
        updateTextInCardView("WhatsApp: +91-9230755876", ctx.localized(R.string.whatsapp_label))

        binding.root.findViewById<MaterialButton>(R.id.btnBookTest)?.text =
            ctx.localized(R.string.book_test_title)
        binding.root.findViewById<MaterialButton>(R.id.btnViewReports)?.text =
            ctx.localized(R.string.view_reports_title)
        binding.root.findViewById<MaterialButton>(R.id.btnMyBookings)?.text =
            ctx.localized(R.string.my_bookings_title)
        binding.root.findViewById<MaterialButton>(R.id.btnHomeCollection)?.text =
            ctx.localized(R.string.home_collection_title)
    }

    private fun updateTextInCardView(oldText: String, newText: String) {
        findAndUpdateTextViews(binding.root, oldText, newText)
    }

    private fun findAndUpdateTextViews(view: View, oldText: String, newText: String) {
        if (view is TextView && view.text.toString().equals(oldText, ignoreCase = true)) {
            view.text = newText
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findAndUpdateTextViews(view.getChildAt(i), oldText, newText)
            }
        }
    }

    private fun setupButtonClickListeners() {
        binding.root.findViewById<MaterialButton>(R.id.btnBookTest)?.setOnClickListener {
            findNavController().navigate(R.id.nav_book_test)
        }
        binding.root.findViewById<MaterialButton>(R.id.btnViewReports)?.setOnClickListener {
            findNavController().navigate(R.id.nav_my_reports)
        }
        binding.root.findViewById<MaterialButton>(R.id.btnMyBookings)?.setOnClickListener {
            findNavController().navigate(R.id.nav_my_bookings)
        }
        binding.root.findViewById<MaterialButton>(R.id.btnHomeCollection)?.setOnClickListener {
            Toast.makeText(requireContext(), localized(R.string.home_collection_info), Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateTextsBasedOnLanguage()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
