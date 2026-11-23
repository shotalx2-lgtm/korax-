package com.koora.app

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
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

private val KBg = Color(0xFF07090D)
private val KCard = Color(0xFF11161E)
private val KCard2 = Color(0xFF1A202A)
private val KLime = Color(0xFFD5FF00)
private val KText = Color(0xFFF4F6F8)
private val KMuted = Color(0xFF929AA6)
private val KBorder = Color(0xFF27303B)

class VisualShellActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(7, 9, 13)
        window.navigationBarColor = android.graphics.Color.rgb(7, 9, 13)
        @Suppress("DEPRECATION") window.decorView.systemUiVisibility = 0
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(colorScheme = darkColorScheme(
                    primary = KLime, onPrimary = Color.Black, secondary = KLime,
                    background = KBg, onBackground = KText, surface = KCard,
                    onSurface = KText, outline = KBorder
                )) { KoraxSplash() }
            }
        }
    }
}

@Composable
private fun KoraxSplash() {
    var intro by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(1050); intro = false }
    if (intro) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF1B2508), Color(0xFF0D1118), Color(0xFF050609)))), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("KORA", color = Color.White, fontSize = 49.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Text("X", color = KLime, fontSize = 55.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(8.dp))
                Text("BEYOND FOOTBALL", color = KMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Spacer(Modifier.height(25.dp))
                Box(Modifier.width(54.dp).height(3.dp).clip(CircleShape).background(KLime))
                Spacer(Modifier.height(18.dp))
                Text("NEWS  •  TRANSFERS  •  FANTASY", color = Color(0xFFBEC5CE), fontSize = 9.sp, letterSpacing = 1.sp)
            }
        }
    } else KoraxApp()
}

private data class NewsItem(val id: String, val title: String, val summary: String, val url: String, val source: String, val date: String, val image: String?, val category: String = "football")
private data class TransferItem(val id: String, val player: String, val playerImage: String, val from: String, val fromLogo: String, val to: String, val toLogo: String, val type: String, val date: String, val amount: String, val completed: Boolean, val league: String)
private data class MatchItem(val id: String, val home: String, val away: String, val homeLogo: String, val awayLogo: String, val score: String, val status: String, val kickoff: String, val league: String)

private fun apiBase() = BuildConfig.KOORA_API_BASE_URL.trim().trimEnd('/')
private suspend fun apiJson(path: String): JSONObject = withContext(Dispatchers.IO) {
    val base = apiBase()
    require(base.startsWith("https://")) { "خدمة البيانات غير مربوطة بعد. اضبط KOORA_API_BASE_URL أولًا." }
    val c = (URL(base + path).openConnection() as HttpURLConnection).apply {
        connectTimeout = 9000; readTimeout = 13000; setRequestProperty("Accept", "application/json")
    }
    try {
        if (c.responseCode !in 200..299) error("الخادم أعاد HTTP " + c.responseCode)
        JSONObject(c.inputStream.bufferedReader().use { it.readText() })
    } finally { c.disconnect() }
}
private fun JSONObject.rows() = optJSONArray("data") ?: JSONArray()
private fun JSONObject.https(name: String) = optString(name).takeIf { it.startsWith("https://") }.orEmpty()

private suspend fun loadNews(): List<NewsItem> {
    val data = apiJson("/api/news?per_page=60").rows()
    return (0 until data.length()).mapNotNull { i ->
        val x = data.optJSONObject(i) ?: return@mapNotNull null
        val url = x.optString("url")
        val title = x.optString("title").trim()
        if (title.isBlank() || !url.startsWith("https://")) return@mapNotNull null
        NewsItem(x.optString("id", url), title, x.optString("summary"), url,
            x.optString("source", "مصدر رياضي"), x.optString("publishedAt"),
            x.https("imageUrl").takeIf { it.isNotBlank() }, x.optString("category", "football"))
    }.distinctBy { it.url }
}

private suspend fun loadTransfers(): List<TransferItem> {
    val data = apiJson("/api/transfers?per_page=50&page=1").rows()
    return (0 until data.length()).mapNotNull { i ->
        val x = data.optJSONObject(i) ?: return@mapNotNull null
        val p = x.optJSONObject("player"); val f = x.optJSONObject("fromTeam"); val t = x.optJSONObject("toTeam")
        val name = p?.optString("name").orEmpty().ifBlank { x.optString("playerName") }
        if (name.isBlank()) return@mapNotNull null
        val amount = x.opt("amount")
        TransferItem(x.optString("id", i.toString()), name, p?.https("image_path").orEmpty(),
            f?.optString("name").orEmpty().ifBlank { "غير محدد" }, f?.https("image_path").orEmpty(),
            t?.optString("name").orEmpty().ifBlank { "غير محدد" }, t?.https("image_path").orEmpty(),
            x.optString("typeName").ifBlank { "انتقال" }, x.optString("date"),
            if (amount == null || amount == JSONObject.NULL || amount.toString() == "0") "القيمة غير معلنة" else amount.toString(),
            x.optBoolean("completed"), x.optJSONObject("league")?.optString("name").orEmpty().ifBlank { x.optString("leagueName") })
    }
}

private suspend fun loadMatches(live: Boolean, selectedDate: String): List<MatchItem> {
    val data = apiJson(if (live) "/api/matches?live=1" else "/api/matches?date=$selectedDate").rows()
    return (0 until data.length()).mapNotNull { i ->
        val x = data.optJSONObject(i) ?: return@mapNotNull null
        val h = x.optJSONObject("homeTeam"); val a = x.optJSONObject("awayTeam")
        val home = h?.optString("name").orEmpty().ifBlank { x.optString("home") }
        val away = a?.optString("name").orEmpty().ifBlank { x.optString("away") }
        if (home.isBlank() || away.isBlank()) return@mapNotNull null
        val hs = x.opt("homeScore"); val ascore = x.opt("awayScore")
        val score = if (hs != null && ascore != null && hs != JSONObject.NULL && ascore != JSONObject.NULL) "$hs - $ascore" else "VS"
        MatchItem(x.optString("id", i.toString()), home, away,
            h?.https("crest").orEmpty().ifBlank { h?.https("logoUrl").orEmpty() },
            a?.https("crest").orEmpty().ifBlank { a?.https("logoUrl").orEmpty() },
            score, x.optString("status", "غير متاحة"), x.optString("startTime"),
            x.optJSONObject("competition")?.optString("name").orEmpty().ifBlank { x.optString("competitionName") })
    }
}

private val newsCategories = listOf("الكل", "العالمية", "العربية", "الدوري الإنجليزي", "الدوري الإسباني", "الدوري الإيطالي", "الدوري الألماني", "الدوري الفرنسي", "الدوري السعودي", "الانتقالات", "الإصابات", "الفانتازي")
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
        "الفانتازي" -> has("فانتازي", "fantasy", "fantasy premier league", "الكابتن", "تشكيلة الجولة", "دبل جيم ويك", "وايلد كارد")
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
    var tab by rememberSaveable { mutableStateOf("news") }
    var matchLiveRequested by rememberSaveable { mutableStateOf(false) }
    var matchDate by rememberSaveable { mutableStateOf(todayIso()) }
    Scaffold(containerColor = KBg, bottomBar = {
        NavigationBar(containerColor = KCard) {
            val tabs = listOf(
                Triple("matches", "المباريات", Icons.Filled.CalendarMonth),
                Triple("news", "الأخبار", Icons.Filled.Search),
                Triple("fantasy", "الفانتازي", Icons.Filled.SportsSoccer),
                Triple("transfers", "الانتقالات", Icons.Filled.SwapHoriz),
                Triple("more", "المزيد", Icons.Filled.Settings)
            )
            tabs.forEach { (key, label, icon) ->
                NavigationBarItem(selected = tab == key, onClick = { tab = key },
                    icon = { Icon(icon, contentDescription = label) }, label = { Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                    alwaysShowLabel = true, colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = KLime, selectedTextColor = KLime, indicatorColor = KLime.copy(alpha = .12f),
                        unselectedIconColor = KMuted, unselectedTextColor = KMuted
                    ))
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                "news" -> NewsScreen(
                    onOpenLive = { matchLiveRequested = true; tab = "matches" },
                    onChooseDate = { selected -> matchDate = selected; matchLiveRequested = false; tab = "matches" },
                    onOpenFantasy = { tab = "fantasy" }
                )
                "transfers" -> TransfersScreen()
                "matches" -> MatchesScreen(initialLive = matchLiveRequested, selectedDate = matchDate)
                "fantasy" -> FantasyScreen()
                "favorites" -> StateCard("المفضلة", "تابع أنديتك ولاعبيك لإضافتهم هنا. مزامنة المفضلة بين الأجهزة تحتاج حسابًا سحابيًا.", "♡")
                "notifications" -> StateCard("الإشعارات", "ستظهر هنا تنبيهات المباريات والأخبار والفانتازي بعد تفعيل خدمة الإشعارات.", "♧")
                else -> MoreScreen({ tab = "news" }, { tab = "favorites" }, { tab = "notifications" })
            }
        }
    }
}

private fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

@Composable
private fun KoraxHeader(title: String, subtitle: String = "", refresh: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("KORA", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
                Text("X", color = KLime, fontSize = 23.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(9.dp))
                Text(title, color = KText, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            }
            if (subtitle.isNotBlank()) Text(subtitle, color = KMuted, fontSize = 10.sp)
        }
        if (refresh != null) Surface(shape = CircleShape, color = KCard2, border = BorderStroke(1.dp, KBorder)) {
            IconButton(onClick = refresh) { Icon(Icons.Filled.Refresh, "تحديث", tint = KLime) }
        }
    }
}

@Composable
private fun BrandToolbar(onSearch: () -> Unit, onLive: () -> Unit, onDate: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("KORA", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
            Text("X", color = KLime, fontSize = 28.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.weight(1f))
        Surface(shape = RoundedCornerShape(13.dp), color = KCard2, border = BorderStroke(1.dp, KBorder)) {
            IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "بحث", tint = KLime) }
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            modifier = Modifier.clip(RoundedCornerShape(13.dp)).clickable(onClick = onLive),
            shape = RoundedCornerShape(13.dp), color = Color(0xFF28151A),
            border = BorderStroke(1.dp, Color(0xFF65303A))
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFFF5268)))
                Spacer(Modifier.width(6.dp))
                Text("LIVE", color = Color(0xFFFF7283), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(shape = RoundedCornerShape(13.dp), color = KCard2, border = BorderStroke(1.dp, KBorder)) {
            IconButton(onClick = onDate) { Icon(Icons.Filled.CalendarMonth, "اختيار التاريخ", tint = KText) }
        }
    }
}

@Composable
private fun FantasyShortcut(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF172006)),
        border = BorderStroke(1.dp, Color(0xFF465717))
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(KLime), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.SportsSoccer, null, tint = Color.Black)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("KORAX XI", color = KLime, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)
                Text("ادخل إلى فريقك وسوق اللاعبين والنقاط", color = Color(0xFFD0D5C3), fontSize = 10.sp)
            }
            Text("دخول  ←", color = KLime, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NewsScreen(
    onOpenLive: () -> Unit,
    onChooseDate: (String) -> Unit,
    onOpenFantasy: () -> Unit
) {
    var articles by remember { mutableStateOf<List<NewsItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("الكل") }
    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<NewsItem?>(null) }
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
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, day ->
                onChooseDate(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    LaunchedEffect(Unit) { refresh() }
    val filtered = articles.filter {
        matchesCategory(it, category) && (query.isBlank() || (it.title + " " + it.summary).contains(query, true))
    }

    Column(Modifier.fillMaxSize()) {
        BrandToolbar(
            onSearch = { searchExpanded = !searchExpanded },
            onLive = onOpenLive,
            onDate = { chooseMatchDate() }
        )
        if (searchExpanded) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                placeholder = { Text("ابحث عن لاعب أو نادٍ أو خبر...", color = KMuted, fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = KLime) },
                trailingIcon = {
                    TextButton(onClick = { query = ""; searchExpanded = false }) {
                        Text("إغلاق", color = KLime, fontSize = 10.sp)
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(7.dp))
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
        FantasyShortcut(onClick = onOpenFantasy)
        Spacer(Modifier.height(8.dp))
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
                    NewsCard(article, onOpen = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.url))) }
                    }, onComment = { selected = article })
                }
            }
        }
    }
    selected?.let { CommentsDialog(it) { selected = null } }
}

@Composable
private fun NewsCard(article: NewsItem, onOpen: () -> Unit, onComment: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = KCard), border = BorderStroke(1.dp, KBorder)) {
        Column {
            if (!article.image.isNullOrBlank()) {
                coil.compose.AsyncImage(model = article.image, contentDescription = article.title,
                    modifier = Modifier.fillMaxWidth().height(172.dp).clickable(onClick = onOpen), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            } else Box(Modifier.fillMaxWidth().height(102.dp).background(Brush.horizontalGradient(listOf(Color(0xFF1B2508), Color(0xFF151B24)))).clickable(onClick = onOpen), contentAlignment = Alignment.Center) {
                Text("KORAX X", color = KLime, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
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
                Text("اقرأ الخبر من المصدر ←", color = KLime, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onOpen))
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
private fun MatchesScreen(initialLive: Boolean, selectedDate: String) {
    var live by rememberSaveable { mutableStateOf(initialLive) }
    var matches by remember { mutableStateOf<List<MatchItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    suspend fun refresh() {
        loading = true
        val result = runCatching { loadMatches(live, selectedDate) }
        matches = result.getOrDefault(emptyList()); error = result.exceptionOrNull()?.message.orEmpty(); loading = false
    }
    LaunchedEffect(live, selectedDate) { refresh() }
    Column(Modifier.fillMaxSize()) {
        KoraxHeader("المباريات", "التاريخ: $selectedDate", refresh = { scope.launch { refresh() } })
        Row(Modifier.padding(horizontal = 13.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FilterChip(selected = !live, onClick = { live = false }, label = { Text("حسب التاريخ") })
            FilterChip(selected = live, onClick = { live = true }, label = { Text("LIVE") })
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = KLime) }
            error.isNotBlank() -> StateCard("المباريات غير متاحة", "$error\nتحقق من إعداد مزود البيانات.", "⚽")
            matches.isEmpty() -> StateCard(if (live) "لا توجد مباريات مباشرة" else "لا توجد مباريات اليوم", "لم تصل بيانات لهذا الاختيار من المصدر المتصل.", "📅")
            else -> LazyColumn(contentPadding = PaddingValues(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(matches, key = { it.id }) { match ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = KCard), border = BorderStroke(1.dp, KBorder)) {
                        Column(Modifier.padding(11.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(match.league.ifBlank { "بطولة" }, color = KLime, fontSize = 10.sp, modifier = Modifier.weight(1f))
                                Text(match.status, color = KMuted, fontSize = 9.sp)
                            }
                            Spacer(Modifier.height(9.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (match.homeLogo.isNotBlank()) coil.compose.AsyncImage(model = match.homeLogo, contentDescription = match.home, modifier = Modifier.size(30.dp))
                                    Text(match.home, color = KText, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Text(match.score, color = KLime, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp))
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (match.awayLogo.isNotBlank()) coil.compose.AsyncImage(model = match.awayLogo, contentDescription = match.away, modifier = Modifier.size(30.dp))
                                    Text(match.away, color = KText, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (match.kickoff.isNotBlank()) Text(match.kickoff, color = KMuted, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 7.dp))
                        }
                    }
                }
            }
        }
        Text(
            "Powered by SportScore",
            color = KMuted,
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).clickable {
                uriHandler.openUri("https://sportscore.com/")
            }.padding(vertical = 7.dp)
        )
    }
}


@Composable
private fun AccountCard() {
    val context = LocalContext.current
    val auth: FirebaseAuth? = remember {
        if (FirebaseApp.getApps(context).isNotEmpty()) runCatching { FirebaseAuth.getInstance() }.getOrNull()
        else null
    }
    var currentUser by remember(auth) { mutableStateOf(auth?.currentUser) }
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var creatingAccount by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    DisposableEffect(auth) {
        if (auth != null) {
            val listener = FirebaseAuth.AuthStateListener { currentUser = it.currentUser }
            auth.addAuthStateListener(listener)
            onDispose { auth.removeAuthStateListener(listener) }
        } else onDispose { }
    }

    fun runEmailAuth(create: Boolean) {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            feedback = "يلزم إعداد Firebase أولًا."
            return
        }
        scope.launch {
            busy = true
            feedback = ""
            runCatching {
                val cleanEmail = email.trim()
                check(cleanEmail.contains("@")) { "اكتب بريدًا إلكترونيًا صحيحًا." }
                check(password.length >= 6) { "كلمة المرور يجب أن تكون 6 أحرف على الأقل." }
                if (create) firebaseAuth.createUserWithEmailAndPassword(cleanEmail, password).await()
                else firebaseAuth.signInWithEmailAndPassword(cleanEmail, password).await()
            }.onSuccess {
                feedback = if (create) "تم إنشاء الحساب وتسجيل الدخول." else "تم تسجيل الدخول بنجاح."
            }.onFailure { error ->
                feedback = error.localizedMessage ?: "تعذر إكمال تسجيل الدخول."
            }
            busy = false
        }
    }

    fun runGoogleAuth() {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            feedback = "يلزم إعداد Firebase أولًا."
            return
        }
        scope.launch {
            busy = true
            feedback = ""
            runCatching {
                val clientIdResource = context.resources.getIdentifier(
                    "default_web_client_id", "string", context.packageName
                )
                check(clientIdResource != 0) {
                    "إعداد OAuth غير موجود. أضف google-services.json بعد تفعيل Google في Firebase."
                }
                val googleOption = GetGoogleIdOption.Builder()
                    .setServerClientId(context.getString(clientIdResource))
                    .setFilterByAuthorizedAccounts(false)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleOption)
                    .build()
                val result = CredentialManager.create(context).getCredential(context, request)
                val credential = result.credential
                check(
                    credential is CustomCredential &&
                        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) { "لم يتم استلام بيانات اعتماد Google صالحة." }
                val googleToken = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(googleToken.idToken, null)
                firebaseAuth.signInWithCredential(firebaseCredential).await()
            }.onSuccess {
                feedback = "تم تسجيل الدخول بنجاح."
            }.onFailure { error ->
                feedback = error.localizedMessage ?: "تعذر تسجيل الدخول. تحقق من إعداد Firebase."
            }
            busy = false
        }
    }

    Card(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = KCard),
        border = BorderStroke(1.dp, KBorder)
    ) {
        Column(Modifier.fillMaxWidth().padding(15.dp)) {
            Text("حساب KORAX", color = KText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            when {
                currentUser != null -> {
                    Text(currentUser?.displayName ?: currentUser?.email.orEmpty(), color = KLime, fontSize = 12.sp)
                    Text(currentUser?.email.orEmpty(), color = KMuted, fontSize = 10.sp)
                    Button(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                auth?.signOut()
                                runCatching {
                                    CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
                                }
                                feedback = "تم تسجيل الخروج من حساب KORAX."
                                busy = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = KCard2, contentColor = KText)
                    ) { Text(if (busy) "جارٍ التنفيذ..." else "تسجيل الخروج") }
                }
                auth == null -> {
                    Text(
                        "تسجيل البريد وGoogle جاهز برمجيًا، لكن يلزم إضافة إعداد Firebase الخاص بتطبيق KORAX وتفعيل طريقتَي الدخول من لوحة Firebase.",
                        color = KMuted, fontSize = 11.sp, lineHeight = 17.sp
                    )
                }
                else -> {
                    Text("سجّل الدخول لحفظ حسابك وربط بياناتك.", color = KMuted, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = email, onValueChange = { email = it },
                        label = { Text("البريد الإلكتروني") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(7.dp))
                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        label = { Text("كلمة المرور") }, singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        enabled = !busy,
                        onClick = { runEmailAuth(creatingAccount) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = KLime, contentColor = Color.Black)
                    ) {
                        Text(if (busy) "جارٍ التنفيذ..." else if (creatingAccount) "إنشاء حساب بالبريد" else "تسجيل الدخول بالبريد")
                    }
                    TextButton(
                        enabled = !busy,
                        onClick = { creatingAccount = !creatingAccount; feedback = "" },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(if (creatingAccount) "لديك حساب؟ سجّل الدخول" else "ليس لديك حساب؟ أنشئ حسابًا")
                    }
                    Button(
                        enabled = !busy,
                        onClick = { runGoogleAuth() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = KCard2, contentColor = KText)
                    ) { Text(if (busy) "جارٍ تسجيل الدخول..." else "تسجيل الدخول باستخدام Google") }
                }
            }
            if (feedback.isNotBlank()) {
                Text(feedback, color = if (currentUser != null) KLime else KMuted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun MoreScreen(home: () -> Unit, favorites: () -> Unit, notifications: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        KoraxHeader("المزيد", "إعدادات وتجربتك في KORAX")
        Column(Modifier.padding(horizontal = 14.dp)) {
            AccountCard()
            MoreRow("الأخبار", "العودة إلى مركز الأخبار", "⌂", home)
            MoreRow("المفضلة", "الأندية واللاعبون الذين تتابعهم", "♡", favorites)
            MoreRow("الإشعارات", "مركز تنبيهات المباريات والأخبار", "♧", notifications)
            StateCard("KORAX X", "نسخة Android متصلة بمصادر RSS وواجهات البيانات التي يتم إعدادها على الخادم.", "X")
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
