package com.anubhav.app.ui.gallery

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.anubhav.app.R
import com.anubhav.app.databinding.FragmentGalleryBinding
import com.anubhav.app.utils.LanguageManager
import com.anubhav.app.utils.localized

class GalleryFragment : Fragment() {

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!
    private lateinit var checkboxFreeHomeCollection: CheckBox
    private lateinit var languageManager: LanguageManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val galleryViewModel = ViewModelProvider(this)[GalleryViewModel::class.java]

        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Initialize language manager
        languageManager = LanguageManager(requireContext())

        setupUI()
        observeViewModel(galleryViewModel)

        return root
    }

    private fun setupUI() {
        // Initialize the checkbox
        checkboxFreeHomeCollection = binding.root.findViewById(R.id.checkboxFreeHomeCollection)

        // Set up click listeners for test cards
        setupTestCardClickListeners()

        // Set up checkbox listener
        checkboxFreeHomeCollection.setOnCheckedChangeListener { _, isChecked ->
            val message = if (isChecked) {
                if (languageManager.isBengali()) {
                    "বিনামূল্যে বাড়িতে নমুনা সংগ্রহ নির্বাচিত! আমরা আপনার বাড়িতে নমুনা সংগ্রহ করব।"
                } else {
                    "Free home collection selected! We'll collect your sample at home."
                }
            } else {
                if (languageManager.isBengali()) {
                    "বাড়িতে নমুনা সংগ্রহ বাতিল। অনুগ্রহ করে নমুনা সংগ্রহের জন্য আমাদের কেন্দ্রে যান।"
                } else {
                    "Home collection deselected. Please visit our center for sample collection."
                }
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        // Set up click listener for the features card
        binding.root.findViewById<View>(R.id.text_gallery)?.setOnClickListener {
            showBookingComingSoon()
        }
    }

    private fun setupTestCardClickListeners() {
        // Find all test cards and set click listeners
        val testCards = mutableListOf<View>()
        findTestCards(binding.root, testCards)

        testCards.forEachIndexed { index, card ->
            card.setOnClickListener {
                when (index) {
                    0 -> {
                        val testName = getString(R.string.cbc_test)
                        val preparation = localized(R.string.cbc_preparation)
                        showTestDetails(testName, "₹300", preparation)
                    }

                    1 -> {
                        val testName = getString(R.string.lipid_test)
                        val preparation = localized(R.string.lipid_preparation)
                        showTestDetails(testName, "₹800", preparation)
                    }

                    2 -> {
                        val testName = getString(R.string.thyroid_test)
                        val preparation = localized(R.string.thyroid_preparation)
                        showTestDetails(testName, "₹600", preparation)
                    }

                    else -> {
                        val testName = if (languageManager.isBengali()) {
                            "পরীক্ষা"
                        } else {
                            "Test"
                        }
                        val price = if (languageManager.isBengali()) {
                            "জিজ্ঞাসার উপর দাম"
                        } else {
                            "Price available on inquiry"
                        }
                        val preparation = if (languageManager.isBengali()) {
                            "প্রস্তুতির বিবরণ প্রদান করা হবে"
                        } else {
                            "Preparation details will be provided"
                        }
                        showTestDetails(testName, price, preparation)
                    }
                }
            }
        }
    }

    private fun findTestCards(view: View, cards: MutableList<View>) {
        if (view is com.google.android.material.card.MaterialCardView && view.isClickable) {
            // Check if this card contains test information
            val hasTestContent = findTextViewWithTestContent(view)
            if (hasTestContent) {
                cards.add(view)
            }
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findTestCards(view.getChildAt(i), cards)
            }
        }
    }

    private fun findTextViewWithTestContent(view: View): Boolean {
        if (view is android.widget.TextView) {
            val text = view.text.toString()
            return text.contains("CBC") || text.contains("Lipid") || text.contains("Thyroid") ||
                    text.contains("₹300") || text.contains("₹800") || text.contains("₹600")
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (findTextViewWithTestContent(view.getChildAt(i))) {
                    return true
                }
            }
        }

        return false
    }

    private fun observeViewModel(galleryViewModel: GalleryViewModel) {
        // Keep the existing text observation but don't override the new layout
        galleryViewModel.text.observe(viewLifecycleOwner) { _ ->
            // The text is now part of the static layout, so we don't need to set it dynamically
        }
    }

    private fun showTestDetails(testName: String, price: String, preparation: String) {
        val homeCollectionText = if (checkboxFreeHomeCollection.isChecked) {
            if (languageManager.isBengali()) {
                "\n\nআমি বিনামূল্যে বাড়িতে নমুনা সংগ্রহ পরিষেবা প্রয়োজন।"
            } else {
                "\n\nI also need free home collection service."
            }
        } else {
            ""
        }

        val message = """
            $testName
            
            Price: $price
            Preparation: $preparation
            
            $homeCollectionText
            ${if (languageManager.isBengali()) "কল করুন +91-9230755875 বুকিং এর জন্য" else "Call +91-9230755875 to book"}
            ${if (languageManager.isBengali()) "বা ওয়াটসঅ্যাপ +91-9230755876" else "WhatsApp +91-9230755876"}
        """.trimIndent()

        Toast.makeText(context, message, Toast.LENGTH_LONG).show()

        // Optionally, open WhatsApp directly
        openWhatsAppBooking(testName)
    }

    private fun showBookingComingSoon() {
        Toast.makeText(
            context,
            if (languageManager.isBengali()) {
                "পূর্ণ বুকিং সিস্টেম শীঘ্রই আসছে! এখনও পর্যন্ত, অনুগ্রহ করে +91-9230755875 কল করুন বা +91-9230755876 ওয়াটসঅ্যাপ করুন"
            } else {
                "Full booking system coming soon! For now, please call +91-9230755875 or WhatsApp +91-9230755876"
            },
            Toast.LENGTH_LONG
        ).show()

        // Open WhatsApp for immediate booking
        openWhatsAppBooking("Diagnostic Test")
    }

    private fun openWhatsAppBooking(testName: String) {
        val homeCollectionText = if (checkboxFreeHomeCollection.isChecked) {
            if (languageManager.isBengali()) {
                "\n\nআমি বিনামূল্যে বাড়িতে নমুনা সংগ্রহ পরিষেবা প্রয়োজন।"
            } else {
                "\n\nI also need free home collection service."
            }
        } else {
            ""
        }

        val message =
            if (languageManager.isBengali()) {
                "হাই! আমি নিম্নলিখিত পরীক্ষা বুক করতে চাই: $testName$homeCollectionText\n\nঅনুগ্রহ করে আমাকে বুকিং বিবরণ এবং অর্থ প্রদানের তথ্য প্রদান করুন।"
            } else {
                "Hi! I would like to book the following test: $testName$homeCollectionText\n\nPlease provide me with the booking details and payment information."
            }
        val phoneNumber = "+919230755876"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(
                "https://api.whatsapp.com/send?phone=$phoneNumber&text=${
                    Uri.encode(message)
                }"
            )
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback to regular message
            val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phoneNumber")
                putExtra("sms_body", message)
            }
            try {
                startActivity(smsIntent)
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    if (languageManager.isBengali()) "মেসেজিং অ্যাপ খোলা সম্ভব নয়" else "Unable to open messaging app",
                    Toast.LENGTH_SHORT
                )
                    .show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh language manager when fragment resumes
        languageManager = LanguageManager(requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}