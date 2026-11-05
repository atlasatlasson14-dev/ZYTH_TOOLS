package com.zyth.tools

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.zip.ZipFile

/**
 * APK metadata inspection and safe menu-layout authoring.
 *
 * This activity deliberately never rewrites, repacks, injects into, or signs an uploaded APK.
 * Menu layouts are exported as JSON for source projects that explicitly implement the contract.
 */
class AiModBuilderActivity : Activity() {
    private val purple = Color.rgb(181, 77, 255)
    private val purpleLight = Color.rgb(215, 165, 255)
    private val backgroundColor = Color.rgb(8, 7, 14)
    private val surface = Color.rgb(20, 17, 30)
    private val raised = Color.rgb(31, 26, 44)
    private val muted = Color.rgb(177, 169, 192)
    private val items = mutableListOf<JSONObject>()
    private val worker = Executors.newSingleThreadExecutor()

    private lateinit var content: LinearLayout
    private var apkSummary: TextView? = null
    private var previewContent: LinearLayout? = null
    private var buildStatus: TextView? = null
    private var latestRunUrl: String? = null
    private var apkReport = "No APK selected. Choose one to inspect its package metadata and basic device compatibility."
    private var menuTitle = "ZYTH Menu"

    private val actionsUrl =
        "https://github.com/atlasatlasson14-dev/ZYTH_TOOLS/actions/workflows/android-build.yml"
    private val statusApiUrl =
        "https://api.github.com/repos/atlasatlasson14-dev/ZYTH_TOOLS/actions/runs?per_page=1"
    private val releaseApkUrl =
        "https://github.com/atlasatlasson14-dev/ZYTH_TOOLS/releases/latest/download/ZYTH_TOOLS.apk"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        showBuilder()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun showBuilder() {
        baseScreen(
            "AI MOD BUILDER",
            "Inspect APK metadata and create a menu layout for compatible source projects."
        )

        val scopeCard = card()
        scopeCard.addView(labelView("SAFE APK ANALYSIS", purpleLight, 12f, true))
        scopeCard.addView(bodyView(
            "APK inspection stays on this device and checks package metadata, minimum Android API, and native CPU libraries. It does not upload or modify the APK."
        ))
        content.addView(scopeCard, blockParams(bottom = 12))

        button("INSERT APK  /  ANALYZE", true) { openApkPicker() }
        val apkCard = card()
        apkCard.addView(labelView("APK DETAILS", purpleLight, 12f, true))
        apkSummary = bodyView(apkReport)
        apkCard.addView(apkSummary)
        content.addView(apkCard, blockParams(bottom = 16))

        val warning = card()
        warning.addView(labelView("THIRD-PARTY GAME WARNING", Color.rgb(255, 172, 112), 12f, true))
        warning.addView(bodyView(
            "Modifying third-party games can violate their rules and may result in account bans. This tool will not patch protected apps or silently change an APK."
        ))
        content.addView(warning, blockParams(bottom = 18))

        label("MENU LAYOUT")
        val generatorNote = card()
        generatorNote.addView(labelView("GENERATOR STATUS", purpleLight, 12f, true))
        generatorNote.addView(bodyView(
            "This build uses a local starter-template generator; no AI model service is configured. Generated controls are editable and previewed below."
        ))
        content.addView(generatorNote, blockParams(bottom = 10))

        val titleField = inputField("Menu title", menuTitle)
        titleField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                menuTitle = s?.toString().orEmpty()
                renderPreview()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        content.addView(titleField, blockParams(bottom = 10))

        button("GENERATE STARTER MENU", true) { generateStarterMenu() }
        button("＋  ADD BUTTON", false) { addItem("button") }
        button("＋  ADD TOGGLE", false) { addItem("toggle") }
        button("＋  ADD SLIDER", false) { addItem("slider") }
        button("＋  ADD DROPDOWN", false) { addItem("dropdown") }

        label("COMPONENTS  •  ${items.size}")
        if (items.isEmpty()) {
            val empty = card()
            empty.addView(bodyView("Generate a starter layout or add controls individually."))
            content.addView(empty, blockParams(bottom = 12))
        } else {
            items.forEachIndexed { index, item ->
                content.addView(componentCard(index, item), blockParams(bottom = 10))
            }
        }

        label("LIVE PREVIEW")
        val previewCard = card()
        previewContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        previewCard.addView(previewContent)
        content.addView(previewCard, blockParams(bottom = 12))
        renderPreview()

        button("EXPORT MENU CONFIG", true) { exportMenuConfig() }
        button("INSERT MENU INTO PROJECT", false) { explainSupportedIntegration() }

        label("CLOUD BUILD")
        val buildCard = card()
        buildCard.addView(labelView("BUILD APK", purpleLight, 12f, true))
        buildCard.addView(bodyView(
            "Cloud compilation currently builds ZYTH TOOLS from its GitHub source. It does not compile or repackage the APK selected above. The GitHub workflow builds a debug-signed testing APK."
        ))
        content.addView(buildCard, blockParams(bottom = 10))
        button("OPEN BUILD WORKFLOW", false) { openBuildWorkflow() }
        button("CHECK LATEST BUILD STATUS", false) { checkLatestBuild() }
        buildStatus = bodyView("Build status has not been checked.")
        content.addView(buildStatus, blockParams(bottom = 10))
        button("OPEN LATEST BUILD RUN", false) { openLatestRun() }
        button("DOWNLOAD ZYTH TOOLS APK", false) { downloadLatestZythApk() }
        button("BACK TO HOME", false) { finish() }
    }

    private fun openApkPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.android.package-archive"
        }
        try {
            startActivityForResult(intent, REQUEST_INSPECT_APK)
        } catch (_: Exception) {
            toast("No compatible file picker is available.")
        }
    }

    @Deprecated("Retained for compatibility with the document picker result.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when {
            requestCode == REQUEST_INSPECT_APK && resultCode == Activity.RESULT_OK -> {
                val uri = data?.data ?: return
                apkSummary?.text = "Analyzing APK metadata on this device…"
                worker.execute {
                    val result = try {
                        inspectApk(uri)
                    } catch (e: Exception) {
                        "APK analysis failed: ${e.message ?: "the file could not be parsed"}.\nThis file is unsupported by the metadata analyzer; no changes were made."
                    }
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) apkSummary?.text = result
                    }
                }
            }
            requestCode == REQUEST_SAVE_CONFIG && resultCode == Activity.RESULT_OK -> {
                val uri = data?.data ?: return
                try {
                    val output = contentResolver.openOutputStream(uri)
                    if (output == null) {
                        toast("Could not access the selected save location.")
                    } else {
                        output.bufferedWriter().use { it.write(pendingConfigJson) }
                        toast("Menu configuration exported.")
                    }
                } catch (_: Exception) {
                    toast("Could not save the menu configuration.")
                }
            }
        }
    }

    private fun inspectApk(uri: Uri): String {
        val displayName = queryDisplayName(uri) ?: "Selected APK"
        val tempFile = File(cacheDir, "zyth-inspect-${System.currentTimeMillis()}.apk")
        try {
            val input = contentResolver.openInputStream(uri)
                ?: throw IOException("Android could not read the selected file")
            input.use { source ->
                tempFile.outputStream().use { destination ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_APK_BYTES) throw IOException("APK exceeds the 512 MB inspection limit")
                        destination.write(buffer, 0, count)
                    }
                    if (total == 0L) throw IOException("selected file is empty")
                }
            }

            val packageInfo = getArchivePackageInfo(tempFile.absolutePath)
                ?: throw IOException("Android could not read a package manifest from this file")
            val appInfo = packageInfo.applicationInfo
                ?: throw IOException("package metadata did not include an application record")
            appInfo.sourceDir = tempFile.absolutePath
            appInfo.publicSourceDir = tempFile.absolutePath

            val label = try {
                appInfo.loadLabel(packageManager).toString()
            } catch (_: Exception) {
                displayName
            }
            val minimumApi = appInfo.minSdkVersion
            val targetApi = appInfo.targetSdkVersion
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            val nativeAbis = readNativeAbis(tempFile)
            val deviceAbis = Build.SUPPORTED_ABIS.toSet()
            val issues = mutableListOf<String>()
            if (minimumApi > Build.VERSION.SDK_INT) {
                issues.add("Requires Android API $minimumApi; this device reports API ${Build.VERSION.SDK_INT}.")
            }
            if (nativeAbis.isNotEmpty() && nativeAbis.intersect(deviceAbis).isEmpty()) {
                issues.add("No APK native CPU library matches this device (${deviceAbis.joinToString()}).")
            }
            val compatibility = if (issues.isEmpty()) {
                "BASIC COMPATIBILITY: passed API/ABI checks. This does not guarantee installation or runtime compatibility."
            } else {
                "BASIC COMPATIBILITY: likely unsupported.\n" + issues.joinToString("\n")
            }
            val abiSummary = if (nativeAbis.isEmpty()) "No native libraries detected" else nativeAbis.joinToString()

            return buildString {
                appendLine("File: $displayName")
                appendLine("App: $label")
                appendLine("Package: ${packageInfo.packageName}")
                appendLine("Version: ${packageInfo.versionName ?: "unknown"} ($versionCode)")
                appendLine("Minimum Android API: ${if (minimumApi > 0) minimumApi else "unknown"}")
                appendLine("Target Android API: ${if (targetApi > 0) targetApi else "unknown"}")
                appendLine("Native ABIs: $abiSummary")
                append(compatibility)
            }
        } finally {
            tempFile.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun getArchivePackageInfo(path: String): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(
                path,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )
        } else {
            packageManager.getPackageArchiveInfo(path, PackageManager.GET_META_DATA)
        }

    private fun readNativeAbis(file: File): Set<String> {
        val abis = linkedSetOf<String>()
        ZipFile(file).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.isDirectory && entry.name.startsWith("lib/")) {
                    val parts = entry.name.split('/')
                    if (parts.size >= 3) abis.add(parts[1])
                }
            }
        }
        return abis
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (column >= 0) cursor.getString(column) else null
                    } else {
                        null
                    }
                }
        } catch (_: Exception) {
            null
        }
    }

    private fun generateStarterMenu() {
        items.clear()
        items.add(JSONObject().put("type", "button").put("name", "Quick Action"))
        items.add(JSONObject().put("type", "toggle").put("name", "Feature Enabled").put("default", false))
        items.add(
            JSONObject().put("type", "slider").put("name", "Intensity")
                .put("min", 0).put("max", 100).put("default", 50)
        )
        items.add(
            JSONObject().put("type", "dropdown").put("name", "Mode")
                .put("choicesText", "Default, Option 2, Option 3")
        )
        showBuilder()
        toast("Generated a local starter template. No remote AI service is configured.")
    }

    private fun addItem(type: String) {
        val item = JSONObject().put("type", type).put("name", "New ${type.replaceFirstChar { it.uppercase() }}")
        when (type) {
            "toggle" -> item.put("default", false)
            "slider" -> item.put("min", 0).put("max", 100).put("default", 50)
            "dropdown" -> item.put("choicesText", "Option 1, Option 2")
        }
        items.add(item)
        showBuilder()
    }

    private fun componentCard(index: Int, item: JSONObject): LinearLayout {
        val box = card()
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(
            labelView("${index + 1}  /  ${item.optString("type").uppercase()}", purpleLight, 12f, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        heading.addView(TextView(this).apply {
            text = "REMOVE"
            textSize = 11f
            setTextColor(Color.rgb(255, 120, 156))
            setPadding(dp(10), dp(8), 0, dp(8))
            setOnClickListener {
                items.removeAt(index)
                showBuilder()
            }
        })
        box.addView(heading)

        val nameField = inputField("Control label", item.optString("name"))
        nameField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                item.put("name", s?.toString().orEmpty())
                renderPreview()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        box.addView(nameField, blockParams(top = 8, bottom = 6))

        when (item.optString("type")) {
            "toggle" -> {
                val defaultToggle = Switch(this).apply {
                    text = "Enabled by default"
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    buttonTintList = android.content.res.ColorStateList.valueOf(purple)
                    isChecked = item.optBoolean("default", false)
                    setOnCheckedChangeListener { _, checked ->
                        item.put("default", checked)
                        renderPreview()
                    }
                }
                box.addView(defaultToggle)
            }
            "slider" -> {
                val minField = numberField("Minimum", item.optInt("min", 0))
                val maxField = numberField("Maximum", item.optInt("max", 100))
                val defaultField = numberField("Default", item.optInt("default", 50))
                bindInteger(minField, item, "min")
                bindInteger(maxField, item, "max")
                bindInteger(defaultField, item, "default")
                box.addView(minField, blockParams(top = 4, bottom = 5))
                box.addView(maxField, blockParams(bottom = 5))
                box.addView(defaultField)
            }
            "dropdown" -> {
                val choicesField = inputField(
                    "Options separated by commas",
                    item.optString("choicesText", "Option 1, Option 2")
                )
                choicesField.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        item.put("choicesText", s?.toString().orEmpty())
                        renderPreview()
                    }
                    override fun afterTextChanged(s: Editable?) = Unit
                })
                box.addView(choicesField, blockParams(top = 4))
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
                renderPreview()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun renderPreview() {
        val target = previewContent ?: return
        target.removeAllViews()
        target.addView(labelView(menuTitle.ifBlank { "Untitled Menu" }, Color.WHITE, 19f, true))
        if (items.isEmpty()) {
            target.addView(bodyView("Add a control to preview the menu."))
            return
        }
        items.forEach { item ->
            val name = item.optString("name").ifBlank { "Unnamed control" }
            when (item.optString("type")) {
                "button" -> {
                    val view = Button(this).apply {
                        text = name
                        isAllCaps = false
                        setTextColor(Color.WHITE)
                        typeface = Typeface.DEFAULT_BOLD
                        background = rounded(purple, purple, 14f)
                        setOnClickListener { toast("$name preview button") }
                    }
                    target.addView(view, blockParams(top = 6, bottom = 4))
                }
                "toggle" -> {
                    val view = Switch(this).apply {
                        text = name
                        textSize = 14f
                        setTextColor(Color.WHITE)
                        buttonTintList = android.content.res.ColorStateList.valueOf(purple)
                        isChecked = item.optBoolean("default", false)
                    }
                    target.addView(view, blockParams(top = 4, bottom = 4))
                }
                "slider" -> {
                    val min = item.optInt("min", 0)
                    val max = item.optInt("max", 100)
                    val default = item.optInt("default", 50).coerceIn(min.coerceAtMost(max), max.coerceAtLeast(min))
                    target.addView(labelView("$name  •  $default", muted, 13f, false))
                    val seek = SeekBar(this).apply {
                        this.max = (max - min).coerceAtLeast(1)
                        progress = (default - min).coerceIn(0, this.max)
                        progressTintList = android.content.res.ColorStateList.valueOf(purple)
                        thumbTintList = android.content.res.ColorStateList.valueOf(purpleLight)
                    }
                    target.addView(seek, blockParams(bottom = 5))
                }
                "dropdown" -> {
                    target.addView(labelView(name, muted, 13f, false))
                    val choices = dropdownChoices(item)
                    val spinner = Spinner(this)
                    spinner.adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        choices.ifEmpty { listOf("No options") }
                    )
                    target.addView(spinner, blockParams(bottom = 4))
                }
            }
        }
    }

    private fun dropdownChoices(item: JSONObject): List<String> =
        item.optString("choicesText")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun exportMenuConfig() {
        if (menuTitle.trim().isEmpty()) {
            toast("Add a title before exporting.")
            return
        }
        for ((index, item) in items.withIndex()) {
            if (item.optString("name").trim().isEmpty()) {
                toast("Component ${index + 1} needs a label.")
                return
            }
            when (item.optString("type")) {
                "slider" -> {
                    if (item.isNull("min") || item.isNull("max") || item.isNull("default")) {
                        toast("Enter whole-number slider values.")
                        return
                    }
                    val minimum = item.optInt("min")
                    val maximum = item.optInt("max")
                    val default = item.optInt("default")
                    if (minimum >= maximum || default !in minimum..maximum) {
                        toast("Slider ${index + 1} needs a valid range and default.")
                        return
                    }
                }
                "dropdown" -> if (dropdownChoices(item).size < 2) {
                    toast("Dropdown ${index + 1} needs at least two options.")
                    return
                }
            }
        }

        val exportedItems = JSONArray()
        items.forEach { item ->
            val copy = JSONObject(item.toString())
            if (copy.optString("type") == "dropdown") {
                val options = JSONArray()
                dropdownChoices(copy).forEach { options.put(it) }
                copy.remove("choicesText")
                copy.put("options", options)
            }
            exportedItems.put(copy)
        }
        val config = JSONObject()
            .put("schemaVersion", 1)
            .put("integration", "ZYTH_MENU_V1")
            .put("title", menuTitle.trim())
            .put(
                "theme",
                JSONObject()
                    .put("background", "#08070E")
                    .put("panel", "#14111E")
                    .put("accent", "#B54DFF")
            )
            .put("items", exportedItems)

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "zyth_menu_config.json")
        }
        pendingConfigJson = config.toString(2)
        try {
            startActivityForResult(intent, REQUEST_SAVE_CONFIG)
        } catch (_: Exception) {
            toast("No compatible file picker is available to export the menu.")
        }
    }

    private var pendingConfigJson = ""

    private fun explainSupportedIntegration() {
        AlertDialog.Builder(this)
            .setTitle("Project integration not detected")
            .setMessage(
                "An APK file alone does not expose a supported menu extension point. This tool will not rewrite or inject into the selected APK. Export the menu JSON, then integrate it into a source project you own that explicitly implements ZYTH_MENU_V1. Protected or unsupported apps remain unchanged."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun openBuildWorkflow() {
        AlertDialog.Builder(this)
            .setTitle("Open cloud build")
            .setMessage(
                "This GitHub Actions workflow compiles ZYTH TOOLS itself, not the selected APK or a generated target app. Sign in to GitHub and use Run workflow to start it. The result is a debug-signed testing APK."
            )
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("OPEN WORKFLOW") { _, _ ->
                openUrl(actionsUrl)
            }
            .show()
    }

    private fun checkLatestBuild() {
        buildStatus?.text = "Checking the latest GitHub Actions run…"
        worker.execute {
            val result = try {
                val connection = (URL(statusApiUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "ZYTH-TOOLS-Android")
                }
                try {
                    if (connection.responseCode !in 200..299) {
                        throw IOException("GitHub returned HTTP ${connection.responseCode}")
                    }
                    val payload = connection.inputStream.bufferedReader().use { it.readText() }
                    val runs = JSONObject(payload).optJSONArray("workflow_runs")
                    if (runs == null || runs.length() == 0) {
                        latestRunUrl = null
                        "No cloud build runs were found."
                    } else {
                        val run = runs.getJSONObject(0)
                        latestRunUrl = run.optString("html_url").takeIf { it.startsWith("https://") }
                        val status = run.optString("status", "unknown")
                        val conclusion = run.optString("conclusion", "")
                        val title = run.optString("display_title", "ZYTH TOOLS build")
                        if (status == "completed") {
                            "$title\nResult: ${conclusion.uppercase().ifBlank { "UNKNOWN" }}"
                        } else {
                            "$title\nStatus: ${status.uppercase()}"
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                "Could not check build status: ${e.message ?: "network error"}. Try again when online."
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) buildStatus?.text = result
            }
        }
    }

    private fun openLatestRun() {
        val url = latestRunUrl
        if (url == null) {
            toast("Check build status first, or open the workflow page.")
        } else {
            openUrl(url)
        }
    }

    private fun downloadLatestZythApk() {
        openUrl(releaseApkUrl)
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            toast("No browser is available to open this link.")
        }
    }

    private fun baseScreen(title: String, subtitle: String) {
        val scroll = android.widget.ScrollView(this).apply {
            setBackgroundColor(backgroundColor)
            isFillViewport = true
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(28))
        }
        scroll.addView(content)
        content.addView(labelView("ZYTH  /  ANDROID TOOLKIT", purpleLight, 11f, true))
        content.addView(labelView(title, Color.WHITE, 29f, true), blockParams(top = 5))
        content.addView(bodyView(subtitle), blockParams(bottom = 16))
        setContentView(scroll)
    }

    private fun label(text: String) {
        content.addView(labelView(text, purpleLight, 12f, true), blockParams(top = 6, bottom = 5))
    }

    private fun labelView(text: String, color: Int, size: Float, bold: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(3))
        }

    private fun bodyView(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(muted)
        setPadding(0, dp(5), 0, dp(3))
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(13), dp(15), dp(13))
        background = rounded(surface, Color.rgb(46, 37, 61), 17f)
    }

    private fun inputField(hintText: String, value: String): EditText =
        EditText(this).apply {
            hint = hintText
            setText(value)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(130, 123, 145))
            textSize = 14f
            setSingleLine(true)
            setPadding(dp(13), dp(10), dp(13), dp(10))
            background = rounded(raised, Color.rgb(65, 48, 85), 12f)
        }

    private fun numberField(hintText: String, value: Int): EditText =
        inputField(hintText, value.toString()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        }

    private fun button(text: String, primary: Boolean, action: () -> Unit) {
        val fill = if (primary) purple else raised
        val control = Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            backgroundTintList = null
            background = rounded(fill, if (primary) purple else Color.rgb(84, 60, 111), 14f)
            minHeight = dp(52)
            setOnClickListener { action() }
        }
        content.addView(control, blockParams(bottom = 8))
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
        private const val REQUEST_INSPECT_APK = 2001
        private const val REQUEST_SAVE_CONFIG = 2002
        private const val MAX_APK_BYTES = 512L * 1024L * 1024L
    }
}
