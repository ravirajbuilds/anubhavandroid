package com.anubhav.app.ui.slideshow

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.anubhav.app.databinding.FragmentSlideshowBinding
import com.anubhav.app.utils.LanguageManager

class SlideshowFragment : Fragment() {

    private var _binding: FragmentSlideshowBinding? = null
    private lateinit var languageManager: LanguageManager

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val slideshowViewModel =
            ViewModelProvider(this).get(SlideshowViewModel::class.java)

        _binding = FragmentSlideshowBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Initialize language manager
        languageManager = LanguageManager(requireContext())

        // Update the text to show bookings information
        slideshowViewModel.text.observe(viewLifecycleOwner) { originalText ->
            updateBookingsText()
        }

        return root
    }

    override fun onResume() {
        super.onResume()
        // Refresh language manager and update text when fragment resumes
        languageManager = LanguageManager(requireContext())
        updateBookingsText()
    }

    private fun updateBookingsText() {
        val bookingsText = if (languageManager.isBengali()) {
            """
            📅 আমার বুকিং
            
            🔍 এখনও কোন বুকিং পাওয়া যায়নি।
            
            আপনার প্রথম বুকিং করতে:
            1. মেনু থেকে "টেস্ট বুক করুন" এ যান
            2. আপনার প্রয়োজনীয় পরীক্ষাগুলি নির্বাচন করুন
            3. পছন্দের তারিখ এবং সময় বেছে নিন
            4. পেমেন্ট সম্পূর্ণ করুন (যদি অগ্রিম প্রয়োজন হয়)
            5. ইমেইলের মাধ্যমে নিশ্চিতকরণ পান
            
            📊 বুকিং স্ট্যাটাস বিকল্পসমূহ:
            • পেন্ডিং - নিশ্চিতকরণের অপেক্ষায়
            • নিশ্চিত - অ্যাপয়েন্টমেন্ট নির্ধারিত
            • প্রগ্রেসে - নমুনা সংগ্রহ
            • সম্পন্ন - রিপোর্ট প্রস্তুত
            • বাতিল - বুকিং বাতিল
            
            📧 ইমেইল আপডেট:
            সমস্ত বুকিং আপডেট আপনার নিবন্ধিত ইমেইল ঠিকানা এবং contact.anubhavlife@gmail.com এ পাঠানো হবে
            
            🏥 অনুভব লাইফ কেয়ার
            📞 +৯১-৯২৩০৭৫৫৮৭৫ | +৯১-৯২৩০৭৫৫৮৭০
            📱 হোয়াটসঅ্যাপ: +৯১-৯২৩০৭৫৫৮৭৬
            """.trimIndent()
        } else {
            """
            📅 My Bookings
            
            🔍 No bookings found yet.
            
            To make your first booking:
            1. Go to "Book Test" from menu
            2. Select your required tests
            3. Choose preferred date and time
            4. Complete payment (if advance required)
            5. Receive confirmation via email
            
            📊 Booking Status Options:
            • Pending - Awaiting confirmation
            • Confirmed - Appointment scheduled  
            • In Progress - Sample collection
            • Completed - Report ready
            • Cancelled - Booking cancelled
            
            📧 Email Updates:
            All booking updates will be sent to your registered email address and also to contact.anubhavlife@gmail.com
            
            🏥 AKTIV Admin
            📞 +91-9230755875 | +91-9230755870
            📱 WhatsApp: +91-9230755876
            """.trimIndent()
        }

        binding.textSlideshow.text = bookingsText
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}