package com.anubhav.app.ui.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Filter
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.anubhav.app.BuildConfig
import com.anubhav.app.R
import com.anubhav.app.utils.LocationHelper
import com.anubhav.app.utils.localized
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import java.util.Locale
import kotlin.math.abs

/**
 * Full-screen "drop a pin on the map" picker built on Leaflet + OpenStreetMap tiles,
 * both bundled in `assets/map/` — no Play Services, no map SDK key, no tracking.
 *
 * The pin is a fixed overlay at the centre of the map: the map moves under it, which is
 * the interaction every delivery/ride app has already taught users. Whatever sits under
 * the pin when "Confirm" is tapped is the home-collection address.
 *
 * Show it with [newInstance] and read the answer with a fragment result on [REQUEST_KEY].
 */
class MapPickerFragment : DialogFragment() {

    companion object {
        const val REQUEST_KEY = "map_picker_request"
        const val RESULT_LATITUDE = "latitude"
        const val RESULT_LONGITUDE = "longitude"
        const val RESULT_ADDRESS = "address"

        private const val ARG_LATITUDE = "arg_latitude"
        private const val ARG_LONGITUDE = "arg_longitude"
        private const val ARG_ADDRESS = "arg_address"

        private const val STATE_LATITUDE = "state_latitude"
        private const val STATE_LONGITUDE = "state_longitude"
        private const val STATE_ZOOM = "state_zoom"
        private const val STATE_ADDRESS = "state_address"

        /** Anubhav Life Care, Kolkata — where the pin starts when nothing better is known. */
        private const val FALLBACK_LATITUDE = 22.5726
        private const val FALLBACK_LONGITUDE = 88.3639
        private const val DEFAULT_ZOOM = 16.0
        private const val LOCATED_ZOOM = 17.0
        private const val SEARCH_DEBOUNCE_MS = 450L

        fun newInstance(
            latitude: Double? = null,
            longitude: Double? = null,
            address: String? = null,
        ): MapPickerFragment = MapPickerFragment().apply {
            arguments = Bundle().apply {
                if (LocationHelper.isValidCoordinate(latitude, longitude)) {
                    putDouble(ARG_LATITUDE, latitude!!)
                    putDouble(ARG_LONGITUDE, longitude!!)
                }
                if (!address.isNullOrBlank()) putString(ARG_ADDRESS, address)
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var inflationFailed = false
    private var webView: WebView? = null
    private var mapReady = false
    private var pageInitialised = false
    private val pendingScripts = mutableListOf<String>()

    private var pinLatitude: Double? = null
    private var pinLongitude: Double? = null
    private var pinZoom: Double = DEFAULT_ZOOM
    private var pinAddress: String? = null

    /**
     * A label the user already chose (a search hit, or the address the caller passed in)
     * that is waiting for the map to settle on it. Without this the programmatic move
     * that follows would reverse-geocode the same point and overwrite the chosen name.
     */
    private var pendingLabel: String? = null
    private var pendingLabelLatitude = 0.0
    private var pendingLabelLongitude = 0.0

    /** Guards against a slow reverse-geocode landing after the pin has moved on. */
    private var geocodeSequence = 0
    private var searchRunnable: Runnable? = null
    private var searchResults: List<LocationHelper.Place> = emptyList()
    private var locating = false

    // Asking here matters: the picker is often the first screen where the user wants
    // location, and a toast saying "permission needed" with no way to grant it is a
    // dead end.
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (!isAdded) return@registerForActivityResult
        if (grants.values.any { it }) {
            locateMe()
        } else {
            Toast.makeText(
                requireContext(),
                localized(R.string.location_permission_needed),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_AnubhavLifeCare_NoActionBar)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = runCatching {
        inflater.inflate(R.layout.fragment_map_picker, container, false)
    }.getOrElse {
        // Inflating a WebView throws while the system WebView package is being updated.
        // Fail closed rather than crashing the booking flow.
        inflationFailed = true
        FrameLayout(inflater.context)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (inflationFailed) {
            Toast.makeText(requireContext(), localized(R.string.map_unavailable), Toast.LENGTH_LONG).show()
            dismissAllowingStateLoss()
            return
        }

        val args = arguments
        pinLatitude = savedInstanceState?.takeIf { it.containsKey(STATE_LATITUDE) }?.getDouble(STATE_LATITUDE)
            ?: args?.takeIf { it.containsKey(ARG_LATITUDE) }?.getDouble(ARG_LATITUDE)
        pinLongitude = savedInstanceState?.takeIf { it.containsKey(STATE_LONGITUDE) }?.getDouble(STATE_LONGITUDE)
            ?: args?.takeIf { it.containsKey(ARG_LONGITUDE) }?.getDouble(ARG_LONGITUDE)
        pinZoom = savedInstanceState?.getDouble(STATE_ZOOM, DEFAULT_ZOOM) ?: DEFAULT_ZOOM
        pinAddress = savedInstanceState?.getString(STATE_ADDRESS) ?: args?.getString(ARG_ADDRESS)

        val title = view.findViewById<TextView>(R.id.tvMapTitle)
        val back = view.findViewById<MaterialButton>(R.id.btnMapBack)
        val search = view.findViewById<AutoCompleteTextView>(R.id.etMapSearch)
        val locate = view.findViewById<FloatingActionButton>(R.id.fabLocateMe)
        val confirm = view.findViewById<MaterialButton>(R.id.btnConfirmLocation)
        val web = view.findViewById<WebView>(R.id.mapWebView)
        webView = web

        title.text = localized(R.string.map_picker_title)
        confirm.text = localized(R.string.map_confirm)
        view.findViewById<TextView>(R.id.tvPickedAddress).text = localized(R.string.map_move_to_pick)

        back.setOnClickListener { dismissAllowingStateLoss() }
        locate.setOnClickListener { locateMe() }
        confirm.setOnClickListener { confirmSelection() }

        setUpSearch(search)
        setUpWebView(web)
        renderSelection()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            // Deprecated since API 30 but still the only way to make a dialog window
            // resize for the keyboard on every version this app supports (minSdk 24).
            @Suppress("DEPRECATION")
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pinLatitude?.let { outState.putDouble(STATE_LATITUDE, it) }
        pinLongitude?.let { outState.putDouble(STATE_LONGITUDE, it) }
        outState.putDouble(STATE_ZOOM, pinZoom)
        pinAddress?.let { outState.putString(STATE_ADDRESS, it) }
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onPause() {
        webView?.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        searchRunnable?.let { mainHandler.removeCallbacks(it) }
        searchRunnable = null
        mainHandler.removeCallbacksAndMessages(null)
        webView?.let { web ->
            // Detach before destroy(), otherwise the WebView leaks the whole view tree.
            web.stopLoading()
            web.removeJavascriptInterface("AndroidMap")
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
        }
        webView = null
        mapReady = false
        pageInitialised = false
        locating = false
        pendingScripts.clear()
        super.onDestroyView()
    }

    // ------------------------------------------------------------------ map

    @SuppressLint("SetJavaScriptEnabled")
    private fun setUpWebView(web: WebView) {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // The page is a local asset; it must never reach the file system or the
            // network for anything except OpenStreetMap tiles.
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            builtInZoomControls = false
            displayZoomControls = false
            // Keep the map chrome legible at any system font scale without breaking layout.
            textZoom = 100
            userAgentString = "$userAgentString AnubhavLifeCare/${BuildConfig.VERSION_NAME}"
        }
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false
        web.setBackgroundColor(0)
        web.addJavascriptInterface(MapBridge(), "AndroidMap")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                // The only link on the page is the OSM copyright notice — hand it to the
                // browser and never navigate the picker itself away from the local asset.
                val url = request?.url ?: return true
                if (url.scheme == "https" || url.scheme == "http") {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, url)) }
                }
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!isAdded) return
                mapReady = true
                this@MapPickerFragment.view?.findViewById<ProgressBar>(R.id.mapProgress)
                    ?.visibility = View.GONE
                // Anything queued before the page loaded was queued first, so it runs first.
                pendingScripts.toList().forEach { web.evaluateJavascript(it, null) }
                pendingScripts.clear()
                val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
                callJs("window.mapApi.setDark(${if (night) "true" else "false"})")
                callJs("window.mapApi.setHint(${jsString(localized(R.string.map_drag_hint))})")
                // onPageFinished fires again if the WebView ever reloads (an OOM kill of
                // the renderer, say). Re-centring then would yank the pin off whatever
                // the user had already chosen.
                if (!pageInitialised) {
                    pageInitialised = true
                    centreOnInitialPin()
                } else {
                    restoreCurrentPin()
                }
                // If Leaflet failed to evaluate (corrupt asset, WebView out of memory) the
                // page silently stays blank; say so instead of showing an empty grey box.
                web.evaluateJavascript("typeof window.mapApi") { result ->
                    if (!isAdded || result?.contains("object") == true) return@evaluateJavascript
                    Toast.makeText(
                        requireContext(),
                        localized(R.string.map_unavailable),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
        web.loadUrl("file:///android_asset/map/picker.html")
    }

    /** Opens on the caller's pin, else the last GPS fix, else the clinic's city. */
    private fun centreOnInitialPin() {
        val lat = pinLatitude
        val lng = pinLongitude
        if (LocationHelper.isValidCoordinate(lat, lng)) {
            // The caller's own address wins over anything the geocoder would say about
            // the same point — the user typed it, or picked it here last time.
            pinAddress?.takeIf { it.isNotBlank() }?.let { setPendingLabel(it, lat!!, lng!!) }
            moveMap(lat!!, lng!!, pinZoom, animate = false)
            return
        }
        moveMap(FALLBACK_LATITUDE, FALLBACK_LONGITUDE, 12.0, animate = false)
        // Only offer to locate when permission is already granted: a permission dialog
        // the user never asked for, the instant the map opens, is not the way to ask.
        if (LocationHelper.hasLocationPermission(requireContext())) locateMe()
    }

    /** Puts the pin back where it was after an unexpected reload of the page. */
    private fun restoreCurrentPin() {
        val lat = pinLatitude
        val lng = pinLongitude
        if (!LocationHelper.isValidCoordinate(lat, lng)) return
        pinAddress?.takeIf { it.isNotBlank() }?.let { setPendingLabel(it, lat!!, lng!!) }
        moveMap(lat!!, lng!!, pinZoom, animate = false)
    }

    private fun setPendingLabel(label: String, latitude: Double, longitude: Double) {
        pendingLabel = label
        pendingLabelLatitude = latitude
        pendingLabelLongitude = longitude
    }

    /**
     * Consumes [pendingLabel] if the map really did settle on the point it was set for.
     * One-shot: a label that does not match is dropped, not carried to the next move.
     */
    private fun takePendingLabel(latitude: Double, longitude: Double): String? {
        val label = pendingLabel ?: return null
        pendingLabel = null
        // ~11 m. The map settles on exactly the coordinates it was sent to, so anything
        // further out means the user has since moved the pin and the label is stale.
        val matches = abs(latitude - pendingLabelLatitude) < 1e-4 &&
            abs(longitude - pendingLabelLongitude) < 1e-4
        return label.takeIf { matches }
    }

    private fun moveMap(latitude: Double, longitude: Double, zoom: Double, animate: Boolean) {
        // A non-finite value would interpolate as the literal `NaN` and throw in JS,
        // leaving the map stuck on whatever it was showing with no error anywhere.
        if (!LocationHelper.isValidCoordinate(latitude, longitude)) return
        val safeZoom = if (zoom.isFinite()) zoom else DEFAULT_ZOOM
        callJs(
            "window.mapApi.setView($latitude, $longitude, $safeZoom, ${if (animate) "true" else "false"})",
        )
    }

    private fun callJs(script: String) {
        val web = webView ?: return
        if (mapReady) web.evaluateJavascript(script, null) else pendingScripts += script
    }

    /** JS -> Kotlin. Every method arrives on the WebView's JS thread, never the main one. */
    private inner class MapBridge {
        @JavascriptInterface
        fun onReady() = Unit

        @JavascriptInterface
        fun onMoveStart() {
            mainHandler.post {
                if (!isAdded) return@post
                // The address under the old pin is no longer true the moment the map moves,
                // and neither is any label the user chose for the old point.
                geocodeSequence += 1
                pinAddress = null
                pendingLabel = null
                renderSelection(searching = true)
            }
        }

        @JavascriptInterface
        fun onCentreChanged(latitude: Double, longitude: Double, zoom: Double, byUser: Boolean) {
            mainHandler.post {
                if (!isAdded) return@post
                if (!LocationHelper.isValidCoordinate(latitude, longitude)) return@post
                pinLatitude = latitude
                pinLongitude = longitude
                if (zoom.isFinite()) pinZoom = zoom
                val chosen = if (byUser) null else takePendingLabel(latitude, longitude)
                if (chosen != null) {
                    // Cancel any lookup still in flight so it cannot overwrite this.
                    geocodeSequence += 1
                    pinAddress = chosen
                    renderSelection()
                } else {
                    requestAddressFor(latitude, longitude)
                }
            }
        }

        @JavascriptInterface
        fun onTilesUnavailable() {
            mainHandler.post {
                if (!isAdded) return@post
                callJs("window.mapApi.setBanner(${jsString(localized(R.string.map_tiles_offline))})")
            }
        }

        @JavascriptInterface
        fun onTilesRecovered() {
            mainHandler.post {
                if (!isAdded) return@post
                callJs("window.mapApi.setBanner('')")
            }
        }
    }

    // -------------------------------------------------------------- geocoding

    private fun requestAddressFor(latitude: Double, longitude: Double) {
        geocodeSequence += 1
        val sequence = geocodeSequence
        renderSelection(searching = true)
        LocationHelper.reverseGeocode(requireContext(), latitude, longitude) { address ->
            if (!isAdded || sequence != geocodeSequence) return@reverseGeocode
            pinAddress = address
            renderSelection()
        }
    }

    private fun renderSelection(searching: Boolean = false) {
        val root = view ?: return
        val addressView = root.findViewById<TextView>(R.id.tvPickedAddress)
        val coordsView = root.findViewById<TextView>(R.id.tvPickedCoords)
        val confirm = root.findViewById<MaterialButton>(R.id.btnConfirmLocation)

        val lat = pinLatitude
        val lng = pinLongitude
        val hasPin = LocationHelper.isValidCoordinate(lat, lng)

        addressView.text = when {
            !hasPin -> localized(R.string.map_move_to_pick)
            !pinAddress.isNullOrBlank() -> pinAddress
            searching -> localized(R.string.map_reading_address)
            else -> localized(R.string.map_no_address)
        }
        coordsView.text = if (hasPin) LocationHelper.formatCoordinates(lat!!, lng!!) else ""
        // Coordinates alone are enough for the collector, so never block on the address.
        confirm.isEnabled = hasPin
        confirm.alpha = if (hasPin) 1f else 0.5f
    }

    // ----------------------------------------------------------------- search

    private fun setUpSearch(search: AutoCompleteTextView) {
        // The suggestions come from the geocoder, not from what was typed, so the
        // adapter's own prefix filter has to be disabled or it hides every result.
        val adapter = object : ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            mutableListOf<String>(),
        ) {
            private val passThrough = object : Filter() {
                override fun performFiltering(constraint: CharSequence?) = FilterResults()
                override fun publishResults(constraint: CharSequence?, results: FilterResults?) =
                    notifyDataSetChanged()
            }

            override fun getFilter(): Filter = passThrough
        }
        search.setAdapter(adapter)
        search.threshold = 1

        search.setOnItemClickListener { _, _, position, _ ->
            // Resolve by label, not by index: the two lists can drift apart while a
            // newer lookup is in flight.
            val label = adapter.getItem(position)
            val place = searchResults.firstOrNull { it.label == label } ?: return@setOnItemClickListener
            pinAddress = place.label
            // Hold the chosen name so the move that follows does not reverse-geocode the
            // point and replace "Anubhav Life Care, Sodepur" with a bare street line.
            setPendingLabel(place.label, place.latitude, place.longitude)
            moveMap(place.latitude, place.longitude, LOCATED_ZOOM, animate = true)
            search.dismissDropDown()
            hideKeyboard(search)
        }

        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch(search.text?.toString().orEmpty(), adapter, search)
                hideKeyboard(search)
                true
            } else {
                false
            }
        }

        search.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun afterTextChanged(s: Editable?) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchRunnable?.let { mainHandler.removeCallbacks(it) }
                    val query = s?.toString().orEmpty()
                    if (query.trim().length < 3) return
                    val runnable = Runnable { runSearch(query, adapter, search) }
                    searchRunnable = runnable
                    mainHandler.postDelayed(runnable, SEARCH_DEBOUNCE_MS)
                }
            },
        )
    }

    private fun runSearch(
        query: String,
        adapter: ArrayAdapter<String>,
        anchor: AutoCompleteTextView,
    ) {
        if (!isAdded) return
        if (query.trim().length < 3) return
        LocationHelper.searchPlaces(requireContext(), query) { places ->
            if (!isAdded) return@searchPlaces
            searchResults = places
            adapter.clear()
            adapter.addAll(places.map { it.label })
            adapter.notifyDataSetChanged()
            if (places.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    localized(R.string.map_search_no_results),
                    Toast.LENGTH_SHORT,
                ).show()
            } else if (anchor.hasWindowFocus()) {
                anchor.showDropDown()
            }
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    // ---------------------------------------------------------------- locate

    private fun locateMe() {
        if (locating) return
        val context = context ?: return
        if (!LocationHelper.hasLocationPermission(context)) {
            runCatching { locationPermissionLauncher.launch(LocationHelper.LOCATION_PERMISSIONS) }
            return
        }
        locating = true
        callJs("window.mapApi.setHint(${jsString(localized(R.string.locating))})")
        LocationHelper.fetchCurrentLocation(context) { outcome ->
            // Cleared before the isAdded guard: leaving it set would jam the button for
            // the rest of the fragment's life if the answer lands while it is detached.
            locating = false
            if (!isAdded) return@fetchCurrentLocation
            when (outcome) {
                is LocationHelper.FixOutcome.Found -> {
                    val fix = outcome.fix
                    callJs("window.mapApi.setHint('')")
                    if (!LocationHelper.isValidCoordinate(fix.latitude, fix.longitude)) {
                        Toast.makeText(
                            requireContext(),
                            localized(R.string.location_unavailable),
                            Toast.LENGTH_LONG,
                        ).show()
                        return@fetchCurrentLocation
                    }
                    val accuracy = fix.accuracyMeters?.toDouble()?.takeIf { it.isFinite() && it > 0 } ?: 0.0
                    callJs("window.mapApi.showMe(${fix.latitude}, ${fix.longitude}, $accuracy)")
                    moveMap(fix.latitude, fix.longitude, LOCATED_ZOOM, animate = true)
                }
                is LocationHelper.FixOutcome.Failed -> {
                    callJs("window.mapApi.setHint('')")
                    val messageRes = when (outcome.error) {
                        LocationHelper.Error.NO_PERMISSION -> R.string.location_permission_needed
                        LocationHelper.Error.LOCATION_DISABLED -> R.string.location_turn_on_gps
                        LocationHelper.Error.LOCATION_UNAVAILABLE -> R.string.location_unavailable
                    }
                    Toast.makeText(requireContext(), localized(messageRes), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --------------------------------------------------------------- confirm

    private fun confirmSelection() {
        val lat = pinLatitude
        val lng = pinLongitude
        if (!LocationHelper.isValidCoordinate(lat, lng)) {
            Toast.makeText(requireContext(), localized(R.string.map_move_to_pick), Toast.LENGTH_SHORT).show()
            return
        }
        val landmark = view?.findViewById<TextInputEditText>(R.id.etMapLandmark)
            ?.text?.toString()?.trim().orEmpty()
        val geocoded = pinAddress?.trim().orEmpty()
        val address = listOf(landmark, geocoded)
            .filter { it.isNotBlank() }
            // A landmark the geocoder already returned would read twice otherwise.
            .distinctBy { it.lowercase(Locale.getDefault()) }
            .joinToString(", ")
            .ifBlank { LocationHelper.formatCoordinates(lat!!, lng!!) }

        setFragmentResult(
            REQUEST_KEY,
            bundleOf(
                RESULT_LATITUDE to lat,
                RESULT_LONGITUDE to lng,
                RESULT_ADDRESS to address,
            ),
        )
        dismissAllowingStateLoss()
    }

    /** Quotes a value for safe interpolation into an evaluateJavascript() expression. */
    private fun jsString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("<", "\\u003C")
            .replace("\u2028", "\\u2028")
            .replace("\u2029", "\\u2029")
        return "'$escaped'"
    }
}
