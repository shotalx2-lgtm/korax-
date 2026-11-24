package com.koora.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.core.content.ContextCompat
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal val KBg: Color get() = KoraxThemePalette.background
internal val KCard: Color get() = KoraxThemePalette.card
internal val KCard2: Color get() = KoraxThemePalette.card2
internal val KLime: Color get() = KoraxThemePalette.accent
internal val KText: Color get() = KoraxThemePalette.text
internal val KMuted: Color get() = KoraxThemePalette.muted
internal val KBorder: Color get() = KoraxThemePalette.border

class VisualShellActivity : ComponentActivity() {
    private var smartAlertsJob: Job? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KoraxSmartAlerts.ensureChannels(this)
        KoraxSmartAlerts.setBackgroundUpdates(this, KoraxSettingsStore.getBoolean(this, "background_updates", true))
        window.statusBarColor = android.graphics.Color.rgb(0, 0, 0)
        window.navigationBarColor = android.graphics.Color.rgb(0, 0, 0)
        @Suppress("DEPRECATION") window.decorView.systemUiVisibility = 0
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                val paletteLight = KoraxThemePalette.light
                val scheme = if (paletteLight) {
                    lightColorScheme(
                        primary = KLime, onPrimary = Color.Black, secondary = KLime,
                        background = KBg, onBackground = KText, surface = KCard,
                        onSurface = KText, outline = KBorder
                    )
                } else {
                    darkColorScheme(
                        primary = KLime, onPrimary = Color.Black, secondary = KLime,
                        background = KBg, onBackground = KText, surface = KCard,
                        onSurface = KText, outline = KBorder
                    )
                }
                MaterialTheme(colorScheme = scheme) { KoraxSplash() }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        smartAlertsJob?.cancel()
        smartAlertsJob = lifecycleScope.launch {
            while (isActive) {
                runCatching { KoraxSmartAlerts.poll(this@VisualShellActivity) }
                delay(30_000L)
            }
        }
    }

    override fun onStop() {
        smartAlertsJob?.cancel()
        smartAlertsJob = null
        super.onStop()
    }
}

@Composable
private fun KoraxSplash() {
    var intro by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(900); intro = false }
    if (intro) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color(0xFF101A12), Color(0xFF050807), Color(0xFF000000))
                )
            ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.korax_brand),
                    contentDescription = "KORAX",
                    modifier = Modifier.size(184.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "كرة القدم بين يديك",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    "مباريات مباشرة • أخبار • بطولات",
                    color = Color(0xFFD5FF00),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.1.sp
                )
                Spacer(Modifier.height(28.dp))
                Box(
                    Modifier.width(44.dp).height(3.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFD5FF00))
                )
            }
        }
    } else KoraxApp()
}

internal data class NewsItem(val id: String, val title: String, val summary: String, val url: String, val source: String, val date: String, val image: String?, val category: String = "football")
private data class TransferItem(val id: String, val player: String, val playerImage: String, val from: String, val fromLogo: String, val to: String, val toLogo: String, val type: String, val date: String, val amount: String, val completed: Boolean, val league: String)
private data class MatchItem(val id: String, val home: String, val away: String, val homeLogo: String, val awayLogo: String, val score: String, val status: String, val kickoff: String, val league: String, val detailsAvailable: Boolean)

private fun apiBase() = BuildConfig.KOORA_API_BASE_URL.trim().trimEnd('/')
internal suspend fun apiJson(path: String): JSONObject = withContext(Dispatchers.IO) {
    val base = apiBase()
    require(base.startsWith("https://")) { "خدمة البيانات غير مربوطة بعد. اضبط KOORA_API_BASE_URL أولًا." }
    val c = (URL(base + path).openConnection() as HttpURLConnection).apply {
        connectTimeout = 12000
        readTimeout = 18000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
        setRequestProperty("Accept-Language", "ar,en;q=0.8")
        setRequestProperty("User-Agent", "KORAX-Android/0.6")
    }
    try {
        val code = c.responseCode
        if (code !in 200..299) {
            val body = runCatching { c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty() }.getOrDefault("")
            error("خادم KORAX أعاد HTTP $code" + if (body.isBlank()) "" else ": " + body.take(180))
        }
        JSONObject(c.inputStream.bufferedReader().use { it.readText() })
    } finally { c.disconnect() }
}
internal fun JSONObject.rows() = optJSONArray("data") ?: JSONArray()
internal fun JSONObject.https(name: String) = optString(name).takeIf { it.startsWith("https://") }.orEmpty()
internal fun JSONObject.firstHttps(vararg names: String): String =
    names.firstNotNullOfOrNull { name -> https(name).takeIf { it.isNotBlank() } }.orEmpty()

private suspend fun loadNews(): List<NewsItem> {
    // The endpoint already filters Arabic. Keep older Worker responses that omit
    // the language field, but never mix explicitly English-only items into the feed.
    val primary = apiJson("/api/news?language=ar&per_page=100")
    var data = primary.rows()
    if (data.length() == 0) {
        val fallback = apiJson("/api/news?per_page=100").rows()
        data = JSONArray()
        for (index in 0 until fallback.length()) {
            val row = fallback.optJSONObject(index) ?: continue
            val language = row.optString("language")
            if (language.isBlank() || language.equals("ar", ignoreCase = true)) data.put(row)
        }
    }
    val parsed = (0 until data.length()).mapNotNull { i ->
        val x = data.optJSONObject(i) ?: return@mapNotNull null
        val language = x.optString("language")
        if (language.isNotBlank() && !language.equals("ar", ignoreCase = true)) return@mapNotNull null
        val url = x.optString("url")
        val title = x.optString("title").trim()
        val summary = x.optString("summary")
        val source = x.optString("source", "مصدر رياضي")
        if (title.isBlank() || !url.startsWith("https://")) return@mapNotNull null
        if (!FootballNewsFilter.isFootballNews(title, summary, source)) return@mapNotNull null
        NewsItem(x.optString("id", url), title, summary, url,
            source, x.optString("publishedAt"),
            x.firstHttps("imageUrl", "image_path", "image_url", "photo_url", "thumbnail").takeIf { it.isNotBlank() },
            x.optString("category", "football"))
    }
    val seen = HashSet<String>()
    return parsed.filter { item ->
        val key = item.title.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
        key.isNotBlank() && seen.add(key)
    }
}

private suspend fun loadTransfers(): List<TransferItem> {
    val data = apiJson("/api/transfers?per_page=50&page=1").rows()
    return (0 until data.length()).mapNotNull { i ->
        val x = data.optJSONObject(i) ?: return@mapNotNull null
        val p = x.optJSONObject("player"); val f = x.optJSONObject("fromTeam"); val t = x.optJSONObject("toTeam")
        val name = p?.optString("name").orEmpty().ifBlank { x.optString("playerName") }
        if (name.isBlank()) return@mapNotNull null
        val amount = x.opt("amount")
        TransferItem(x.optString("id", i.toString()), name, p?.firstHttps("image_path", "imageUrl", "image_url", "photo_url", "photo", "thumbnail").orEmpty(),
            f?.optString("name").orEmpty().ifBlank { "غير محدد" }, f?.firstHttps("image_path", "imageUrl", "image_url", "logoUrl", "logo_url", "crest", "badge").orEmpty(),
            t?.optString("name").orEmpty().ifBlank { "غير محدد" }, t?.firstHttps("image_path", "imageUrl", "image_url", "logoUrl", "logo_url", "crest", "badge").orEmpty(),
            x.optString("typeName").ifBlank { "انتقال" }, x.optString("date"),
            if (amount == null || amount == JSONObject.NULL || amount.toString() == "0") "القيمة غير معلنة" else amount.toString(),
            x.optBoolean("completed"), x.optJSONObject("league")?.optString("name").orEmpty().ifBlank { x.optString("leagueName") })
    }
}

private fun localizeMatchStatus(raw: String): String {
    val value = raw.lowercase(Locale.ROOT).replace("_", " ").trim()
    return when {
        value.contains("live") || value.contains("in play") || value.contains("inplay") -> "مباشر"
        value.contains("half time") || value.contains("halftime") -> "استراحة"
        value.contains("finished") || value == "ft" || value.contains("full time") -> "انتهت"
        value.contains("not started") || value.contains("scheduled") || value == "ns" -> "لم تبدأ"
        value.contains("postponed") -> "تأجلت"
        value.contains("cancelled") || value.contains("canceled") -> "أُلغيت"
        value.contains("suspended") -> "متوقفة"
        else -> raw.ifBlank { "الحالة غير متاحة" }
    }
}

private fun displayKickoff(raw: String): String {
    if (raw.isBlank()) return "موعد البداية غير متاح"
    val parsed = runCatching {
        DateTimeFormatter.ofPattern("HH:mm • dd/MM", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse(raw))
    }.getOrNull()
    return parsed ?: raw
}

private suspend fun loadMatches(mode: String, selectedDate: String): List<MatchItem> {
    val data = apiJson("/api/matches?mode=$mode&scope=main&date=$selectedDate").rows()
    return (0 until data.length()).mapNotNull { i ->
        val x = data.optJSONObject(i) ?: return@mapNotNull null
        val h = x.optJSONObject("homeTeam")
        val a = x.optJSONObject("awayTeam")
        val home = h?.optString("name").orEmpty().ifBlank { x.optString("home") }
        val away = a?.optString("name").orEmpty().ifBlank { x.optString("away") }
        if (home.isBlank() || away.isBlank()) return@mapNotNull null
        val hs = x.opt("homeScore")
        val ascore = x.opt("awayScore")
        val score = if (hs != null && ascore != null && hs != JSONObject.NULL && ascore != JSONObject.NULL) "$hs - $ascore" else "VS"
        val stableMatchId = x.optString("id").takeIf { it.isNotBlank() }
        val fixtureId = stableMatchId ?: listOf(
            home, away, x.optString("startTime"), x.optString("competitionName")
        ).filter { it.isNotBlank() }.joinToString("|").ifBlank { "match-$i" }
        MatchItem(
            fixtureId, home, away,
            h?.firstHttps("crest", "logoUrl", "imageUrl", "image_path", "image_url", "logo", "logo_url", "badge").orEmpty(),
            a?.firstHttps("crest", "logoUrl", "imageUrl", "image_path", "image_url", "logo", "logo_url", "badge").orEmpty(),
            score,
            localizeMatchStatus(x.optString("status", "غير متاحة")),
            x.optString("startTime"),
            x.optJSONObject("competition")?.optString("name").orEmpty().ifBlank { x.optString("competitionName") },
            detailsAvailable = stableMatchId != null && x.optBoolean("detailAvailable", false)
        )
    }.distinctBy { it.id }
}

private val newsCategories = listOf("لك", "الكل", "العالمية", "العربية", "الدوري الإنجليزي", "الدوري الإسباني", "الدوري الإيطالي", "الدوري الألماني", "الدوري الفرنسي", "الدوري السعودي", "الانتقالات", "الإصابات")
private fun matchesCategory(article: NewsItem, category: String): Boolean {
    val text = (article.title + " " + article.summary).lowercase(Locale.ROOT)
    fun has(vararg words: String) = words.any { text.contains(it.lowercase(Locale.ROOT)) }
    return when (category) {
        "الكل" -> true
        "العربية" -> article.category in setOf("egypt", "saudi") ||
            has("الدوري المغربي", "المغرب", "المغربي", "الترجي", "الوداد", "الرجاء", "الكرة العربية", "منتخب مصر", "منتخب المغرب", "منتخب الجزائر", "منتخب تونس", "منتخب العراق", "منتخب السعودية")
        "العالمية" -> article.category !in setOf("egypt", "saudi") &&
            !has("الدوري المغربي", "المغرب", "المغربي", "الترجي", "الوداد", "الرجاء", "الكرة العربية", "منتخب مصر", "منتخب المغرب", "منتخب الجزائر", "منتخب تونس", "منتخب العراق", "منتخب السعودية")
        "الدوري الإنجليزي" -> article.category == "premier-league" ||
            has("الدوري الإنجليزي", "بريميرليغ", "premier league", "ليفربول", "مانشستر سيتي", "أرسنال", "تشيلسي", "توتنهام")
        "الدوري الإسباني" -> article.category == "la-liga" ||
            has("الدوري الإسباني", "الليغا", "la liga", "ريال مدريد", "برشلونة", "أتلتيكو مدريد")
        "الدوري الإيطالي" -> article.category == "serie-a" ||
            has("الدوري الإيطالي", "serie a", "إنتر ميلان", "يوفنتوس", "ميلان", "نابولي")
        "الدوري الألماني" -> article.category == "bundesliga" ||
            has("الدوري الألماني", "bundesliga", "بايرن ميونخ", "دورتموند")
        "الدوري الفرنسي" -> article.category == "ligue-1" ||
            has("الدوري الفرنسي", "ligue 1", "باريس سان جيرمان", "مارسيليا", "موناكو")
        "الدوري السعودي" -> article.category == "saudi" ||
            has("الدوري السعودي", "دوري روشن", "الهلال السعودي", "النصر السعودي", "الاتحاد السعودي", "الأهلي السعودي", "الشباب السعودي", "saudi pro league", "al-hilal", "al-nassr", "al-ittihad", "al-ahli")
        "الانتقالات" -> article.category == "transfers" ||
            has("انتقال", "صفقة", "ميركاتو", "إعارة", "تجديد عقد", "مفاوضات", "ضم اللاعب")
        "الإصابات" -> has("إصابة", "مصاب", "غياب", "إيقاف", "جاهزية", "عودة للتدريبات", "تأهيل", "injury", "injured", "suspended")
        else -> false
    }
}
private class LocalComments(context: Context) {
    private val prefs = context.getSharedPreferences("korax_comments_v1", Context.MODE_PRIVATE)
    private fun key(url: String) = "c_" + url.hashCode()
    fun read(url: String): List<String> = runCatching {
        val arr = JSONArray(prefs.getString(key(url), "[]"))
        (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())
    fun add(url: String, text: String): List<String> {
        val list = (read(url) + text.trim()).takeLast(100)
        val arr = JSONArray(); list.forEach { arr.put(it) }
        prefs.edit().putString(key(url), arr.toString()).apply()
        return list
    }
}

@Composable
private fun KoraxApp() {
    val context = LocalContext.current
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val themeMode = KoraxSettingsStore.theme(context)
    KoraxThemePalette.applyLightMode(when (themeMode) {
        "light" -> true
        "system" -> !systemDark
        else -> false
    })

    var tab by rememberSaveable { mutableStateOf("news") }
    var matchLiveRequested by rememberSaveable { mutableStateOf(false) }
    var matchDate by rememberSaveable { mutableStateOf(todayIso()) }
    var selectedArticle by remember { mutableStateOf<NewsItem?>(null) }
    var selectedProfile by remember { mutableStateOf<KoraxFavorite?>(null) }
    var selectedMatchId by rememberSaveable { mutableStateOf<String?>(null) }
    var setupComplete by remember { mutableStateOf(KoraxFavoriteStore.setupComplete(context)) }
    var permissionAsked by remember { mutableStateOf(KoraxSettingsStore.requestPermissionAsked(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionAsked = true
        KoraxSettingsStore.markPermissionAsked(context)
        if (granted) KoraxSmartAlerts.ensureChannels(context)
    }

    LaunchedEffect(Unit) {
        if (!permissionAsked) {
            KoraxSettingsStore.markPermissionAsked(context)
            permissionAsked = true
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(containerColor = KBg, bottomBar = {
        if (selectedArticle == null && selectedProfile == null && selectedMatchId == null && tab != "settings" && tab != "notifications") {
            NavigationBar(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)),
                containerColor = KCard,
                tonalElevation = 10.dp
            ) {
                val tabs = listOf(
                    Triple("matches", "المباريات", Icons.Filled.CalendarMonth),
                    Triple("news", "الأخبار", Icons.Filled.Search),
                    Triple("competitions", "البطولات", Icons.Filled.Explore),
                    Triple("favorites", "المفضلة", Icons.Filled.Favorite),
                    Triple("more", "المزيد", Icons.Filled.Settings)
                )
                tabs.forEach { (key, label, icon) ->
                    NavigationBarItem(
                        selected = tab == key,
                        onClick = { tab = key },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = KLime, selectedTextColor = KLime,
                            indicatorColor = KLime.copy(alpha = .12f),
                            unselectedIconColor = KMuted, unselectedTextColor = KMuted
                        )
                    )
                }
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(if (selectedArticle == null && selectedProfile == null && selectedMatchId == null) padding else PaddingValues(0.dp))) {
            when {
                selectedArticle != null -> KoraxNewsDetailScreen(selectedArticle!!) { selectedArticle = null }
                selectedProfile != null -> KoraxEntityProfileScreen(
                    favorite = selectedProfile!!,
                    onBack = { selectedProfile = null },
                    onOpenPlayer = { selectedProfile = it }
                )
                selectedMatchId != null -> KoraxMatchDetailPage(selectedMatchId!!) { selectedMatchId = null }
                tab == "news" -> NewsScreen(
                    onOpenLive = { matchLiveRequested = true; tab = "matches" },
                    onChooseDate = { selected -> matchDate = selected; matchLiveRequested = false; tab = "matches" },
                    onOpenArticle = { selectedArticle = it },
                    onExplore = { tab = "explore" }
                )
                tab == "transfers" -> KoraxTopicPage(onBack = { tab = "more" }, onOpenArticle = { selectedArticle = it })
                tab == "matches" -> MatchesScreen(
                    initialLive = matchLiveRequested,
                    selectedDate = matchDate,
                    onOpenMatch = { selectedMatchId = it },
                    onChooseDate = { matchDate = it; matchLiveRequested = false },
                    onSearchEntities = { tab = "match_search" }
                )
                tab == "match_search" -> KoraxFavoriteSelectionScreen(
                    isFirstRun = false,
                    onFinish = { tab = "matches" },
                    onOpenProfile = { selectedProfile = it }
                )
                tab == "competitions" -> KoraxCompetitionsHubPage(
                    onBack = { tab = "more" },
                    onOpenProfile = { selectedProfile = it }
                )
                tab == "explore" -> KoraxFavoriteSelectionScreen(
                    isFirstRun = false,
                    onFinish = {
                        if (!setupComplete) {
                            KoraxFavoriteStore.setSetupComplete(context, true)
                            setupComplete = true
                        }
                        tab = "favorites"
                    },
                    onOpenProfile = { selectedProfile = it }
                )
                tab == "favorites" -> KoraxFavoritesScreen(
                    onOpenProfile = { selectedProfile = it },
                    onAddMore = { tab = "explore" }
                )
                tab == "notifications" -> KoraxNotificationsScreen { tab = "more" }
                tab == "tools" -> KoraxExpansionScreen(
                    onBack = { tab = "more" },
                    onOpenMatch = { selectedMatchId = it },
                    onOpenArticle = { selectedArticle = it }
                )
                tab == "settings" -> KoraxSettingsScreen(
                    onBack = { tab = "more" },
                    onOpenNotifications = { tab = "notifications" },
                    onManageFavorites = { tab = "favorites" }
                )
                else -> MoreScreen(
                    home = { tab = "news" },
                    favorites = { tab = "favorites" },
                    notifications = { tab = "notifications" },
                    explore = { tab = "explore" },
                    competitions = { tab = "competitions" },
                    settings = { tab = "settings" },
                    transfers = { tab = "transfers" },
                    tools = { tab = "tools" }
                )
            }
        }
    }
}

private fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

@Composable
private fun KoraxHeader(
    title: String,
    subtitle: String = "",
    refresh: (() -> Unit)? = null,
    search: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = KText, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            if (subtitle.isNotBlank()) Text(subtitle, color = KMuted, fontSize = 10.sp)
        }
        if (search != null) {
            Surface(shape = RoundedCornerShape(12.dp), color = KCard, border = BorderStroke(1.dp, KBorder)) {
                IconButton(onClick = search) { Icon(Icons.Filled.Search, contentDescription = "بحث", tint = KLime) }
            }
        }
        if (refresh != null) {
            Spacer(Modifier.width(7.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = KCard, border = BorderStroke(1.dp, KBorder)) {
                IconButton(onClick = refresh) { Icon(Icons.Filled.Refresh, contentDescription = "تحديث", tint = KLime) }
            }
        }
    }
}

@Composable
private fun BrandToolbar(onSearch: () -> Unit, onLive: () -> Unit, onDate: () -> Unit, onRefresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = KCard, border = BorderStroke(1.dp, KBorder)) {
            IconButton(onClick = onDate) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = "اختيار التاريخ", tint = KText)
            }
        }
        Surface(
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onLive),
            shape = RoundedCornerShape(12.dp), color = Color(0xFF24151A),
            border = BorderStroke(1.dp, Color(0xFF65303A))
        ) {
            Row(Modifier.padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFFF5268)))
                Spacer(Modifier.width(6.dp))
                Text("مباشر", color = Color(0xFFFF7283), fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.weight(1f))
        Surface(shape = RoundedCornerShape(12.dp), color = KCard, border = BorderStroke(1.dp, KBorder)) {
            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = "بحث شامل", tint = KLime)
            }
        }
        Surface(shape = RoundedCornerShape(12.dp), color = KCard, border = BorderStroke(1.dp, KBorder)) {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "تحديث الأخبار", tint = KText)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KoraxDatePickerDialog(
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = state.selectedDateMillis ?: return@TextButton
                    val date = Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC).toLocalDate()
                    onSelect(String.format(Locale.US, "%04d-%02d-%02d", date.year, date.monthValue, date.dayOfMonth))
                }
            ) { Text("تأكيد التاريخ", color = KLime, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = KMuted) } },
        shape = RoundedCornerShape(22.dp),
        colors = DatePickerDefaults.colors(containerColor = KCard)
    ) {
        DatePicker(
            state = state,
            title = { Text("اختر يوم المباريات", color = KText, modifier = Modifier.padding(16.dp)) },
            headline = null,
            showModeToggle = false,
            colors = DatePickerDefaults.colors(
                containerColor = KCard,
                selectedDayContainerColor = KLime,
                selectedDayContentColor = Color.Black,
                todayDateBorderColor = KLime,
                todayContentColor = KLime
            )
        )
    }
}

@Composable
private fun NewsScreen(
    onOpenLive: () -> Unit,
    onChooseDate: (String) -> Unit,
    onOpenArticle: (NewsItem) -> Unit,
    onExplore: () -> Unit
) {
    var articles by remember { mutableStateOf<List<NewsItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("الكل") }
    var selected by remember { mutableStateOf<NewsItem?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        loading = true
        error = ""
        val result = runCatching { loadNews() }
        articles = result.getOrDefault(emptyList())
        error = result.exceptionOrNull()?.message.orEmpty()
        loading = false
    }

    fun chooseMatchDate() {
        showDatePicker = true
    }

    LaunchedEffect(Unit) { refresh() }
    val followedNames = KoraxFavoriteStore.followed(context).map { it.name }.filter { it.isNotBlank() }
    val filtered = articles.filter {
        val categoryMatch = if (category == "لك") {
            followedNames.isNotEmpty() && followedNames.any { name ->
                (it.title + " " + it.summary).contains(name, ignoreCase = true)
            }
        } else matchesCategory(it, category)
        categoryMatch
    }

    Column(Modifier.fillMaxSize()) {
        BrandToolbar(
            onSearch = onExplore,
            onLive = onOpenLive,
            onDate = { chooseMatchDate() },
            onRefresh = { scope.launch { refresh() } }
        )
        if (!KoraxFavoriteStore.setupComplete(context)) {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = KCard),
                border = BorderStroke(1.dp, KBorder),
                shape = RoundedCornerShape(13.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("خصص أخبار KORAX", color = KText, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text("اختر أنديتك ودورياتك لتفعيل التنبيهات المخصصة", color = KMuted, fontSize = 9.sp)
                    }
                    TextButton(onClick = onExplore) { Text("اختيار", color = KLime, fontSize = 10.sp) }
                }
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 13.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(newsCategories) { option ->
                FilterChip(
                    selected = category == option,
                    onClick = { category = option },
                    label = { Text(option, fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = KLime, selectedLabelColor = Color.Black, labelColor = KMuted
                    )
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = KLime) }
            articles.isEmpty() -> StateCard("تعذر تحميل الأخبار", error.ifBlank { "تأكد من نشر Worker وضبط رابط API." }, "📰")
            filtered.isEmpty() -> StateCard("لا توجد أخبار في هذا القسم", "غيّر التصنيف أو كلمة البحث. لا نعرض أخبارًا ثابتة أو صفقات وهمية.", "🔎")
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("● من المصادر", color = KLime, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("${filtered.size} خبر", color = KMuted, fontSize = 10.sp)
                    }
                }
                items(filtered, key = { it.url }) { article ->
                    NewsCard(article, onOpen = { onOpenArticle(article) }, onComment = { selected = article })
                }
            }
        }
    }
    selected?.let { CommentsDialog(it) { selected = null } }
    if (showDatePicker) {
        KoraxDatePickerDialog(
            onDismiss = { showDatePicker = false },
            onSelect = { date ->
                showDatePicker = false
                onChooseDate(date)
            }
        )
    }
}

@Composable
private fun NewsCard(article: NewsItem, onOpen: () -> Unit, onComment: () -> Unit) {
    val context = LocalContext.current
    val showImages = KoraxSettingsStore.getBoolean(context, "show_images", true) &&
        !KoraxSettingsStore.getBoolean(context, "data_saver", false)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = KCard), border = BorderStroke(1.dp, KBorder)) {
        Column {
            if (showImages && !article.image.isNullOrBlank()) {
                coil.compose.AsyncImage(model = article.image, contentDescription = article.title,
                    modifier = Modifier.fillMaxWidth().height(172.dp).clickable(onClick = onOpen), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            } else Box(Modifier.fillMaxWidth().height(102.dp).background(Brush.horizontalGradient(listOf(Color(0xFF1B2508), Color(0xFF151B24)))).clickable(onClick = onOpen), contentAlignment = Alignment.Center) {
                Text("KORAX", color = KLime, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            }
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(article.source, color = KLime, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(article.date, color = KMuted, fontSize = 9.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onComment, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) { Text("تعليقات", color = KLime, fontSize = 10.sp) }
                }
                Spacer(Modifier.height(6.dp))
                Text(article.title, color = KText, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 21.sp,
                    maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable(onClick = onOpen))
                if (article.summary.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(article.summary, color = KMuted, fontSize = 10.sp, lineHeight = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(6.dp))
                Text("عرض تفاصيل الخبر ←", color = KLime, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onOpen))
            }
        }
    }
}

@Composable
private fun CommentsDialog(article: NewsItem, close: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { LocalComments(context) }
    var draft by remember(article.url) { mutableStateOf("") }
    var comments by remember(article.url) { mutableStateOf(store.read(article.url)) }
    AlertDialog(onDismissRequest = close, title = { Text("التعليقات", color = KText, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(article.title, color = KMuted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                if (comments.isEmpty()) Text("كن أول من يعلّق على الخبر.", color = KMuted, fontSize = 12.sp)
                else LazyColumn(Modifier.heightIn(max = 170.dp)) {
                    items(comments) { comment ->
                        Text(comment, color = KText, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                        Divider(color = KBorder)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = draft, onValueChange = { draft = it.take(500) }, modifier = Modifier.fillMaxWidth(), label = { Text("اكتب تعليقك") }, maxLines = 3)
                Spacer(Modifier.height(5.dp))
                Text("التعليقات في النسخة الحالية محفوظة على هذا الجهاز فقط؛ مشاركتها بين المستخدمين تحتاج حسابات وقاعدة بيانات مشتركة.", color = KMuted, fontSize = 9.sp)
            }
        },
        confirmButton = { TextButton(onClick = { if (draft.isNotBlank()) { comments = store.add(article.url, draft); draft = "" } }) { Text("إضافة", color = KLime) } },
        dismissButton = { TextButton(onClick = close) { Text("إغلاق", color = KMuted) } },
        containerColor = KCard, titleContentColor = KText, textContentColor = KText)
}

@Composable
private fun TransfersScreen() {
    var list by remember { mutableStateOf<List<TransferItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("الكل") }
    var league by rememberSaveable { mutableStateOf("الكل") }
    val scope = rememberCoroutineScope()
    suspend fun refresh() {
        loading = true
        val result = runCatching { loadTransfers() }
        list = result.getOrDefault(emptyList()); error = result.exceptionOrNull()?.message.orEmpty(); loading = false
    }
    LaunchedEffect(Unit) { refresh() }
    val leagues = listOf("الكل", "الدوري الإنجليزي", "الدوري الإسباني", "الدوري الإيطالي", "الدوري الألماني", "الدوري الفرنسي", "الدوري السعودي")
    val aliases = mapOf(
        "الدوري الإنجليزي" to listOf("Premier League", "الدوري الإنجليزي"),
        "الدوري الإسباني" to listOf("La Liga", "Primera", "الدوري الإسباني"),
        "الدوري الإيطالي" to listOf("Serie A", "الدوري الإيطالي"),
        "الدوري الألماني" to listOf("Bundesliga", "الدوري الألماني"),
        "الدوري الفرنسي" to listOf("Ligue 1", "الدوري الفرنسي"),
        "الدوري السعودي" to listOf("Saudi Pro League", "Roshn", "الدوري السعودي")
    )
    val shown = list.filter { item ->
        val typeOK = when (kind) {
            "رسمي" -> item.completed
            "إعارة" -> item.type.contains("loan", true) || item.type.contains("إعارة")
            "غير مكتمل" -> !item.completed
            else -> true
        }
        val leagueOK = league == "الكل" || aliases[league].orEmpty().any { item.league.contains(it, true) }
        typeOK && leagueOK
    }
    Column(Modifier.fillMaxSize()) {
        KoraxHeader("الانتقالات", "صفقات وإعارات وانتقالات اللاعبين", refresh = { scope.launch { refresh() } })
        LazyRow(contentPadding = PaddingValues(horizontal = 13.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf("الكل", "رسمي", "إعارة", "غير مكتمل")) { value ->
                FilterChip(selected = kind == value, onClick = { kind = value }, label = { Text(value, fontSize = 10.sp) })
            }
        }
        Spacer(Modifier.height(5.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 13.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(leagues) { value ->
                FilterChip(selected = league == value, onClick = { league = value }, label = { Text(value, fontSize = 9.sp) })
            }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = KLime) }
            error.isNotBlank() -> StateCard("الانتقالات غير متاحة", "$error\nيلزم نشر Worker وضبط KOORA_API_BASE_URL فقط؛ لا يلزم مفتاح API للمصدر الحالي.", "⇄")
            shown.isEmpty() -> StateCard("بيانات الانتقالات غير متاحة", "SportScore لا يوفر حاليًا مسارًا موثقًا للانتقالات المؤكدة. تابع أخبار الانتقالات في قسم الأخبار؛ لن نعرض صفقات غير مؤكدة كأنها رسمية.", "⇄")
            else -> LazyColumn(contentPadding = PaddingValues(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.id }) { item ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = KCard), border = BorderStroke(1.dp, KBorder)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.width(78.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (item.fromLogo.isNotBlank()) coil.compose.AsyncImage(model = item.fromLogo, contentDescription = item.from, modifier = Modifier.size(27.dp))
                                Text(item.from, color = KMuted, fontSize = 8.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("↓", color = KLime, fontWeight = FontWeight.Black)
                                if (item.toLogo.isNotBlank()) coil.compose.AsyncImage(model = item.toLogo, contentDescription = item.to, modifier = Modifier.size(27.dp))
                                Text(item.to, color = KMuted, fontSize = 8.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.player, color = KText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(item.type, color = KLime, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(item.date, color = KMuted, fontSize = 9.sp)
                                Text(item.amount, color = KMuted, fontSize = 9.sp)
                                Text(if (item.completed) "مكتمل حسب المصدر" else "غير مكتمل حسب المصدر", color = KMuted, fontSize = 9.sp)
                            }
                            if (item.playerImage.isNotBlank()) coil.compose.AsyncImage(model = item.playerImage, contentDescription = item.player,
                                modifier = Modifier.size(53.dp).clip(RoundedCornerShape(10.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                            else Box(Modifier.size(53.dp).clip(RoundedCornerShape(10.dp)).background(KCard2), contentAlignment = Alignment.Center) {
                                Text(item.player.take(1), color = KLime, fontWeight = FontWeight.Black, fontSize = 19.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchesScreen(
    initialLive: Boolean,
    selectedDate: String,
    onOpenMatch: (String) -> Unit,
    onChooseDate: (String) -> Unit,
    onSearchEntities: () -> Unit
) {
    val context = LocalContext.current
    var statusTab by rememberSaveable(selectedDate, initialLive) {
        mutableStateOf(if (initialLive) "live" else "upcoming")
    }
    var matches by remember { mutableStateOf<List<MatchItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var selectedSummary by remember { mutableStateOf<MatchItem?>(null) }
    val scope = rememberCoroutineScope()
    val sectionTitle = when (statusTab) {
        "live" -> "المباريات المباشرة"
        "finished" -> "المباريات المنتهية"
        else -> "المباريات القادمة"
    }

    suspend fun refresh() {
        loading = true
        error = ""
        val result = runCatching { loadMatches(statusTab, selectedDate) }
        matches = result.getOrDefault(emptyList())
        error = result.exceptionOrNull()?.message.orEmpty()
        loading = false
    }
    LaunchedEffect(statusTab, selectedDate) { refresh() }
    LaunchedEffect(statusTab, selectedDate) {
        if (statusTab == "live") {
            while (true) {
                delay(30_000L)
                refresh()
            }
        }
    }

    Column(Modifier.fillMaxSize().background(KBg)) {
        KoraxHeader(
            "المباريات",
            "$sectionTitle • $selectedDate",
            refresh = { scope.launch { refresh() } },
            search = onSearchEntities
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                Triple("upcoming", "▦", "القادمة"),
                Triple("live", "●", "لايف"),
                Triple("finished", "✓", "المنتهية")
            ).forEach { (key, icon, label) ->
                FilterChip(
                    selected = statusTab == key,
                    onClick = { statusTab = key },
                    modifier = Modifier.weight(1f),
                    label = { Text("$icon  $label", fontSize = 10.sp, maxLines = 1, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = KLime, selectedLabelColor = Color.Black, labelColor = KMuted
                    )
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("تاريخ المباريات", color = KMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                onChooseDate(runCatching { LocalDate.parse(selectedDate).minusDays(1).toString() }.getOrDefault(todayIso()))
            }) { Text("‹ السابق", color = KLime, fontSize = 10.sp) }
            TextButton(onClick = {
                onChooseDate(runCatching { LocalDate.parse(selectedDate).plusDays(1).toString() }.getOrDefault(todayIso()))
            }) { Text("التالي ›", color = KLime, fontSize = 10.sp) }
        }
        when {
            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = KLime)
            }
            error.isNotBlank() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                StateCard("تعذر تحميل المباريات", "$error\nحاول التحديث مرة أخرى.", "⚽")
            }
            matches.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                val emptyTitle = when (statusTab) {
                    "live" -> "لا توجد مباريات مباشرة الآن"
                    "finished" -> "لا توجد مباريات منتهية في هذا التاريخ"
                    else -> "لا توجد مباريات قادمة في هذا التاريخ"
                }
                StateCard(
                    emptyTitle,
                    "لا توجد مباراة ضمن البطولات الرئيسية لهذا الاختيار. استخدم البحث للعثور على نادٍ أو لاعب أو بطولة.",
                    "📅"
                )
            }
            else -> LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 2.dp)) {
                        Text(sectionTitle, color = KText, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.weight(1f))
                        Text(matches.size.toString() + " مباراة", color = KMuted, fontSize = 10.sp)
                    }
                }
                items(matches, key = { it.id }) { match ->
                    val context = LocalContext.current
                    val showImages = KoraxSettingsStore.getBoolean(context, "show_images", true) &&
                        !KoraxSettingsStore.getBoolean(context, "data_saver", false)
                    Card(
                        Modifier.fillMaxWidth().clickable {
                            if (match.id.matches(Regex("[A-Za-z0-9_-]{8,}"))) onOpenMatch(match.id)
                            else selectedSummary = match
                        },
                        shape = RoundedCornerShape(15.dp),
                        colors = CardDefaults.cardColors(containerColor = KCard),
                        border = BorderStroke(1.dp, KBorder)
                    ) {
                        Column(Modifier.padding(horizontal = 11.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    match.league.ifBlank { "بطولة غير محددة" },
                                    color = KLime, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(displayKickoff(match.kickoff), color = KMuted, fontSize = 9.sp)
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (showImages && match.homeLogo.isNotBlank()) {
                                        coil.compose.AsyncImage(
                                            model = match.homeLogo, contentDescription = match.home,
                                            modifier = Modifier.size(42.dp),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                        )
                                    } else Text("⚽", fontSize = 25.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        match.home, color = KText, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        match.score,
                                        color = if (statusTab == "live") Color(0xFFFF596D) else KLime,
                                        fontSize = 20.sp, fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                    Text(match.status, color = KMuted, fontSize = 9.sp)
                                }
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (showImages && match.awayLogo.isNotBlank()) {
                                        coil.compose.AsyncImage(
                                            model = match.awayLogo, contentDescription = match.away,
                                            modifier = Modifier.size(42.dp),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                        )
                                    } else Text("⚽", fontSize = 25.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        match.away, color = KText, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (match.detailsAvailable) "تفاصيل المباراة متاحة" else "البيانات الأساسية من المصدر",
                                    color = KMuted, fontSize = 8.sp, modifier = Modifier.weight(1f)
                                )
                                Text("التفاصيل ←", color = KLime, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        Text(
            "Powered by SportScore",
            color = KMuted,
            fontSize = 8.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).clickable {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sportscore.com/"))) }
            }.padding(vertical = 5.dp)
        )
    }

    selectedSummary?.let { match ->
        AlertDialog(
            onDismissRequest = { selectedSummary = null },
            title = { Text(match.home + " × " + match.away, color = KText, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("النتيجة: " + match.score, color = KLime, fontWeight = FontWeight.Bold)
                    Text("الحالة: " + match.status, color = KMuted, fontSize = 11.sp)
                    Text("الموعد: " + displayKickoff(match.kickoff), color = KMuted, fontSize = 11.sp)
                    Text("البطولة: " + match.league.ifBlank { "غير متاحة" }, color = KMuted, fontSize = 11.sp)
                    Text("لم يصل معرّف ثابت لهذه المباراة، لذلك لا يمكن فتح تفاصيلها بأمان بعد.", color = KMuted, fontSize = 10.sp)
                }
            },
            confirmButton = { TextButton(onClick = { selectedSummary = null }) { Text("إغلاق", color = KLime) } },
            containerColor = KCard
        )
    }
}

@Composable
private fun MoreScreen(home: () -> Unit, favorites: () -> Unit, notifications: () -> Unit, explore: () -> Unit, competitions: () -> Unit, settings: () -> Unit, transfers: () -> Unit, tools: () -> Unit) {
    val context = LocalContext.current
    val followedCount = KoraxFavoriteStore.all(context).size
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        KoraxHeader("المزيد", "مركز المشجع وإعدادات KORAX")
        Column(Modifier.padding(horizontal = 14.dp)) {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = KCard),
                border = BorderStroke(1.dp, KBorder)
            ) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(58.dp).clip(CircleShape).background(KCard2),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⚽", fontSize = 27.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("ملف المشجع", color = KText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        Text("المفضلة وإعدادات المتابعة محفوظة على جهازك", color = KMuted, fontSize = 10.sp, lineHeight = 15.sp)
                        Text("$followedCount عنصر في المفضلة", color = KLime, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = favorites) {
                        Text("عرض", color = KLime, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MoreQuickAction("☆", "المفضلة", favorites, Modifier.weight(1f), Color(0xFF8D72D7))
                MoreQuickAction("⇄", "أخبار الانتقالات", transfers, Modifier.weight(1f), KLime)
            }
            MoreSectionLabel("كرة القدم")
            MoreRow("الأخبار", "آخر أخبار كرة القدم من المصادر المتاحة", "⌂", home)
            MoreRow("البطولات والدوريات", "المباريات والجداول وترتيب الفرق", "🏆", competitions)
            MoreRow("استكشف", "الأندية والمنتخبات واللاعبون", "⌕", explore)
            MoreRow("مركز الإشعارات", "تنبيهات المباريات والأخبار على جهازك", "♧", notifications)
            MoreSectionLabel("التطبيق")
            MoreRow("الإعدادات", "المظهر والصور وتوفير البيانات والتحديثات", "⚙", settings)
            MoreRow("أدوات KORAX", "المقارنة والأرشيف والمساعد وفحص المصادر", "✦", tools)
            Text(
                "لا يوجد تسجيل حساب أو مزامنة سحابية للمفضلة في هذه النسخة. بيانات المتابعة الحالية محفوظة محليًا على جهازك.",
                color = KMuted, fontSize = 9.sp, lineHeight = 15.sp,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 14.dp)
            )
        }
    }
}
@Composable
private fun MoreSectionLabel(title: String) {
    Text(title, color = KMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 5.dp, top = 18.dp, bottom = 8.dp))
}
@Composable
private fun MoreQuickAction(label: String, title: String, click: () -> Unit, modifier: Modifier, tint: Color) {
    Surface(
        modifier = modifier.height(46.dp).clickable(onClick = click),
        shape = RoundedCornerShape(14.dp),
        color = KCard2,
        border = BorderStroke(1.dp, KBorder)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(label, color = tint, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(7.dp))
            Text(title, color = KText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
@Composable
private fun MoreRow(title: String, detail: String, icon: String, click: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(bottom = 9.dp).clickable(onClick = click), shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = KCard), border = BorderStroke(1.dp, KBorder)) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, color = KLime, fontSize = 22.sp, modifier = Modifier.background(KCard2, RoundedCornerShape(10.dp)).padding(9.dp))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = KText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(detail, color = KMuted, fontSize = 10.sp)
            }
            Text("←", color = KLime)
        }
    }
}
@Composable
private fun StateCard(title: String, description: String, icon: String) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = KCard), border = BorderStroke(1.dp, KBorder)) {
            Column(Modifier.padding(17.dp)) {
                Text(icon, color = KLime, fontSize = 27.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                Text(title, color = KText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(description, color = KMuted, fontSize = 11.sp, lineHeight = 17.sp)
            }
        }
    }
}
