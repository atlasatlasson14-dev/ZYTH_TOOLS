package com.zyth.tools

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private val purple = Color.rgb(181, 77, 255)
    private val purpleLight = Color.rgb(215, 165, 255)
    private val background = Color.rgb(8, 7, 14)
    private val surface = Color.rgb(20, 17, 30)
    private val surfaceRaised = Color.rgb(31, 26, 44)
    private val muted = Color.rgb(177, 169, 192)
    private val generatedItems = mutableListOf<JSONObject>()

    private lateinit var content: LinearLayout
    private var currentMenuTitle = "Zyth Menu"
    private var pendingJson = ""
    private var pendingInstallUri: Uri? = null
    private var waitingForInstallPermission = false
    private var queuedDownloadId = -1L
    private var downloadReceiverRegistered = false

    // Set to the project's GitHub Releases asset URL before the first cloud build.
    private val apkReleaseUrl =
        "https://github.com/atlasatlasson14-dev/ZYTH_TOOLS/releases/latest/download/ZYTH_TOOLS.apk"

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (completedId != queuedDownloadId) return

            queuedDownloadId = -1L
            val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(completedId)
            manager.query(query).use { cursor ->
                if (!cursor.moveToFirst()) {
                    toast("The APK download could not be checked.")
                    return
                }
                val statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = if (statusColumn >= 0) cursor.getInt(statusColumn) else -1
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val apkUri = manager.getUriForDownloadedFile(completedId)
                    if (apkUri != null) {
                        toast("Download complete. Opening Android's installer.")
                        requestSystemInstall(apkUri)
                    } else {
                        toast("Download finished, but Android could not open the APK.")
                    }
                } else {
                    toast("Download failed. Check your connection and try again.")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = background
        registerDownloadReceiver()
        showHome()
    }

    override fun onResume() {
        super.onResume()
        if (waitingForInstallPermission &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.canRequestPackageInstalls()
        ) {
            waitingForInstallPermission = false
            val uri = pendingInstallUri
            pendingInstallUri = null
            if (uri != null) launchSystemInstaller(uri)
        }
    }

    override fun onDestroy() {
        if (downloadReceiverRegistered) {
            try {
                unregisterReceiver(downloadReceiver)
            } catch (_: IllegalArgumentException) {
                // The receiver was already removed by the system.
            }
        }
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun registerDownloadReceiver() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(downloadReceiver, filter)
        }
        downloadReceiverRegistered = true
    }

    private fun baseScreen(title: String, subtitle: String): LinearLayout {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(this@MainActivity.background)
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(28))
        }
        scroll.addView(outer)

        outer.addView(TextView(this).apply {
            text = "ZYTH  /  ANDROID TOOLKIT"
            textSize = 11f
            letterSpacing = 0.16f
            setTextColor(purpleLight)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(8))
        })
        outer.addView(TextView(this).apply {
            text = title
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            setPadding(0, 0, 0, dp(5))
        })
        outer.addView(TextView(this).apply {
            text = subtitle
            textSize = 14f
            setTextColor(muted)
            setPadding(0, 0, 0, dp(18))
        })

        content = outer
        setContentView(scroll)
        return outer
    }

    private fun showHome() {
        baseScreen("ZYTH TOOLS", "APK utilities and a flexible menu-layout builder.")

        val statusCard = card()
        statusCard.addView(TextView(this).apply {
            text = "QUEST 3S  •  ANDROID APP"
            textSize = 12f
            letterSpacing = 0.08f
            setTextColor(purpleLight)
            typeface = Typeface.DEFAULT_BOLD
        })
        statusCard.addView(TextView(this).apply {
            text = "Designed as a standard 2D Android app. Installation and compatibility still depend on Quest system settings."
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(7), 0, 0)
        })
        content.addView(statusCard, blockParams(bottom = 16))

        button("INSTALL APK", primary = true) { openApkPicker() }
        button("DOWNLOAD APK", primary = false) { downloadLatestApk() }
        button("AI MOD BUILDER", primary = false) {
            startActivity(Intent(this, AiModBuilderActivity::class.java))
        }
        button("MOD MENU GENERATOR", primary = false) { showGenerator() }

        val note = card()
        note.addView(TextView(this).apply {
            text = "SAFE INSTALL FLOW"
            textSize = 12f
            letterSpacing = 0.08f
            setTextColor(purpleLight)
            typeface = Typeface.DEFAULT_BOLD
        })
        note.addView(TextView(this).apply {
            text = "APK installation is handed to Android's official package installer. Only install files from sources you trust."
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(7), 0, 0)
        })
        content.addView(note, blockParams(top = 5))
    }

    private fun openApkPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.android.package-archive"
        }
        try {
            startActivityForResult(intent, REQUEST_PICK_APK)
        } catch (_: Exception) {
            toast("No compatible file picker is available on this device.")
        }
    }

    private fun downloadLatestApk() {
        val uri = Uri.parse(apkReleaseUrl)
        if (uri.scheme != "https") {
            toast("The APK download link must use HTTPS.")
            return
        }
        try {
            val request = DownloadManager.Request(uri)
                .setTitle("ZYTH TOOLS APK")
                .setDescription("Downloading the latest ZYTH TOOLS build")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(
                    this,
                    Environment.DIRECTORY_DOWNLOADS,
                    "ZYTH_TOOLS-${System.currentTimeMillis()}.apk"
                )
            queuedDownloadId = (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            toast("Download started. Android will notify you when it is ready.")
        } catch (_: Exception) {
            toast("Could not start the APK download. Check that downloads are available.")
        }
    }

    private fun requestSystemInstall(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            pendingInstallUri = uri
            AlertDialog.Builder(this)
                .setTitle("Allow APK installation")
                .setMessage("Android requires permission before ZYTH TOOLS can open the official package installer. Enable installs for this app, then return here.")
                .setPositiveButton("OPEN SETTINGS") { _, _ ->
                    waitingForInstallPermission = true
                    try {
                        val settingsIntent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:$packageName")
                        )
                        startActivityForResult(settingsIntent, REQUEST_INSTALL_PERMISSION)
                    } catch (_: Exception) {
                        waitingForInstallPermission = false
                        toast("Open Android settings and allow installs for ZYTH TOOLS.")
                    }
                }
                .setNegativeButton("CANCEL") { _, _ ->
                    pendingInstallUri = null
                }
                .show()
            return
        }
        launchSystemInstaller(uri)
    }

    private fun launchSystemInstaller(uri: Uri) {
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            toast("Android could not open this APK. Check that the file is valid and compatible.")
        }
    }

    @Deprecated("Retained for compatibility with Android's document picker and settings result flow.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_PICK_APK -> {
                if (resultCode == Activity.RESULT_OK) {
                    val uri = data?.data ?: return
                    requestSystemInstall(uri)
                }
            }
            REQUEST_INSTALL_PERMISSION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    packageManager.canRequestPackageInstalls()
                ) {
                    waitingForInstallPermission = false
                    val uri = pendingInstallUri
                    pendingInstallUri = null
                    if (uri != null) launchSystemInstaller(uri)
                } else {
                    waitingForInstallPermission = false
                    toast("Install permission was not enabled.")
                }
            }
            REQUEST_SAVE_JSON -> {
                if (resultCode == Activity.RESULT_OK) {
                    val uri = data?.data ?: return
                    try {
                        val output = contentResolver.openOutputStream(uri)
                        if (output == null) {
                            toast("Could not access the selected save location.")
                        } else {
                            output.bufferedWriter().use { it.write(pendingJson) }
                            toast("Menu JSON exported.")
                        }
                    } catch (_: Exception) {
                        toast("Could not save the JSON file.")
                    }
                }
            }
        }
    }

    private fun showGenerator() {
        baseScreen("MENU BUILDER", "Create a reusable layout for your own app or a game that explicitly supports mods.")

        label("MENU TITLE", 12f, purpleLight, bold = true)
        val titleField = inputField("Menu title", currentMenuTitle)
        titleField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentMenuTitle = s?.toString().orEmpty()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        content.addView(titleField, blockParams(bottom = 16))

        label("ADD COMPONENT", 12f, purpleLight, bold = true)
        button("＋  ADD BUTTON", primary = false) {
            generatedItems.add(JSONObject().put("type", "button").put("name", "New Button"))
            showGenerator()
        }
        button("＋  ADD TOGGLE", primary = false) {
            generatedItems.add(
                JSONObject().put("type", "toggle").put("name", "New Toggle").put("default", false)
            )
            showGenerator()
        }
        button("＋  ADD SLIDER", primary = false) {
            generatedItems.add(
                JSONObject().put("type", "slider").put("name", "New Slider")
                    .put("min", 0).put("max", 100).put("default", 50)
            )
            showGenerator()
        }

        label("COMPONENTS  •  ${generatedItems.size}", 12f, purpleLight, bold = true)
        if (generatedItems.isEmpty()) {
            val empty = card()
            empty.addView(TextView(this).apply {
                text = "Your menu is empty. Add a button, toggle, or slider to get started."
                textSize = 14f
                setTextColor(muted)
            })
            content.addView(empty, blockParams(bottom = 14))
        } else {
            generatedItems.forEachIndexed { index, item ->
                content.addView(componentCard(index, item), blockParams(bottom = 12))
            }
        }

        button("EXPORT MENU JSON", primary = true) { exportMenu() }
        button("CLEAR MENU", primary = false) {
            AlertDialog.Builder(this)
                .setTitle("Clear this menu?")
                .setMessage("This removes all components from the current menu.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("CLEAR") { _, _ ->
                    generatedItems.clear()
                    showGenerator()
                }
                .show()
        }
        button("BACK TO HOME", primary = false) { showHome() }

        val safeUse = card()
        safeUse.addView(TextView(this).apply {
            text = "LAYOUT EXPORT ONLY"
            textSize = 12f
            letterSpacing = 0.08f
            setTextColor(purpleLight)
            typeface = Typeface.DEFAULT_BOLD
        })
        safeUse.addView(TextView(this).apply {
            text = "This exports configuration JSON. It does not inject code, modify another app, or bypass game protections."
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(7), 0, 0)
        })
        content.addView(safeUse, blockParams(top = 6))
    }

    private fun componentCard(index: Int, item: JSONObject): LinearLayout {
        val box = card()
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(TextView(this).apply {
            text = "${index + 1}  /  ${item.optString("type").uppercase()}"
            textSize = 12f
            letterSpacing = 0.05f
            setTextColor(purpleLight)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        heading.addView(TextView(this).apply {
            text = "REMOVE"
            textSize = 11f
            letterSpacing = 0.04f
            setTextColor(Color.rgb(255, 120, 156))
            setPadding(dp(10), dp(6), 0, dp(6))
            setOnClickListener {
                generatedItems.removeAt(index)
                showGenerator()
            }
        })
        box.addView(heading)

        val nameField = inputField("Component label", item.optString("name"))
        nameField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                item.put("name", s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        box.addView(nameField, blockParams(top = 10, bottom = 8))

        when (item.optString("type")) {
            "toggle" -> {
                val defaultSwitch = Switch(this).apply {
                    text = "Enabled by default"
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    buttonTintList = android.content.res.ColorStateList.valueOf(purple)
                    isChecked = item.optBoolean("default", false)
                    setOnCheckedChangeListener { _, checked -> item.put("default", checked) }
                }
                box.addView(defaultSwitch, blockParams(top = 2))
            }
            "slider" -> {
                labelInto(box, "RANGE AND DEFAULT", 11f, muted, bold = true)
                val minField = numberField("Minimum", item.optInt("min", 0))
                val maxField = numberField("Maximum", item.optInt("max", 100))
                val defaultField = numberField("Default value", item.optInt("default", 50))
                bindInteger(minField, item, "min")
                bindInteger(maxField, item, "max")
                bindInteger(defaultField, item, "default")
                box.addView(minField, blockParams(top = 6, bottom = 7))
                box.addView(maxField, blockParams(bottom = 7))
                box.addView(defaultField, blockParams(bottom = 2))
            }
        }
        return box
    }

    private fun bindInteger(field: EditText, item: JSONObject, key: String) {
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val value = s?.toString()?.trim()?.toIntOrNull()
                if (value == null) item.put(key, JSONObject.NULL) else item.put(key, value)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun numberField(hintText: String, value: Int): EditText =
        inputField(hintText, value.toString()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        }

    private fun inputField(hintText: String, value: String): EditText =
        EditText(this).apply {
            hint = hintText
            setText(value)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(130, 123, 145))
            textSize = 14f
            setSingleLine(true)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(surfaceRaised, Color.rgb(65, 48, 85), 13f)
        }

    private fun exportMenu() {
        val title = currentMenuTitle.trim()
        if (title.isEmpty()) {
            toast("Add a title for this menu before exporting.")
            return
        }
        for ((index, item) in generatedItems.withIndex()) {
            if (item.optString("name").trim().isEmpty()) {
                toast("Component ${index + 1} needs a label.")
                return
            }
            if (item.optString("type") == "slider") {
                if (item.isNull("min") || item.isNull("max") || item.isNull("default")) {
                    toast("Enter whole numbers for slider ${index + 1}.")
                    return
                }
                val min = item.optInt("min")
                val max = item.optInt("max")
                val defaultValue = item.optInt("default")
                if (min >= max) {
                    toast("Slider ${index + 1} needs a maximum greater than its minimum.")
                    return
                }
                if (defaultValue !in min..max) {
                    toast("Slider ${index + 1}'s default must be within its range.")
                    return
                }
            }
        }

        val exportedItems = JSONArray()
        generatedItems.forEach { exportedItems.put(JSONObject(it.toString())) }
        val json = JSONObject()
            .put("schemaVersion", 1)
            .put("title", title)
            .put(
                "theme",
                JSONObject()
                    .put("background", "#08070E")
                    .put("panel", "#14111E")
                    .put("accent", "#B54DFF")
            )
            .put("items", exportedItems)

        pendingJson = json.toString(2)
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "zyth_menu.json")
        }
        try {
            startActivityForResult(intent, REQUEST_SAVE_JSON)
        } catch (_: Exception) {
            toast("No compatible file picker is available to save the JSON.")
        }
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = rounded(surface, Color.rgb(46, 37, 61), 18f)
    }

    private fun label(
        text: String,
        size: Float = 14f,
        color: Int = muted,
        bold: Boolean = false
    ) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(7), 0, dp(8))
        })
    }

    private fun labelInto(
        parent: LinearLayout,
        text: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ) {
        parent.addView(TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(7), 0, dp(2))
        })
    }

    private fun button(text: String, primary: Boolean, action: () -> Unit) {
        val fill = if (primary) purple else surfaceRaised
        val outline = if (primary) purple else Color.rgb(84, 60, 111)
        val view = Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 14f
            letterSpacing = 0.03f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            backgroundTintList = null
            background = rounded(fill, outline, 15f)
            minHeight = dp(54)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { action() }
        }
        content.addView(view, blockParams(bottom = 10))
    }

    private fun rounded(fill: Int, stroke: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp.toInt()).toFloat()
            setColor(fill)
            setStroke(dp(1), stroke)
        }

    private fun blockParams(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    companion object {
        private const val REQUEST_PICK_APK = 1001
        private const val REQUEST_SAVE_JSON = 1002
        private const val REQUEST_INSTALL_PERMISSION = 1003
    }
}
