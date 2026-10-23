package com.darktype.ktxsrt

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.darktype.ktxsrt.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefManager: PreferenceManager
    private var backPressedOnce = false
    private val handler = Handler(Looper.getMainLooper())

    // 사용자가 웹에서 '로그아웃' 버튼을 눌렀을 때 즉시 다시 자동로그인되는 무한루프 방지
    private var userExplicitlyLoggedOut = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefManager = PreferenceManager(this)

        initCookieManager()
        setupWebView()
        setupSwipeRefresh()
        setupListeners()
        setupBackNavigation()

        loadMainUrl()
    }

    private fun initCookieManager() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(binding.webView, true)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webSettings = binding.webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.allowFileAccess = true
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT

        binding.webView.addJavascriptInterface(
            WebAppInterface(this, prefManager),
            "AndroidBridge"
        )

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressBar.progress = newProgress
                } else {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.layoutError.visibility = View.GONE

                // 사용자가 로그아웃 URL로 이동했는지 감지
                if (url != null && url.contains("/logout")) {
                    userExplicitlyLoggedOut = true
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.swipeRefreshLayout.isRefreshing = false
                CookieManager.getInstance().flush()

                if (url != null) {
                    if (url.contains("/login")) {
                        injectAutoLoginScript(binding.webView)
                    } else if (!url.contains("/logout")) {
                        // 로그인 후 메인 등 정상 페이지로 진입하면 플래그 초기화
                        userExplicitlyLoggedOut = false
                    }
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    binding.layoutError.visibility = View.VISIBLE
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }

    private fun injectAutoLoginScript(view: WebView) {
        val shouldAutoSubmit = prefManager.isAutoLoginEnabled &&
                prefManager.hasCredentials() &&
                !userExplicitlyLoggedOut

        val js = """
        (function() {
            if (!window.AndroidBridge) return;

            // 1. 로그인 폼에 자동로그인 체크박스 UI 주입
            var pane = document.getElementById('pane-login');
            if (pane && !document.getElementById('android-autologin-wrap')) {
                var wrap = document.createElement('div');
                wrap.id = 'android-autologin-wrap';
                wrap.style.cssText = 'display:flex;align-items:center;gap:8px;margin-top:14px;font-size:13px;color:#a0a0a0;user-select:none;';
                
                var isChecked = ${prefManager.isAutoLoginEnabled};
                wrap.innerHTML = '<input type="checkbox" id="chk_autologin" style="width:16px;height:16px;accent-color:#1f6feb;cursor:pointer;" ' + (isChecked ? 'checked' : '') + '>' +
                                 '<label for="chk_autologin" style="margin:0;cursor:pointer;color:#c0c0c0;font-size:13px;">자동 로그인 및 계정 저장</label>';
                
                var btn = pane.querySelector('button.submit');
                if (btn) {
                    pane.insertBefore(wrap, btn);
                } else {
                    pane.appendChild(wrap);
                }

                // doLogin 함수 가로채기 (수동 로그인 시 계정 저장)
                var origDoLogin = window.doLogin;
                window.doLogin = async function() {
                    var email = document.getElementById('li_email').value;
                    var pw = document.getElementById('li_pw').value;
                    var chk = document.getElementById('chk_autologin');
                    var autoLogin = chk ? chk.checked : false;

                    if (email && pw && autoLogin) {
                        window.AndroidBridge.saveCredentials(email, pw, true);
                    } else if (!autoLogin) {
                        window.AndroidBridge.clearCredentials();
                    }
                    return origDoLogin.apply(this, arguments);
                };
            }

            // 2. 자동 로그인 실행 또는 폼 사전 채우기
            var credsStr = window.AndroidBridge.getSavedCredentials();
            if (credsStr) {
                try {
                    var creds = JSON.parse(credsStr);
                    var emailInput = document.getElementById('li_email');
                    var pwInput = document.getElementById('li_pw');

                    if (creds.email && emailInput) emailInput.value = creds.email;
                    if (creds.password && pwInput) pwInput.value = creds.password;

                    var autoSubmit = $shouldAutoSubmit;
                    var errEl = document.getElementById('err');
                    var hasError = errEl && errEl.textContent.trim().length > 0;

                    if (autoSubmit && !hasError && emailInput && pwInput && emailInput.value && pwInput.value) {
                        window.AndroidBridge.showToast('🚀 저장된 계정으로 자동 로그인합니다...');
                        setTimeout(function() {
                            window.doLogin();
                        }, 250);
                    }
                } catch(e) {
                    console.error('AutoLogin Error:', e);
                }
            }
        })();
        """.trimIndent()

        view.evaluateJavascript(js, null)
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.primary, R.color.accent)
        binding.swipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.card_dark)
        binding.swipeRefreshLayout.setOnRefreshListener {
            binding.webView.reload()
        }
    }

    private fun setupListeners() {
        binding.btnRetry.setOnClickListener {
            binding.layoutError.visibility = View.GONE
            loadMainUrl()
        }

        binding.fabSettings.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun showSettingsDialog() {
        val currentEmail = if (prefManager.email.isNotEmpty()) prefManager.email else "저장된 계정 없음"
        val autoStatus = if (prefManager.isAutoLoginEnabled) "사용 중" else "사용 안 함"

        val items = arrayOf(
            "자동 로그인: $autoStatus (토글)",
            "계정 정보: $currentEmail",
            "저장된 계정 삭제",
            "서버 주소 설정 (${prefManager.serverUrl})",
            "캐시 및 쿠키 전체 삭제",
            "화면 새로고침"
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        prefManager.isAutoLoginEnabled = !prefManager.isAutoLoginEnabled
                        val msg = if (prefManager.isAutoLoginEnabled) "자동 로그인이 활성화되었습니다." else "자동 로그인이 비활성화되었습니다."
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                    1, 2 -> {
                        prefManager.clearCredentials()
                        Toast.makeText(this, R.string.credentials_cleared, Toast.LENGTH_SHORT).show()
                        binding.webView.reload()
                    }
                    3 -> showChangeServerUrlDialog()
                    4 -> {
                        CookieManager.getInstance().removeAllCookies(null)
                        binding.webView.clearCache(true)
                        Toast.makeText(this, "캐시와 쿠키가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                        loadMainUrl()
                    }
                    5 -> binding.webView.reload()
                }
            }
            .setPositiveButton("닫기", null)
            .show()
    }

    private fun showChangeServerUrlDialog() {
        val editText = EditText(this).apply {
            setText(prefManager.serverUrl)
            hint = "http://darktype.synology.me:10111"
            setSelection(text.length)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("서버 주소 변경")
            .setView(editText)
            .setPositiveButton("저장 및 이동") { _, _ ->
                val newUrl = editText.text.toString().trim()
                if (newUrl.isNotEmpty()) {
                    prefManager.serverUrl = newUrl
                    loadMainUrl()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun loadMainUrl() {
        binding.webView.loadUrl(prefManager.serverUrl)
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    if (backPressedOnce) {
                        finish()
                    } else {
                        backPressedOnce = true
                        Toast.makeText(this@MainActivity, "한 번 더 누르면 종료됩니다.", Toast.LENGTH_SHORT).show()
                        handler.postDelayed({ backPressedOnce = false }, 2000)
                    }
                }
            }
        })
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }
}
