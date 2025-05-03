package com.rpgmaker.game

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.MutableLiveData
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.google.android.play.core.review.ReviewManagerFactory
import com.limurse.iap.BillingClientConnectionListener
import com.limurse.iap.DataWrappers
import com.limurse.iap.IapConnector
import com.limurse.iap.PurchaseServiceListener
import com.limurse.iap.SubscriptionServiceListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Date

class MainActivity : AppCompatActivity() {
    private lateinit var rpgwebview: WebView
    private val writeExternalStorageRequest = 1

    private lateinit var adView: AdView
    private var mInterstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false
    private var loadTime: Long = 0

    private lateinit var iapConnector: IapConnector
    val isBillingClientConnected: MutableLiveData<Boolean> = MutableLiveData()

    //============================
    // 產品購買資訊
    // Product purchase information
    //============================
    val nonConsumablesList = listOf("mvtest001", "mvtest002")
    val consumablesList = listOf("mvtest003")
    val subsList = listOf("mvtest004")
    //============================

    private var mainact = this@MainActivity

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        setContentView(R.layout.activity_main)

        requestStoragePermission()

        adView = findViewById(R.id.adView)
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars())
        } else {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
        isBillingClientConnected.value = false

        rpgwebview = findViewById(R.id.webView)
        setWebView()
        adView.pause()
        adView.visibility = View.GONE
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
        loadInterstitialAd()
        loadRewardedAd()
        loadRewardedInterstitialAd()
        loadAppOpenAd()
    }

    @Suppress("DEPRECATION")
    private fun isNetworkAvailable(context: Context): Boolean {
        var result = false
        val cm = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cm?.run {
                cm.getNetworkCapabilities(cm.activeNetwork)?.run {
                    result = when {
                        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                        hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                        else -> false
                    }
                }
            }
        } else {
            cm?.run {
                cm.activeNetworkInfo?.run {
                    if (type == ConnectivityManager.TYPE_WIFI) {
                        result = true
                    } else if (type == ConnectivityManager.TYPE_MOBILE) {
                        result = true
                    }
                }
            }
        }
        return result
    }

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            mainact,
            getString(R.string.interstitial_ad_unit_id),
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    mInterstitialAd = null
                }

                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    mInterstitialAd = interstitialAd
                }
            })
        mInterstitialAd?.fullScreenContentCallback =
            object : FullScreenContentCallback() {
                override fun onAdClicked() {
                }

                override fun onAdDismissedFullScreenContent() {
                    // Called when ad is dismissed.
                    mInterstitialAd = null
                }

                override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                    // Called when ad fails to show.
                    mInterstitialAd = null
                }

                override fun onAdImpression() {
                    // Called when an impression is recorded for an ad.
                }

                override fun onAdShowedFullScreenContent() {
                    // Called when ad is shown.
                }
            }
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            mainact,
            getString(R.string.rewarded_ad_unit_id),
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    rewardedAd = null
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    val options = ServerSideVerificationOptions.Builder()
                        .setCustomData("SAMPLE_CUSTOM_DATA_STRING")
                        .build()
                    rewardedAd!!.setServerSideVerificationOptions(options)
                }
            })
        rewardedAd?.fullScreenContentCallback =
            object : FullScreenContentCallback() {
                override fun onAdClicked() {
                    // Called when a click is recorded for an ad.
                }

                override fun onAdDismissedFullScreenContent() {
                    // Called when ad is dismissed.
                    // Set the ad reference to null so you don't show the ad a second time.
                    rewardedAd = null
                }

                override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                    // Called when ad fails to show.
                    rewardedAd = null
                }

                override fun onAdImpression() {
                    // Called when an impression is recorded for an ad.
                }

                override fun onAdShowedFullScreenContent() {
                    // Called when ad is shown.
                }
            }
    }

    private fun loadRewardedInterstitialAd() {
        RewardedInterstitialAd.load(mainact, getString(R.string.rewarded_interstitial_ad_unit_id),
            AdRequest.Builder().build(), object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    rewardedInterstitialAd = ad
                    rewardedInterstitialAd?.fullScreenContentCallback =
                        object : FullScreenContentCallback() {
                            override fun onAdClicked() {
                                // Called when a click is recorded for an ad.
                            }

                            override fun onAdDismissedFullScreenContent() {
                                // Called when ad is dismissed.
                                // Set the ad reference to null so you don't show the ad a second time.
                                rewardedInterstitialAd = null
                            }

                            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                // Called when ad fails to show.
                                rewardedInterstitialAd = null
                            }

                            override fun onAdImpression() {
                                // Called when an impression is recorded for an ad.
                            }

                            override fun onAdShowedFullScreenContent() {
                                // Called when ad is shown.
                            }
                        }

                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    rewardedInterstitialAd = null
                }
            })
    }

    @Suppress("SameParameterValue")
    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        val dateDifference: Long = Date().time - loadTime
        val numMilliSecondsPerHour: Long = 3600000
        return dateDifference < numMilliSecondsPerHour * numHours
    }

    private fun isAdAvailable(): Boolean {
        return appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)
    }

    /** Request an ad. */
    private fun loadAppOpenAd() {
        // Do not load ad if there is an unused ad or one is already loading.
        if (isLoadingAd || isAdAvailable()) {
            return
        }
        isLoadingAd = true
        val request = AdRequest.Builder().build()
        AppOpenAd.load(
            mainact, getString(R.string.app_open_ad_unit_id), request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    // Called when an app open ad has loaded.
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTime = Date().time
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    // Called when an app open ad has failed to load.
                    isLoadingAd = false
                }
            })
    }

    @SuppressLint("SetJavaScriptEnabled")
    var setWebView = {
        val webSettings = rpgwebview.settings
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true
        webSettings.domStorageEnabled = true
        webSettings.mediaPlaybackRequiresUserGesture = false
        webSettings.useWideViewPort = true
        webSettings.databaseEnabled = true
        webSettings.loadWithOverviewMode = true
        webSettings.defaultTextEncodingName = "utf-8"
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        webSettings.loadsImagesAutomatically = true
        webSettings.javaScriptEnabled = true
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        if (getString(R.string.test_mode) != "true") {
            if (!verifyInstallerId(applicationContext)) {
                Toast.makeText(
                    applicationContext,
                    getString(R.string.is_gdd_msg),
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
        rpgwebview.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        val assetLoader = WebViewAssetLoader.Builder()
            .setDomain("example.com")
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        rpgwebview.webViewClient = LocalContentWebViewClient(assetLoader)
        rpgwebview.webChromeClient = WebChromeClient()
        val inAppHtmlUrl = "https://example.com/assets/index.html"
        rpgwebview.loadUrl(inAppHtmlUrl)
        rpgwebview.addJavascriptInterface(AndroidInterface(this), "AndroidInterface")
    }

    inner class LocalContentWebViewClient(private val assetLoader: WebViewAssetLoader) :
        WebViewClientCompat() {
        override fun shouldInterceptRequest(
            view: WebView, request: WebResourceRequest
        ): WebResourceResponse? {
            return assetLoader.shouldInterceptRequest(request.url)
        }
    }

    inner class AndroidInterface(private val activity: MainActivity) {
        @JavascriptInterface
        fun iapInit() {
            runOnUiThread {
                iapConnector = IapConnector(
                    context = activity,
                    nonConsumableKeys = nonConsumablesList,
                    consumableKeys = consumablesList,
                    subscriptionKeys = subsList,
                    key = getString(R.string.license_key),
                    enableLogging = true
                )
                iapConnector.addBillingClientConnectionListener(
                    object : BillingClientConnectionListener {
                        override fun onConnected(status: Boolean, billingResponseCode: Int) {
                            isBillingClientConnected.value = status
                        }
                    })
                iapConnector.addPurchaseListener(
                    object : PurchaseServiceListener {
                        override fun onPricesUpdated(iapKeyPrices: Map<String, List<DataWrappers.ProductDetails>>) {
                            // list of available products will be received here, so you can update UI with prices if needed
                            for ((key, value) in iapKeyPrices.entries) {
                                if (key.equals(
                                        "mvtest001",
                                        ignoreCase = true
                                    )
                                ) {
                                    rpgwebview.evaluateJavascript(
                                        "javascript:BillingGetPurchase('mvtest001','" + value[0].price.toString() + "')"
                                    ) {
                                    }
                                } else if (key.equals(
                                        "mvtest002",
                                        ignoreCase = true
                                    )
                                ) {
                                    rpgwebview.evaluateJavascript(
                                        "javascript:BillingGetPurchase('mvtest002','" + value[0].price.toString() + "')"
                                    ) {
                                    }
                                } else if (key.equals(
                                        "mvtest003",
                                        ignoreCase = true
                                    )
                                ) {
                                    rpgwebview.evaluateJavascript(
                                        "javascript:BillingGetConPurchase('mvtest003','" + value[0].price.toString() + "')"
                                    ) {
                                    }
                                }
                            }
                        }

                        override fun onProductPurchased(purchaseInfo: DataWrappers.PurchaseInfo) {
                            when (purchaseInfo.sku) {
                                "mvtest001" -> {
                                    rpgwebview.evaluateJavascript(
                                        "javascript:BillingSuccess('mvtest001')"
                                    ) {
                                    }
                                }

                                "mvtest002" -> {
                                    rpgwebview.evaluateJavascript(
                                        "javascript:BillingSuccess('mvtest002')"
                                    ) {
                                    }
                                }

                                "mvtest003" -> {
                                    rpgwebview.evaluateJavascript(
                                        "javascript:BillingSuccess('mvtest003')"
                                    ) {
                                    }
                                }
                            }
                        }

                        override fun onProductRestored(purchaseInfo: DataWrappers.PurchaseInfo) {
                            // will be triggered fetching owned products using IapConnector;
                            when (purchaseInfo.sku) {
                                "mvtest001" -> {
                                    rpgwebview.evaluateJavascript(
                                        "javascript:BillingSuccess('mvtest001')"
                                    ) {
                                    }
                                }

                                "mvtest002" -> {
                                    rpgwebview.evaluateJavascript(
                                        "javascript:BillingSuccess('mvtest002')"
                                    ) {
                                    }
                                }
                            }
                        }

                        override fun onPurchaseFailed(
                            purchaseInfo: DataWrappers.PurchaseInfo?,
                            billingResponseCode: Int?
                        ) {
                            // will be triggered whenever a product purchase is failed
                        }
                    })

                iapConnector.addSubscriptionListener(
                    object : SubscriptionServiceListener {
                        override fun onSubscriptionRestored(purchaseInfo: DataWrappers.PurchaseInfo) {
                            // will be triggered upon fetching owned subscription upon initialization
                            when (purchaseInfo.sku) {
                                "mvtest004" -> {
                                    rpgwebview.evaluateJavascript(
                                        "javascript:BillingSubscription('mvtest004','true')"
                                    ) {
                                    }
                                }
                            }
                        }

                        override fun onSubscriptionPurchased(purchaseInfo: DataWrappers.PurchaseInfo) {
                            // will be triggered whenever subscription succeeded
                            when (purchaseInfo.sku) {
                                "mvtest004" -> {
                                    rpgwebview.evaluateJavascript(
                                        "javascript:BillingSubscription('mvtest004','true')"
                                    ) {
                                    }
                                }
                            }
                        }

                        override fun onPricesUpdated(iapKeyPrices: Map<String, List<DataWrappers.ProductDetails>>) {
                            // list of available products will be received here, so you can update UI with prices if needed
                            for ((key, value) in iapKeyPrices.entries) {
                                if (key.equals(
                                        "mvtest004",
                                        ignoreCase = true
                                    )
                                ) {
                                    rpgwebview.evaluateJavascript(
                                        "javascript:BillingGetSubscribe('mvtest004','" + value[0].price.toString() + "')"
                                    ) {
                                    }
                                }
                            }
                        }

                        override fun onPurchaseFailed(
                            purchaseInfo: DataWrappers.PurchaseInfo?,
                            billingResponseCode: Int?
                        ) {
                            // will be triggered whenever subscription purchase is failed
                        }
                    })
                rpgwebview.evaluateJavascript(
                    "javascript:PurchaseStateEvent()"
                ) {
                }
            }
        }

        @JavascriptInterface
        fun iapPurchase(productId: String) {
            runOnUiThread {
                if (isNetworkAvailable(mainact)) {
                    iapConnector.purchase(activity, productId)
                } else {
                    Toast.makeText(
                        activity,
                        getString(R.string.not_internet_msg),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        @JavascriptInterface
        fun iapConsumePurchase(productId: String) {
            runOnUiThread {
                if (isNetworkAvailable(mainact)) {
                    iapConnector.purchase(activity, productId)
                } else {
                    Toast.makeText(
                        activity,
                        getString(R.string.not_internet_msg),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        @JavascriptInterface
        fun iapSubscribe(productId: String) {
            runOnUiThread {
                if (isNetworkAvailable(mainact)) {
                    iapConnector.subscribe(activity, productId)
                } else {
                    Toast.makeText(
                        activity,
                        getString(R.string.not_internet_msg),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        @JavascriptInterface
        fun iapUnSubscribe(productId: String) {
            runOnUiThread {
                if (isNetworkAvailable(mainact)) {
                    iapConnector.unsubscribe(activity, productId)
                } else {
                    Toast.makeText(
                        activity,
                        getString(R.string.not_internet_msg),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        @JavascriptInterface
        fun inAppLink(urls: String) {
            runOnUiThread {
                try {
                    val i = Intent("android.intent.action.MAIN")
                    i.component =
                        ComponentName.unflattenFromString("com.android.chrome/com.android.chrome.Main")
                    i.addCategory("android.intent.category.LAUNCHER")
                    i.data = Uri.parse(urls)
                    startActivity(i)
                } catch (e: ActivityNotFoundException) {
                    val i = Intent(Intent.ACTION_VIEW, Uri.parse(urls))
                    startActivity(i)
                }
            }
        }

        @JavascriptInterface
        fun inAppExit() {
            runOnUiThread { finish() }
        }

        @JavascriptInterface
        fun inAppReview() {
            runOnUiThread {
                val reviewManager = ReviewManagerFactory.create(this@MainActivity)
                val requestReviewFlow = reviewManager.requestReviewFlow()
                requestReviewFlow.addOnCompleteListener { request ->
                    if (request.isSuccessful) {
                        val reviewInfo = request.result
                        val flow = reviewManager.launchReviewFlow(this@MainActivity, reviewInfo)
                        flow.addOnCompleteListener {
                        }
                    }
                }
            }
        }

        @JavascriptInterface
        fun inAppEditText(title: String, msg: String, varlet: String) {
            runOnUiThread { activity.showEditDialog(title, msg, varlet) }
        }

        @JavascriptInterface
        fun inAppScreenShotShare(base64Data: String) {
            activity.handleBase64Data(base64Data)
        }

        @JavascriptInterface
        fun inAppShowToast(message: String, time: String) {
            if (time == "LONG") {
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun admobRewardedAd(event: String) {
            runOnUiThread {
                if (isNetworkAvailable(mainact)) {
                    rewardedAd?.let { ad ->
                        ad.show(mainact) {
                            rpgwebview.evaluateJavascript(
                                "javascript:EarnedReward('$event')"
                            ) {
                            }
                        }
                    } ?: run {
                    }
                } else {
                    Toast.makeText(
                        activity,
                        getString(R.string.not_internet_msg),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        @JavascriptInterface
        fun admobRewardedInterstitialAd(event: String) {
            runOnUiThread {
                if (isNetworkAvailable(mainact)) {
                    rewardedInterstitialAd?.show(mainact) {
                        rpgwebview.evaluateJavascript(
                            "javascript:EarnedReward('$event')"
                        ) {
                        }
                    }
                } else {
                    Toast.makeText(
                        activity,
                        getString(R.string.not_internet_msg),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        @JavascriptInterface
        fun admobAppOpenAd() {
            runOnUiThread {
                if (isNetworkAvailable(mainact)) {
                    if (!isShowingAd) {
                        // If the app open ad is not available yet, invoke the callback then load the ad.
                        if (!isAdAvailable()) {
                            loadAppOpenAd()
                        }
                        appOpenAd?.fullScreenContentCallback =
                            object : FullScreenContentCallback() {
                                override fun onAdDismissedFullScreenContent() {
                                    // Called when full screen content is dismissed.
                                    // Set the reference to null so isAdAvailable() returns false.
                                    appOpenAd = null
                                    isShowingAd = false

                                    loadAppOpenAd()
                                }

                                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                    // Called when fullscreen content failed to show.
                                    // Set the reference to null so isAdAvailable() returns false.
                                    appOpenAd = null
                                    isShowingAd = false

                                    loadAppOpenAd()
                                }

                                override fun onAdShowedFullScreenContent() {
                                    // Called when fullscreen content is shown.
                                }
                            }
                        isShowingAd = true
                        appOpenAd?.show(activity)
                    }
                } else {
                    Toast.makeText(
                        activity,
                        getString(R.string.not_internet_msg),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    }

    private fun showEditDialog(title: String?, msg: String?, ld: String?) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setCancelable(false)
        val input = EditText(this)
        input.hint = msg
        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)
        rpgwebview.onPause()
        builder.setPositiveButton(getString(R.string.are_you_Ok)) { _, _ ->
            val edittext = input.text.toString()
            if (edittext != "") {
                rpgwebview.evaluateJavascript(
                    "javascript:Showeditdialog('$edittext','$ld')"
                ) {
                }
            }
            rpgwebview.onResume()
        }
        builder.setNegativeButton(getString(R.string.are_you_Cancel)) { dialog, _ ->
            rpgwebview.onResume()
            dialog.cancel()
        }
        builder.show()
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    writeExternalStorageRequest
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            writeExternalStorageRequest -> {
                if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    // Permission was granted
                    // You can now perform the action that requires this permission
                } else {
                    // Permission denied
                    Toast.makeText(this, getString(R.string.share_String01), Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    private fun handleBase64Data(base64Data: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val bitmap = getBitmapFromBase64(base64Data)
            saveAndShareBitmap(bitmap)
        }
    }

    private fun getBitmapFromBase64(base64Data: String): Bitmap {
        val decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    }

    private fun saveAndShareBitmap(bitmap: Bitmap) {
        if (isExternalStorageWritable()) {
            // Options
            val mimeType = "image/png"
            val fileName = "screenshot"
            val quality = 100
            val fileNameWithExtension = "$fileName.png"
            var outputStream: OutputStream?
            // Scoped storage API for Android >= Q
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mainact.contentResolver?.also { resolver ->
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileNameWithExtension)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                    }
                    val imageUri: Uri? =
                        resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    outputStream = imageUri?.let { resolver.openOutputStream(it) }
                    outputStream?.use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, quality, it)
                    }
                    val shareIntent = Intent(Intent.ACTION_SEND)
                    shareIntent.type = "image/*"
                    shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri)
                    startActivity(
                        Intent.createChooser(
                            shareIntent,
                            getString(R.string.share_String03)
                        )
                    )
                }
            } else {
                // Deprecated API for Android < Q
                val imagesDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val image = File(imagesDir, fileNameWithExtension)
                outputStream = FileOutputStream(image)
                outputStream?.use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, quality, it)
                }
                val shareIntent = Intent(Intent.ACTION_SEND)
                shareIntent.type = "image/*"
                shareIntent.putExtra(Intent.EXTRA_STREAM, getImageUri(image))
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_String03)))
            }
        } else {
            Toast.makeText(this, getString(R.string.share_String02), Toast.LENGTH_SHORT).show()
        }
    }

    private fun getImageUri(imageFile: File): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Use FileProvider on Android 7 and above
            FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                imageFile
            )
        } else {
            // Use Uri.fromFile on Android 6 and below
            Uri.fromFile(imageFile)
        }
    }

    private fun isExternalStorageWritable(): Boolean {
        val state = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == state
    }

    @Suppress("DEPRECATION")
    private fun verifyInstallerId(context: Context): Boolean {
        // A list with valid installers package name
        val validInstallers: List<String> =
            ArrayList(listOf("com.android.vending", "com.google.android.feedback"))
        // The package name of the app that has installed your app
        val installer: String? = context.packageManager.getInstallerPackageName(context.packageName)
        // true if your app has been downloaded from Play Store
        return installer != null && validInstallers.contains(installer)
    }

    override fun onDestroy() {
        rpgwebview.loadDataWithBaseURL(null, "", "text/html", "utf-8", null)
        rpgwebview.clearHistory()
        rpgwebview.destroy()
        adView.destroy()
        super.onDestroy()
    }

    override fun onPause() {
        rpgwebview.onPause()
        adView.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        rpgwebview.onResume()
        adView.resume()
        loadInterstitialAd()
        loadRewardedAd()
        loadRewardedInterstitialAd()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            rpgwebview.onPause()
            val builder = AlertDialog.Builder(this)
            builder.setTitle(getString(R.string.are_you_exit_title))
            builder.setCancelable(false)
            builder.setMessage(getString(R.string.are_you_exit_msg))
                .setPositiveButton(
                    getString(R.string.are_you_exit_yes)
                ) { _, _ ->
                    finish()
                }
                .setNegativeButton(
                    getString(R.string.are_you_exit_no)
                ) { dialog, _ ->
                    rpgwebview.onResume()
                    dialog.cancel()
                }
            // Create the AlertDialog object and return it
            val alert = builder.create()
            alert.show()
            return false
        }
        return super.onKeyDown(keyCode, event)
    }
}
