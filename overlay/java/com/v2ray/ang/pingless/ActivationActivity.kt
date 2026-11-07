package com.v2ray.ang.pingless

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/**
 * صفحه اصلی PingLess:
 *  - فعال‌سازی با کد (از ربات تلگرام)
 *  - افزودن لینک اشتراک دلخواه
 *  - ورود به صفحه اتصال (v2rayNG)
 *
 * بعد از فعال‌سازی دو گروه اشتراک اضافه می‌شود:
 *  ⚡️ خودکار: یک کانفیگ با balancer که Xray خودش سریع‌ترین سرور را انتخاب می‌کند
 *  🌍 دستی: لیست لوکیشن‌ها برای انتخاب دستی
 */
class ActivationActivity : Activity() {

    companion object {
        const val API_BASE = "https://api.sellbot77.ir"
        const val SUPPORT_URL = "https://t.me/ping_lessbot"
        private const val PREFS = "pingless"
        private const val MAIN_ACTIVITY = "com.v2ray.ang.ui.MainActivity"
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var statusText: TextView
    private lateinit var codeInput: EditText
    private lateinit var subInput: EditText
    private lateinit var activateBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        refreshStatus()
    }

    // ---------------------------------------------------------------- UI

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun rounded(color: Int, radius: Int, stroke: Int? = null): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(radius).toFloat()
        if (stroke != null) g.setStroke(dp(1), stroke)
        return g
    }

    private fun label(text: String, sizeSp: Float, bold: Boolean, color: Int, center: Boolean): TextView {
        val t = TextView(this)
        t.text = text
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        t.gravity = if (center) Gravity.CENTER else Gravity.START
        return t
    }

    private fun input(hint: String, type: Int): EditText {
        val e = EditText(this)
        e.hint = hint
        e.inputType = type
        e.setSingleLine(true)
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        e.setPadding(dp(14), dp(12), dp(14), dp(12))
        e.background = rounded(Color.parseColor("#F4F4F4"), 12, Color.parseColor("#DDDDDD"))
        e.gravity = Gravity.CENTER
        return e
    }

    private fun button(text: String, filled: Boolean, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.setAllCaps(false)
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        b.setTextColor(if (filled) Color.WHITE else Color.BLACK)
        b.background = rounded(if (filled) Color.BLACK else Color.WHITE, 12, Color.BLACK)
        b.setOnClickListener { onClick() }
        return b
    }

    private fun lp(top: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        p.topMargin = dp(top)
        return p
    }

    private fun buildUi(): View {
        val black = Color.parseColor("#111111")
        val gray = Color.parseColor("#666666")
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.layoutDirection = View.LAYOUT_DIRECTION_RTL
        col.setPadding(dp(24), dp(36), dp(24), dp(36))
        col.setBackgroundColor(Color.WHITE)

        val logo = label("P", 46f, true, Color.WHITE, true)
        logo.background = rounded(Color.BLACK, 26)
        val logoLp = LinearLayout.LayoutParams(dp(92), dp(92))
        logoLp.gravity = Gravity.CENTER_HORIZONTAL
        col.addView(logo, logoLp)

        col.addView(label("PingLess", 26f, true, black, true), lp(12))
        statusText = label("", 14f, false, gray, true)
        col.addView(statusText, lp(6))

        col.addView(button("🚀 ورود و اتصال", true) { openMain() }, lp(24))

        col.addView(label("🔑 فعال‌سازی با کد", 17f, true, black, false), lp(32))
        col.addView(label("کد فعال‌سازی را از ربات تلگرام، بخش «سرویس‌های من» بگیرید.", 13f, false, gray, false), lp(4))
        codeInput = input(
            "مثلاً ABCD2345",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        )
        col.addView(codeInput, lp(10))
        activateBtn = button("فعال‌سازی", true) { activate() }
        col.addView(activateBtn, lp(10))

        col.addView(label("🔗 افزودن لینک اشتراک دلخواه", 17f, true, black, false), lp(32))
        col.addView(label("هر لینک اشتراکی (مثلاً از پنل دیگر) را اینجا وارد کنید.", 13f, false, gray, false), lp(4))
        subInput = input("https://...", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        col.addView(subInput, lp(10))
        col.addView(button("افزودن اشتراک", false) { addCustomSub() }, lp(10))

        col.addView(button("💬 پشتیبانی و خرید", false) { openUrl(SUPPORT_URL) }, lp(32))

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.WHITE)
        scroll.isFillViewport = true
        scroll.addView(col)
        return scroll
    }

    // ---------------------------------------------------------------- logic

    private fun prefs(): SharedPreferences = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun refreshStatus() {
        val name = prefs().getString("name", null)
        statusText.text = if (name != null) "✅ فعال: $name" else "برای شروع، کد فعال‌سازی را وارد کنید"
    }

    private fun deviceId(): String {
        val p = prefs()
        val saved = p.getString("device", null)
        if (saved != null) return saved
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val id = if (androidId.isNullOrBlank() || androidId.length < 6) UUID.randomUUID().toString() else "a-$androidId"
        p.edit().putString("device", id).apply()
        return id
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun postJson(url: String, body: JSONObject): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else (c.errorStream ?: c.inputStream)
            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return JSONObject(text)
        } finally {
            c.disconnect()
        }
    }

    private fun activate() {
        val code = codeInput.text.toString().trim().uppercase().replace(" ", "").replace("-", "")
        if (code.length < 6) {
            toast("کد فعال‌سازی را کامل وارد کنید")
            return
        }
        activateBtn.isEnabled = false
        activateBtn.text = "در حال بررسی..."
        val device = deviceId()
        val model = (Build.MANUFACTURER + " " + Build.MODEL).trim()
        Thread {
            val result = runCatching {
                postJson(
                    "$API_BASE/api/app/activate",
                    JSONObject().put("code", code).put("device", device).put("name", model)
                )
            }
            main.post {
                activateBtn.isEnabled = true
                activateBtn.text = "فعال‌سازی"
                val j = result.getOrNull()
                if (j == null) {
                    toast("ارتباط با سرور برقرار نشد. اینترنت را بررسی و دوباره تلاش کنید.")
                } else if (!j.optBoolean("ok")) {
                    toast(j.optString("message", "کد نامعتبر است"))
                } else {
                    val sub = j.optString("sub_url")
                    prefs().edit()
                        .putString("code", code)
                        .putString("name", j.optString("name"))
                        .putString("sub", sub)
                        .apply()
                    refreshStatus()
                    importSubscriptions(code, device, sub)
                }
            }
        }.start()
    }

    private fun importSubscriptions(code: String, device: String, sub: String) {
        val auto = "$API_BASE/api/app/auto?code=" + enc(code) + "&device=" + enc(device)
        installSub(auto, "⚡️ PingLess خودکار")
        main.postDelayed({
            if (sub.isNotBlank()) installSub(sub, "🌍 PingLess انتخاب دستی")
            toast("✅ فعال شد! در صفحه اتصال، «PingLess خودکار» را انتخاب و وصل شوید.")
        }, 1800)
    }

    private fun addCustomSub() {
        val url = subInput.text.toString().trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            toast("لینک اشتراک باید با https:// شروع شود")
            return
        }
        installSub(url, "🔗 اشتراک دلخواه")
        subInput.setText("")
    }

    private fun installSub(url: String, name: String) {
        val uri = Uri.parse("v2rayng://install-sub?url=" + Uri.encode(url) + "#" + Uri.encode(name))
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage(packageName)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast("افزودن اشتراک ممکن نشد")
        }
    }

    private fun openMain() {
        try {
            startActivity(Intent().setClassName(this, MAIN_ACTIVITY))
        } catch (e: Exception) {
            toast("صفحه اتصال باز نشد")
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            toast(url)
        }
    }
}
