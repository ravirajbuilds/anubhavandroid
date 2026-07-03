package com.example.anubhavlifecare.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.anubhavlifecare.R
import com.example.anubhavlifecare.utils.CustomerSessionManager
import com.example.anubhavlifecare.utils.LanguageManager
import com.example.anubhavlifecare.utils.localized
import com.google.android.material.button.MaterialButton

class SettingsFragment : Fragment() {

    private lateinit var languageManager: LanguageManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        languageManager = LanguageManager(requireContext())
        applyLocalizedLabels(view)
        bindAccountInfo(view)
        setupLanguageRadio(view)
        setupSupportButton(view)
    }

    private fun applyLocalizedLabels(view: View) {
        view.findViewById<TextView>(R.id.tvSettingsLanguageTitle).text =
            requireContext().localized(R.string.settings_language_title)
        view.findViewById<TextView>(R.id.tvSettingsLanguageHint).text =
            requireContext().localized(R.string.settings_language_hint)
        view.findViewById<TextView>(R.id.tvSettingsAccountTitle).text =
            requireContext().localized(R.string.settings_account_title)
        view.findViewById<MaterialButton>(R.id.btnContactSupport).text =
            requireContext().localized(R.string.whatsapp_support)
        view.findViewById<RadioButton>(R.id.radioEnglish).text =
            requireContext().localized(R.string.language_english_full)
        view.findViewById<RadioButton>(R.id.radioBengali).text =
            requireContext().localized(R.string.language_bengali_full)
    }

    private fun bindAccountInfo(view: View) {
        val ctx = requireContext()
        val name = CustomerSessionManager.getName(ctx)
        val email = CustomerSessionManager.getEmail(ctx)
        val phone = CustomerSessionManager.getPhone(ctx)

        view.findViewById<TextView>(R.id.tvAccountName).text =
            name ?: ctx.localized(R.string.name)
        view.findViewById<TextView>(R.id.tvAccountEmail).text =
            email ?: "—"
        view.findViewById<TextView>(R.id.tvAccountPhone).text =
            if (!phone.isNullOrBlank()) phone else "—"

        val warning = view.findViewById<TextView>(R.id.tvPhoneWarning)
        if (phone.isNullOrBlank()) {
            warning.visibility = View.VISIBLE
            warning.text = ctx.localized(R.string.complete_profile_banner)
        } else {
            warning.visibility = View.GONE
        }
    }

    private fun setupLanguageRadio(view: View) {
        val radioGroup = view.findViewById<RadioGroup>(R.id.radioLanguage)
        val radioEnglish = view.findViewById<RadioButton>(R.id.radioEnglish)
        val radioBengali = view.findViewById<RadioButton>(R.id.radioBengali)

        if (languageManager.isBengali()) {
            radioBengali.isChecked = true
        } else {
            radioEnglish.isChecked = true
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val newLang = when (checkedId) {
                R.id.radioBengali -> LanguageManager.LANGUAGE_BENGALI
                else -> LanguageManager.LANGUAGE_ENGLISH
            }
            if (languageManager.getCurrentLanguage() != newLang) {
                languageManager.setLanguage(newLang)
                Toast.makeText(requireContext(), localized(R.string.settings_saved), Toast.LENGTH_SHORT).show()
                requireActivity().recreate()
            }
        }
    }

    private fun setupSupportButton(view: View) {
        view.findViewById<MaterialButton>(R.id.btnContactSupport).setOnClickListener {
            val msg = requireContext().localized(R.string.whatsapp_booking_message)
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://wa.me/919230755876?text=${Uri.encode(msg)}"),
                ),
            )
        }
    }
}
