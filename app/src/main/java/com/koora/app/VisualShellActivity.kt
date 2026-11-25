package com.koora.app

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.scale
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
    private val notificationRoute = mutableStateOf<KoraxNotificationRoute?>(null)

    private fun updateNotificationRoute(source: Intent?) {
        val matchId = source?.getStringExtra("korax_match_id").orEmpty()
        val newsUrl = source?.getStringExtra("korax_news_url").orEmpty()
        notificationRoute.value = when {
            matchId.isNotBlank() -> KoraxNotificationRoute(matchId = matchId)
            newsUrl.isNotBlank() -> {
                val articleTitle = source?.getStringExtra("korax_news_title").orEmpty().ifBlank { "خبر كروي جديد" }
                val article = NewsItem(
                    id = newsUrl,
                    title = articleTitle,
                    summary = source?.getStringExtra("korax_news_summary").orEmpty(),
                    url = newsUrl,
                    source = source?.getStringExtra("korax_news_source").orEmpty(),
                    date = source?.getStringExtra("korax_news_date").orEmpty(),
                    image = source?.getStringExtra("korax_news_image")?.takeIf { it.startsWith("https://") }
                )
                KoraxNotificationRoute(article = article)
            }
            else -> null
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateNotificationRoute(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateNotificationRoute(intent)
        KoraxSmartAlerts.ensureChannels(this)
        KoraxSmartAlerts.setBackgroundUpdates(this, KoraxSettingsStore.getBoolean(this, "background_updates", true))
        window.statusBarColor = android.graphics.Color.rgb(0, 0, 0)
        window.navigationBarColor = android.graphics.Color.rgb(0, 0, 0)
        @Suppress("DEPRECATION") window.decorView.systemUiVisibility = 0
        setContent {
            val route by notificationRoute
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
                MaterialTheme(colorScheme = scheme) { KoraxSplash(route) }
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
private fun KoraxSplash(initialNotificationRoute: KoraxNotificationRoute?) {
    var intro by rememberSaveable { mutableStateOf(true) }
    val logoScale = remember { Animatable(0.88f) }
    val glowAlpha = remember { Animatable(0.22f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            launch { logoScale.animateTo(1f, animationSpec = tween(620)) }
            launch {
                glowAlpha.animateTo(0.60f, animationSpec = tween(850))
                glowAlpha.animateTo(0.28f, animationSpec = tween(850))
            }
        }
        delay(420L)
        intro = false
    }
    if (intro) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF121B13), Color(0xFF080C09), Color(0xFF020303)))
            ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(258.dp).clip(CircleShape)
                    .background(Color(0xFFD5FF00).copy(alpha = glowAlpha.value * 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(202.dp),
                    shape = CircleShape,
                    color = Color(0xFF0B120D),
                    border = BorderStroke(1.dp, Color(0xFFD5FF00).copy(alpha = glowAlpha.value))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(R.drawable.korax_brand),
                            contentDescription = "شعار كوراكس",
                            modifier = Modifier.size(150.dp).scale(logoScale.value),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
                }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("كرة القدم بين يديك", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(7.dp))
                Text("مباريات مباشرة  •  أخبار  •  بطولات", color = Color(0xFFD5FF00), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(19.dp),
                    color = Color(0xFFD5FF00),
                    strokeWidth = 2.dp
                )
            }
        }
    } else KoraxApp(initialNotificationRoute)
}

internal data class NewsItem(val id: String, val title: String, val summary: String, val url: String, val source: String, val date: String, val image: String?, val category: String = "football")
private data class NewsEntitySuggestion(val name: String, val kind: String, val aliases: List<String> = emptyList())
private data class KoraxNotificationRoute(val matchId: String? = null, val article: NewsItem? = null)
private data class TransferItem(val id: String, val player: String, val playerImage: String, val from: String, val fromLogo: String, val to: String, val toLogo: String, val type: String, val date: String, val amount: String, val completed: Boolean, val league: String)
private data class MatchItem(val id: String, val home: String, val away: String, val homeLogo: String, val awayLogo: String, val score: String, val status: String, val kickoff: String, val league: String, val detailsAvailable: Boolean, val sourceName: String = "", val sourceLicense: String = "", val sourceUrl: String = "")

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

internal suspend fun loadNews(): List<NewsItem> {
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


private val arabicFootballNameOverrides = mapOf(
    "arsenal" to "أرسنال", "fc arsenal" to "أرسنال",
    "manchester city" to "مانشستر سيتي", "manchester city fc" to "مانشستر سيتي",
    "manchester united" to "مانشستر يونايتد", "manchester united fc" to "مانشستر يونايتد",
    "liverpool" to "ليفربول", "liverpool fc" to "ليفربول", "fc liverpool" to "ليفربول",
    "chelsea" to "تشيلسي", "chelsea fc" to "تشيلسي",
    "tottenham" to "توتنهام", "tottenham hotspur" to "توتنهام",
    "aston villa" to "أستون فيلا", "newcastle united" to "نيوكاسل يونايتد",
    "real madrid" to "ريال مدريد", "real betis" to "ريال بيتيس",
    "barcelona" to "برشلونة", "fc barcelona" to "برشلونة",
    "atletico madrid" to "أتلتيكو مدريد", "atlético madrid" to "أتلتيكو مدريد",
    "athletic bilbao" to "أتلتيك بلباو", "villarreal" to "فياريال", "villarreal cf" to "فياريال",
    "bayern munich" to "بايرن ميونخ", "fc bayern munchen" to "بايرن ميونخ",
    "borussia dortmund" to "بوروسيا دورتموند", "bayer leverkusen" to "باير ليفركوزن",
    "bayer 04 leverkusen" to "باير ليفركوزن", "rb leipzig" to "لايبزيغ",
    "inter milan" to "إنتر ميلان", "inter mailand" to "إنتر ميلان",
    "ac milan" to "ميلان", "juventus" to "يوفنتوس", "juventus fc" to "يوفنتوس",
    "napoli" to "نابولي", "ssc napoli" to "نابولي", "roma" to "روما", "as roma" to "روما",
    "paris saint germain" to "باريس سان جيرمان", "paris st germain" to "باريس سان جيرمان", "psg" to "باريس سان جيرمان",
    "ajax" to "أياكس", "afc ajax" to "أياكس", "benfica" to "بنفيكا", "fc porto" to "بورتو",
    "sporting cp" to "سبورتينغ لشبونة", "feyenoord rotterdam" to "فينورد",
    "fenerbahce sk" to "فنربخشة", "galatasaray istanbul" to "غلطة سراي",
    "al ahly" to "الأهلي", "al-ahly" to "الأهلي", "zamalek" to "الزمالك",
    "pyramids" to "بيراميدز", "al ittihad" to "الاتحاد", "al hilal" to "الهلال",
    "al nassr" to "النصر", "al shabab" to "الشباب", "al ettifaq" to "الاتفاق",
    "premier league" to "الدوري الإنجليزي الممتاز", "english premier league" to "الدوري الإنجليزي الممتاز",
    "la liga" to "الدوري الإسباني", "spanish la liga" to "الدوري الإسباني",
    "bundesliga" to "الدوري الألماني", "german bundesliga" to "الدوري الألماني",
    "serie a" to "الدوري الإيطالي", "italian serie a" to "الدوري الإيطالي",
    "ligue 1" to "الدوري الفرنسي", "french ligue 1" to "الدوري الفرنسي",
    "saudi professional league" to "دوري روشن السعودي", "saudi arabian pro league" to "دوري روشن السعودي",
    "egyptian premier league" to "الدوري المصري الممتاز", "eredivisie" to "الدوري الهولندي",
    "uefa champions league" to "دوري أبطال أوروبا", "champions league" to "دوري أبطال أوروبا",
    "uefa europa league" to "الدوري الأوروبي", "uefa europa conference league" to "دوري المؤتمر الأوروبي",
    "english championship" to "دوري البطولة الإنجليزية", "brasileiro serie a" to "الدوري البرازيلي",
    "copa del rey" to "كأس ملك إسبانيا", "coppa italia" to "كأس إيطاليا",
    "dfb pokal" to "كأس ألمانيا", "coupe de france" to "كأس فرنسا",
    "saudi king cup" to "كأس الملك السعودي", "saudi super cup" to "كأس السوبر السعودي"
)

internal fun arabicFootballName(raw: String): String {
    val value = raw.trim()
    if (value.isBlank() || value.any { it in '\u0600'..'\u06FF' }) return value
    val key = value.lowercase(Locale.ROOT)
        .replace("ä", "a").replace("ö", "o").replace("ü", "u")
        .replace("ß", "ss")
        .replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")
    arabicFootballNameOverrides[key]?.let { return it }

    val pairs = listOf(
        "sch" to "ش", "sh" to "ش", "ch" to "تش", "th" to "ث", "ph" to "ف",
        "kh" to "خ", "gh" to "غ", "zh" to "ژ", "oo" to "و", "ee" to "ي",
        "ou" to "و", "ow" to "او", "ck" to "ك", "qu" to "ك", "tion" to "شن"
    )
    val single = mapOf(
        'a' to "ا", 'b' to "ب", 'c' to "ك", 'd' to "د", 'e' to "ي",
        'f' to "ف", 'g' to "ج", 'h' to "ه", 'i' to "ي", 'j' to "ج",
        'k' to "ك", 'l' to "ل", 'm' to "م", 'n' to "ن", 'o' to "و",
        'p' to "ب", 'q' to "ك", 'r' to "ر", 's' to "س", 't' to "ت",
        'u' to "و", 'v' to "ف", 'w' to "و", 'x' to "كس", 'y' to "ي", 'z' to "ز"
    )
    val result = StringBuilder()
    var index = 0
    while (index < value.length) {
        val remaining = value.substring(index).lowercase(Locale.ROOT)
        val pair = pairs.firstOrNull { remaining.startsWith(it.first) }
        if (pair != null) {
            result.append(pair.second)
            index += pair.first.length
        } else {
            val ch = value[index]
            result.append(single[ch.lowercaseChar()] ?: if (ch.isDigit() || ch.isWhitespace()) ch else "")
            index++
        }
    }
    return result.toString().replace(Regex("\\s+"), " ").trim().ifBlank { value }
}

private suspend fun loadMatches(mode: String, selectedDate: String): List<MatchItem> {
    val data = apiJson("/api/matches?mode=$mode&scope=main&date=$selectedDate").rows()
    return (0 until data.length()).mapNotNull { i ->
        val x = data.optJSONObject(i) ?: return@mapNotNull null
        val h = x.optJSONObject("homeTeam")
        val a = x.optJSONObject("awayTeam")
        val homeRaw = h?.optString("name_ar").orEmpty().ifBlank { h?.optString("name").orEmpty().ifBlank { x.optString("home") } }
        val awayRaw = a?.optString("name_ar").orEmpty().ifBlank { a?.optString("name").orEmpty().ifBlank { x.optString("away") } }
        val home = arabicFootballName(homeRaw)
        val away = arabicFootballName(awayRaw)
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
            arabicFootballName(x.optJSONObject("competition")?.optString("name").orEmpty().ifBlank { x.optString("competitionName") }),
            detailsAvailable = stableMatchId != null && x.optBoolean("detailAvailable", false),
            sourceName = x.optString("sourceName"),
            sourceLicense = x.optString("sourceLicense"),
            sourceUrl = x.optString("sourceUrl").takeIf { it.startsWith("https://") }.orEmpty()
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
private fun rememberKoraxNetworkAvailable(): Boolean {
    val context = LocalContext.current
    var available by remember { mutableStateOf(koraxHasValidatedInternet(context)) }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) { available = koraxHasValidatedInternet(context) }
            override fun onLost(network: android.net.Network) { available = koraxHasValidatedInternet(context) }
            override fun onCapabilitiesChanged(network: android.net.Network, capabilities: android.net.NetworkCapabilities) {
                available = capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }
    return available
}

private fun koraxHasValidatedInternet(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val network = manager.activeNetwork ?: return@runCatching false
    val capabilities = manager.getNetworkCapabilities(network) ?: return@runCatching false
    capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}.getOrDefault(false)

@Composable
private fun KoraxConnectionBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
        color = Color(0xFF231D12), shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, Color(0xFF594324))
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("◌", color = Color(0xFFFFC66D), fontSize = 15.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(8.dp))
            Text(message, color = Color(0xFFFFD89A), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun displayKickoffTime(raw: String): String {
    if (raw.isBlank()) return "—"
    return runCatching {
        DateTimeFormatter.ofPattern("hh:mm a", Locale("ar"))
            .withZone(ZoneId.systemDefault()).format(Instant.parse(raw))
    }.getOrElse { displayKickoff(raw) }
}

@Composable
private fun KoraxApp(initialNotificationRoute: KoraxNotificationRoute? = null) {
    val context = LocalContext.current
    val networkAvailable = rememberKoraxNetworkAvailable()
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
    var selectedArticle by remember { mutableStateOf(initialNotificationRoute?.article) }
    var selectedProfile by remember { mutableStateOf<KoraxFavorite?>(null) }
    var selectedMatchId by rememberSaveable { mutableStateOf(initialNotificationRoute?.matchId) }
    LaunchedEffect(initialNotificationRoute) {
        when {
            initialNotificationRoute?.matchId != null -> {
                selectedArticle = null
                selectedProfile = null
                selectedMatchId = initialNotificationRoute.matchId
                tab = "matches"
            }
            initialNotificationRoute?.article != null -> {
                selectedMatchId = null
                selectedProfile = null
                selectedArticle = initialNotificationRoute.article
                tab = "news"
            }
            else -> {
                selectedArticle = null
                selectedMatchId = null
                selectedProfile = null
                tab = "news"
            }
        }
    }
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
        if (selectedArticle == null && selectedProfile == null && selectedMatchId == null) {
            NavigationBar(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)),
                containerColor = KCard,
                tonalElevation = 10.dp
            ) {
                val tabs = listOf(
                    Triple("matches", "المباريات", Icons.Filled.CalendarMonth),
                    Triple("news", "الأخبار", Icons.Filled.Search),
                    Triple("standings", "الترتيب", Icons.Filled.Explore),
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
        Column(Modifier.fillMaxSize().padding(if (selectedArticle == null && selectedProfile == null && selectedMatchId == null) padding else PaddingValues(0.dp))) {
            if (!networkAvailable) KoraxConnectionBanner("لا يوجد اتصال بالإنترنت • قد لا تتحدث البيانات حتى يعود الاتصال")
            Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                selectedArticle != null -> KoraxNewsDetailScreen(selectedArticle!!) { selectedArticle = null }
                selectedProfile != null -> KoraxEntityProfileScreen(
                    favorite = selectedProfile!!,
                    onBack = { selectedProfile = null },
                    onOpenPlayer = { selectedProfile = it }
                )
                selectedMatchId != null -> KoraxMatchDetailPage(selectedMatchId!!) { selectedMatchId = null }
                else -> AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 16 })
                            .togetherWith(fadeOut(tween(170)) + slideOutHorizontally(tween(170)) { -it / 16 })
                    },
                    label = "koraxTabTransition"
                ) { activeTab ->
                    when {
                        activeTab == "news" -> NewsScreen(
                    onOpenLive = { matchLiveRequested = true; tab = "matches" },
                    onChooseDate = { selected -> matchDate = selected; matchLiveRequested = false; tab = "matches" },
                    onOpenArticle = { selectedArticle = it },
                    onExplore = { tab = "explore" }
                )
                        activeTab == "transfers" -> KoraxTopicPage(onBack = { tab = "more" }, onOpenArticle = { selectedArticle = it })
                        activeTab == "matches" -> MatchesScreen(
                    isOnline = networkAvailable,
                    initialLive = matchLiveRequested,
                    selectedDate = matchDate,
                    onOpenMatch = { selectedMatchId = it },
                    onChooseDate = { matchDate = it; matchLiveRequested = false },
                    onSearchEntities = { tab = "match_search" }
                )
                        activeTab == "match_search" -> KoraxFavoriteSelectionScreen(
                    isFirstRun = false,
                    onFinish = { tab = "matches" },
                    onOpenProfile = { selectedProfile = it }
                )
                        activeTab == "standings" -> KoraxStatisticsHubPage(
                    onBack = { tab = "news" },
                    onExplore = { tab = "statistics" }
                )
                        activeTab == "statistics" -> KoraxFavoriteSelectionScreen(
                    isFirstRun = false,
                    onFinish = { tab = "news" },
                    onOpenProfile = { selectedProfile = it }
                )
                        activeTab == "competitions" -> KoraxCompetitionsHubPage(
                    onBack = { tab = "more" },
                    onOpenProfile = { selectedProfile = it }
                )
                        activeTab == "explore" -> KoraxFavoriteSelectionScreen(
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
                        activeTab == "favorites" -> KoraxFavoritesScreen(
                    onOpenProfile = { selectedProfile = it },
                    onAddMore = { tab = "explore" }
                )
                        activeTab == "notifications" -> KoraxNotificationsScreen { tab = "more" }
                        activeTab == "tools" -> KoraxExpansionScreen(
                    onBack = { tab = "more" },
                    onOpenMatch = { selectedMatchId = it },
                    onOpenArticle = { selectedArticle = it }
                )
                        activeTab == "settings" -> KoraxSettingsScreen(
                    onBack = { tab = "news" },
                    onOpenNotifications = { tab = "notifications" },
                    onManageFavorites = { tab = "statistics" }
                )
                        else -> MoreScreen(
                    home = { tab = "news" },
                    favorites = { tab = "favorites" },
                    notifications = { tab = "notifications" },
                    explore = { tab = "explore" },
                    competitions = { tab = "competitions" },
                    settings = { tab = "settings" },
                    transfers = { tab = "transfers" },
                    tools = { tab = "tools" },
                    statistics = { tab = "statistics" }
                )
                    }
                }
            }
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
    var entityQuery by rememberSaveable { mutableStateOf("") }
    var entitySuggestions by remember { mutableStateOf<List<NewsEntitySuggestion>>(emptyList()) }
    var selectedEntity by remember { mutableStateOf<NewsEntitySuggestion?>(null) }
    var searchingEntities by remember { mutableStateOf(false) }
    var entitySearchMessage by remember { mutableStateOf("") }
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

    fun searchEntities() {
        val needle = entityQuery.trim()
        if (needle.length < 2 || searchingEntities) return
        searchingEntities = true
        entitySearchMessage = ""
        scope.launch {
            val result = runCatching {
                val payload = apiJson("/api/entities/search?search=" + Uri.encode(needle) + "&per_page=20")
                val rows = payload.optJSONArray("data")
                (0 until (rows?.length() ?: 0)).mapNotNull { index ->
                    val row = rows?.optJSONObject(index) ?: return@mapNotNull null
                    val name = row.optString("name").ifBlank {
                        row.optString("name_ar").ifBlank { row.optString("canonical_name").ifBlank { row.optString("title") } }
                    }.trim()
                    if (name.isBlank()) return@mapNotNull null
                    val kind = row.optString("koraxKind").ifBlank { row.optString("kind").ifBlank { row.optString("type") } }
                    val aliasesArray = row.optJSONArray("aliases")
                    val aliases = (0 until (aliasesArray?.length() ?: 0)).map { aliasesArray!!.optString(it) }.filter { it.isNotBlank() }
                    NewsEntitySuggestion(name, kind, aliases)
                }.distinctBy { it.name.lowercase(Locale.ROOT) }.take(15)
            }
            entitySuggestions = result.getOrDefault(emptyList())
            entitySearchMessage = when {
                result.isFailure -> "تعذر البحث الآن. جرّب مرة أخرى."
                entitySuggestions.isEmpty() -> "لم يظهر تطابق في فهرس الأندية والبطولات. جرّب الاسم بالعربية أو الإنجليزية."
                else -> ""
            }
            searchingEntities = false
        }
    }

    LaunchedEffect(Unit) { refresh() }
    val followedNames = KoraxFavoriteStore.followed(context).map { it.name }.filter { it.isNotBlank() }
    val categoryFiltered = articles.filter { item ->
        if (category == "لك") {
            followedNames.isNotEmpty() && followedNames.any { name ->
                (item.title + " " + item.summary + " " + item.source).contains(name, ignoreCase = true)
            }
        } else matchesCategory(item, category)
    }
    val entityTerms = selectedEntity?.let { (listOf(it.name) + it.aliases).filter { value -> value.length >= 2 } }.orEmpty()
    val entityFiltered = if (selectedEntity == null) emptyList() else articles.filter { item ->
        val articleText = item.title + " " + item.summary + " " + item.source
        entityTerms.any { term -> articleText.contains(term, ignoreCase = true) }
    }
    val filtered = if (selectedEntity != null) entityFiltered else categoryFiltered
    val visibleArticles = if (filtered.isNotEmpty()) filtered else articles.take(30)
    val showingFallback = articles.isNotEmpty() && filtered.isEmpty()

    Column(Modifier.fillMaxSize()) {
        BrandToolbar(
            onSearch = onExplore,
            onLive = onOpenLive,
            onDate = { showDatePicker = true },
            onRefresh = { scope.launch { refresh() } }
        )

        Card(
            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 5.dp),
            colors = CardDefaults.cardColors(containerColor = KCard),
            border = BorderStroke(1.dp, KBorder),
            shape = RoundedCornerShape(15.dp)
        ) {
            Column(Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("اختر ما تريد متابعة أخباره", color = KText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("دوري • بطولة • نادٍ • منتخب • لاعب", color = KMuted, fontSize = 9.sp)
                    }
                    TextButton(onClick = {
                        selectedEntity = null
                        entityQuery = ""
                        entitySuggestions = emptyList()
                        entitySearchMessage = ""
                    }) { Text("الكل", color = KLime, fontSize = 10.sp) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = entityQuery,
                        onValueChange = {
                            entityQuery = it
                            entitySuggestions = emptyList()
                            entitySearchMessage = ""
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("ابحث عن الأهلي أو صلاح أو الدوري الإنجليزي", fontSize = 10.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                    )
                    IconButton(onClick = { searchEntities() }, enabled = !searchingEntities) {
                        if (searchingEntities) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = KLime)
                        else Icon(Icons.Filled.Search, contentDescription = "بحث عن عنصر", tint = KLime)
                    }
                }
                if (selectedEntity != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("الأخبار المختارة:", color = KMuted, fontSize = 9.sp)
                        Spacer(Modifier.width(5.dp))
                        Text(selectedEntity!!.name, color = KLime, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { selectedEntity = null }) { Text("إلغاء", color = KMuted, fontSize = 9.sp) }
                    }
                }
                if (entitySuggestions.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(entitySuggestions) { suggestion ->
                            val typeLabel = when (suggestion.kind) {
                                "league" -> "دوري"
                                "tournament" -> "بطولة"
                                "club" -> "نادي"
                                "national" -> "منتخب"
                                "player" -> "لاعب"
                                "coach" -> "مدرب"
                                else -> "كرة القدم"
                            }
                            FilterChip(
                                selected = selectedEntity == suggestion,
                                onClick = {
                                    selectedEntity = suggestion
                                    category = "الكل"
                                    entityQuery = suggestion.name
                                    entitySuggestions = emptyList()
                                },
                                label = { Text("$typeLabel · " + suggestion.name, fontSize = 9.sp, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = KLime,
                                    selectedLabelColor = Color.Black,
                                    labelColor = KMuted
                                )
                            )
                        }
                    }
                } else if (entitySearchMessage.isNotBlank()) {
                    Text(entitySearchMessage, color = KMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        LazyRow(contentPadding = PaddingValues(horizontal = 13.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(newsCategories) { option ->
                FilterChip(
                    selected = category == option && selectedEntity == null,
                    onClick = { category = option; selectedEntity = null },
                    label = { Text(option, fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = KLime, selectedLabelColor = Color.Black, labelColor = KMuted
                    )
                )
            }
        }
        Spacer(Modifier.height(7.dp))

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = KLime)
            }
            articles.isEmpty() -> StateCard("تعذر تحميل الأخبار", error.ifBlank { "تحقق من اتصال الإنترنت ومصادر الأخبار." }, "📰")
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                item {
                    if (showingFallback) {
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = KCard),
                            border = BorderStroke(1.dp, KBorder),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                if (selectedEntity != null)
                                    "لم يظهر اسم «" + selectedEntity!!.name + "» في عناوين التحديث الحالي؛ نعرض أحدث أخبار كرة القدم من المصادر نفسها بدل ترك الصفحة فارغة."
                                else
                                    "لا تتوفر نتائج مطابقة لهذا التصنيف في التحديث الحالي؛ نعرض أحدث أخبار كرة القدم من المصادر نفسها.",
                                color = KMuted, fontSize = 9.sp, lineHeight = 15.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("● أخبار من المصادر", color = KLime, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("${visibleArticles.size} خبر", color = KMuted, fontSize = 10.sp)
                    }
                }
                items(visibleArticles, key = { it.url }) { article ->
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
                Text("أخبار كرة القدم", color = KLime, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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


private fun matchCacheKey(status: String, date: String) = "matches_" + status + "_" + date

private fun readMatchCache(context: Context, status: String, date: String): List<MatchItem> = runCatching {
    val prefs = context.getSharedPreferences("korax_match_cache_v1", Context.MODE_PRIVATE)
    val array = org.json.JSONArray(prefs.getString(matchCacheKey(status, date), "[]"))
    (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val id = item.optString("id")
        val home = item.optString("home")
        val away = item.optString("away")
        if (id.isBlank() || home.isBlank() || away.isBlank()) return@mapNotNull null
        MatchItem(
            id = id, home = arabicFootballName(home), away = arabicFootballName(away),
            homeLogo = item.optString("homeLogo"), awayLogo = item.optString("awayLogo"),
            score = item.optString("score", "VS"), status = item.optString("status"),
            kickoff = item.optString("kickoff"), league = arabicFootballName(item.optString("league")),
            detailsAvailable = item.optBoolean("detailsAvailable"),
            sourceName = item.optString("sourceName"), sourceLicense = item.optString("sourceLicense"),
            sourceUrl = item.optString("sourceUrl")
        )
    }
}.getOrDefault(emptyList())

private fun saveMatchCache(context: Context, status: String, date: String, matches: List<MatchItem>) {
    val array = org.json.JSONArray()
    matches.forEach { match ->
        array.put(org.json.JSONObject()
            .put("id", match.id).put("home", match.home).put("away", match.away)
            .put("homeLogo", match.homeLogo).put("awayLogo", match.awayLogo)
            .put("score", match.score).put("status", match.status).put("kickoff", match.kickoff)
            .put("league", match.league).put("detailsAvailable", match.detailsAvailable)
            .put("sourceName", match.sourceName).put("sourceLicense", match.sourceLicense)
            .put("sourceUrl", match.sourceUrl))
    }
    context.getSharedPreferences("korax_match_cache_v1", Context.MODE_PRIVATE).edit()
        .putString(matchCacheKey(status, date), array.toString())
        .putLong(matchCacheKey(status, date) + "_saved_at", System.currentTimeMillis())
        .apply()
}

@Composable
private fun MatchesScreen(
    isOnline: Boolean,
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
    var slowConnection by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedSummary by remember { mutableStateOf<MatchItem?>(null) }
    val scope = rememberCoroutineScope()
    val sectionTitle = when (statusTab) {
        "live" -> "المباريات المباشرة"
        "finished" -> "المباريات المنتهية"
        else -> "المباريات القادمة"
    }

    suspend fun refresh() {
        if (matches.isEmpty()) loading = true
        error = ""
        if (!isOnline) {
            matches = readMatchCache(context, statusTab, selectedDate)
            error = "offline"
            slowConnection = false
            loading = false
            return
        }
        val startedAt = System.currentTimeMillis()
        val result = runCatching { loadMatches(statusTab, selectedDate) }
        slowConnection = System.currentTimeMillis() - startedAt >= 5_000L
        result.onSuccess { fresh ->
            matches = fresh
            saveMatchCache(context, statusTab, selectedDate, fresh)
        }.onFailure {
            matches = readMatchCache(context, statusTab, selectedDate)
            error = it.message.orEmpty()
            slowConnection = true
        }
        loading = false
    }
    LaunchedEffect(statusTab, selectedDate, isOnline) { refresh() }
    LaunchedEffect(statusTab, selectedDate, isOnline) {
        if (statusTab == "live" && isOnline) {
            while (true) {
                delay(30_000L)
                refresh()
            }
        }
    }

    Column(Modifier.fillMaxSize().background(KBg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("المباريات", color = KText, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text("مواعيد اليوم والنتائج المباشرة", color = KMuted, fontSize = 9.sp)
            }
            IconButton(onClick = {
                onChooseDate(runCatching { LocalDate.parse(selectedDate).minusDays(1).toString() }.getOrDefault(todayIso()))
            }) { Text("‹", color = KMuted, fontSize = 28.sp) }
            IconButton(onClick = { showDatePicker = true }) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = "اختيار التاريخ", tint = KText)
            }
            IconButton(onClick = {
                onChooseDate(runCatching { LocalDate.parse(selectedDate).plusDays(1).toString() }.getOrDefault(todayIso()))
            }) { Text("›", color = KMuted, fontSize = 28.sp) }
            IconButton(onClick = onSearchEntities) { Icon(Icons.Filled.Search, contentDescription = "بحث", tint = KText) }
            IconButton(onClick = { scope.launch { refresh() } }) { Icon(Icons.Filled.Refresh, contentDescription = "تحديث", tint = KLime) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$sectionTitle • $selectedDate", color = KMuted, fontSize = 9.sp, modifier = Modifier.weight(1f))
            Text(
                "البيانات: SportScore", color = KMuted.copy(alpha = .78f), fontSize = 7.sp,
                modifier = Modifier.clickable {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sportscore.com/"))) }
                }
            )
        }
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
        if (isOnline && error.isNotBlank() && matches.isNotEmpty()) {
            KoraxConnectionBanner("تعذر التحديث • نعرض آخر نسخة محفوظة من المباريات")
        } else if (slowConnection) {
            KoraxConnectionBanner("الاتصال بطيء • قد يستغرق تحديث المباريات بعض الوقت")
        }
        when {
            loading && matches.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = KLime)
            }
            matches.isEmpty() && error.isNotBlank() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                StateCard(
                    if (!isOnline) "أنت غير متصل بالإنترنت" else "تعذر تحميل المباريات",
                    if (!isOnline) "عند عودة الاتصال ستُحمّل المباريات تلقائيًا. اضغط زر التحديث بعد الاتصال."
                    else "تعذر الوصول إلى خدمة المباريات. حاول مرة أخرى بعد قليل.",
                    "⚽"
                )
            }
            matches.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
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
                items(
                    matches.groupBy { it.league.ifBlank { "بطولة غير محددة" } }.entries.toList(),
                    key = { it.key }
                ) { leagueGroup ->
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Surface(Modifier.fillMaxWidth(), color = KCard2, shape = RoundedCornerShape(13.dp)) {
                            Row(Modifier.padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(leagueGroup.key, color = KText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("•••", color = KMuted, fontSize = 10.sp)
                            }
                        }
                        leagueGroup.value.forEach { match ->
                    val context = LocalContext.current
                    val showImages = KoraxSettingsStore.getBoolean(context, "show_images", true) &&
                        !KoraxSettingsStore.getBoolean(context, "data_saver", false)
                    Card(
                        Modifier.fillMaxWidth().clickable {
                            if (match.detailsAvailable && match.id.matches(Regex("[A-Za-z0-9_-]{8,}"))) onOpenMatch(match.id)
                            else selectedSummary = match
                        },
                        shape = RoundedCornerShape(15.dp),
                        colors = CardDefaults.cardColors(containerColor = KCard),
                        border = BorderStroke(1.dp, KBorder)
                    ) {
                        Column(Modifier.padding(horizontal = 11.dp, vertical = 10.dp)) {
                            Spacer(Modifier.height(4.dp))
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
                                        if (statusTab == "upcoming") displayKickoffTime(match.kickoff) else match.score,
                                        color = if (statusTab == "live") Color(0xFFFF596D) else KLime,
                                        fontSize = if (statusTab == "upcoming") 14.sp else 20.sp,
                                        fontWeight = FontWeight.Black,
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
                                    if (match.sourceName.isNotBlank()) "المصدر: ${match.sourceName} • ${match.sourceLicense}" else if (match.detailsAvailable) "تفاصيل المباراة متاحة" else "البيانات الأساسية من المصدر",
                                    color = KMuted, fontSize = 8.sp, modifier = Modifier.weight(1f).then(
                                        if (match.sourceName.isNotBlank() && match.sourceUrl.isNotBlank()) Modifier.clickable {
                                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(match.sourceUrl))) }
                                        } else Modifier
                                    )
                                )
                                Text(if (match.detailsAvailable) "التفاصيل ←" else "الملخص ←", color = KLime, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                        }
                    }
                }
            }
        }
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
                    Text(if (match.sourceName.isNotBlank()) "المصدر الإضافي يوفر الموعد أو النتيجة فقط، ولا يوفر أحداثًا أو تشكيلات لهذه المباراة." else "لم يصل معرّف ثابت لهذه المباراة، لذلك لا يمكن فتح تفاصيلها بأمان بعد.", color = KMuted, fontSize = 10.sp)
                    if (match.sourceName.isNotBlank() && match.sourceUrl.isNotBlank()) TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(match.sourceUrl))) }
                    }) { Text("فتح مصدر البيانات", color = KLime, fontSize = 10.sp) }
                }
            },
            confirmButton = { TextButton(onClick = { selectedSummary = null }) { Text("إغلاق", color = KLime) } },
            containerColor = KCard
        )
    }
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
private fun MoreScreen(home: () -> Unit, favorites: () -> Unit, notifications: () -> Unit, explore: () -> Unit, competitions: () -> Unit, settings: () -> Unit, transfers: () -> Unit, tools: () -> Unit, statistics: () -> Unit) {
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
            MoreRow("مركز الإحصائيات", "جداول الترتيب والهدافون وصنّاع الأهداف وإحصاءات الأندية", "📊", statistics)
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
