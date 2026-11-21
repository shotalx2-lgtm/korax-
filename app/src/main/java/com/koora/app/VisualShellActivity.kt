package com.koora.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.util.Xml
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

private val Page = Color(0xFFF4F7FB)
private val Paper = Color.White
private val Navy = Color(0xFF162943)
private val Muted = Color(0xFF718198)
private val Blue = Color(0xFF2874D0)
private val Green = Color(0xFF16A77A)
private val PaleBlue = Color(0xFFEAF2FC)
private val PaleGreen = Color(0xFFE7F6F0)
private val Border = Color(0xFFE5EBF3)

class VisualShellActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(244, 247, 251)
        window.navigationBarColor = android.graphics.Color.WHITE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Blue, onPrimary = Color.White, secondary = Green, onSecondary = Color.White,
                    background = Page, onBackground = Navy, surface = Paper, onSurface = Navy, outline = Border
                )
            ) { KooraVisualApp() }
        }
    }
}

@Composable
private fun KooraVisualApp() {
    var tab by rememberSaveable { mutableStateOf("home") }
    Scaffold(
        containerColor = Page,
        bottomBar = {
            NavigationBar(containerColor = Paper) {
                val tabs = listOf(
                    Triple("home", "الرئيسية", Icons.Filled.Home),
                    Triple("matches", "المباريات", Icons.Filled.CalendarMonth),
                    Triple("fantasy", "الفانتازيا", Icons.Filled.SportsSoccer),
                    Triple("news", "الأخبار", Icons.Filled.Search),
                    Triple("more", "المزيد", Icons.Filled.Settings)
                )
                tabs.forEach { (key, label, icon) ->
                    NavigationBarItem(
                        selected = tab == key, onClick = { tab = key },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 10.sp) }, alwaysShowLabel = true
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                "home" -> HomeScreen({ tab = "news" }, { tab = "matches" }, { tab = "more" })
                "matches" -> MatchesScreen()
                "fantasy" -> FantasyScreen()
                "news" -> NewsScreen()
                "explore" -> ExploreScreen()
                "favorites" -> SimpleScreen("المفضلة", "قائمتك فارغة حاليًا", "أضف أنديتك ولاعبيك وبطولاتك المفضلة بعد توصيل دليل البيانات.", "♡")
                "notifications" -> SimpleScreen("مركز الإشعارات", "لا توجد إشعارات بعد", "سيتم تفعيل تنبيهات المباريات والأندية المفضلة بعد ربط خدمة الإشعارات.", "🔔")
                "settings" -> SettingsScreen()
                else -> MoreScreen({ tab = "explore" }, { tab = "favorites" }, { tab = "notifications" }, { tab = "settings" })
            }
        }
    }
}

@Composable
private fun Header(onNotifications: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(15.dp), color = Blue) {
            Icon(Icons.Filled.SportsSoccer, null, tint = Color.White, modifier = Modifier.padding(10.dp).size(26.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("KORAX", color = Navy, fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text("BEYOND FOOTBALL", color = Green, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        }
        Spacer(Modifier.weight(1f))
        if (onNotifications != null) {
            Surface(shape = CircleShape, color = Paper, border = BorderStroke(1.dp, Border)) {
                IconButton(onClick = onNotifications) { Icon(Icons.Filled.Notifications, "الإشعارات", tint = Navy) }
            }
        }
    }
}

@Composable
private fun HomeScreen(onNews: () -> Unit, onMatches: () -> Unit, onMore: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header(onMore)
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("أهلاً بك في عالم الكرة", color = Muted, fontSize = 13.sp)
            Text("كل اللعبة.. في مكان واحد", color = Navy, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF1D5FAE), Color(0xFF2787D4), Green)))
                    .clickable(onClick = onNews).padding(22.dp)
            ) {
                Column {
                    Surface(color = Color.White.copy(alpha = 0.18f), shape = RoundedCornerShape(30.dp)) {
                        Text("KORAX • BEYOND FOOTBALL", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("عِش تفاصيل\nكرة القدم", color = Color.White, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    Text("مباريات • أخبار • أندية • نجوم", color = Color.White.copy(alpha = .9f), fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("اكتشف آخر الأخبار  ←", color = Blue, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(11.dp), fontSize = 12.sp)
                }
                Text("⚽", fontSize = 68.sp, modifier = Modifier.align(Alignment.BottomEnd))
            }
            Spacer(Modifier.height(22.dp))
            SectionHeader("تابع كل لحظة", "استكشف") { onMore() }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureTile("المباريات", "النتائج والتفاصيل", "⚽", PaleBlue, Modifier.weight(1f), onMatches)
                FeatureTile("الأخبار", "آخر أخبار الكرة", "📰", PaleGreen, Modifier.weight(1f), onNews)
            }
            Spacer(Modifier.height(22.dp))
            SectionHeader("مركز المباريات", "عرض الكل", onMatches)
            EmptyCard("🏟️", "المباريات ستظهر هنا", "لم نضع نتائج أو مواعيد تجريبية. ستظهر البيانات بعد ربط مصدر المباريات الحقيقي.")
            Spacer(Modifier.height(20.dp))
            SectionHeader("أحدث الأخبار", "كل الأخبار", onNews)
            Card(
                Modifier.fillMaxWidth().clickable(onClick = onNews),
                shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Paper),
                border = BorderStroke(1.dp, Border)
            ) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📰", fontSize = 28.sp, modifier = Modifier.background(PaleGreen, RoundedCornerShape(13.dp)).padding(12.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("أخبار من مصادر رياضية حقيقية", color = Navy, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("العناوين وروابط المصادر متاحة في قسم الأخبار", color = Muted, fontSize = 11.sp)
                    }
                    Text("←", color = Blue, fontSize = 20.sp)
                }
            }
            Spacer(Modifier.height(26.dp))
        }
    }
}

@Composable
private fun FeatureTile(title: String, sub: String, emoji: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.height(120.dp).clickable(onClick = onClick), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Paper), border = BorderStroke(1.dp, Border)) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(emoji, fontSize = 22.sp, modifier = Modifier.background(tint, RoundedCornerShape(12.dp)).padding(8.dp))
            Column {
                Text(title, color = Navy, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(sub, color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Navy, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        Text(action, color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onClick).padding(5.dp))
    }
}

@Composable
private fun EmptyCard(icon: String, title: String, description: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Paper), border = BorderStroke(1.dp, Border)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 25.sp, modifier = Modifier.background(PaleBlue, RoundedCornerShape(15.dp)).padding(13.dp))
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Navy, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(3.dp))
                Text(description, color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun MatchesScreen() {
    var filter by rememberSaveable { mutableStateOf("اليوم") }
    Column(Modifier.fillMaxSize()) {
        Header()
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("المباريات", color = Navy, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("تابع مواعيد ونتائج المواجهات", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(15.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("مباشر", "اليوم", "قادمة", "انتهت").forEach {
                    FilterChip(selected = filter == it, onClick = { filter = it }, label = { Text(it) })
                }
            }
            Spacer(Modifier.height(14.dp))
            EmptyCard("📅", "بيانات المباريات غير مفعّلة", "التصميم جاهز، لكن النتائج والمواعيد الحقيقية لن تظهر إلا بعد توصيل خدمة بيانات المباريات.")
        }
    }
}

@Composable
private fun ExploreScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header()
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("استكشف كرة القدم", color = Navy, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
            Text("عالم الأندية واللاعبين والبطولات", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            listOf(
                Triple("الأندية والمنتخبات", "الشعارات والملفات وقوائم اللاعبين", "🏆"),
                Triple("اللاعبون", "الملفات والإحصائيات والمسيرة", "⭐"),
                Triple("البطولات والدوريات", "الترتيب والجداول والأرشيف", "🌍"),
                Triple("الملاعب والمدربون", "معلومات وتفاصيل إضافية", "🏟️")
            ).forEach { (title, desc, emoji) ->
                Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Paper), border = BorderStroke(1.dp, Border)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(emoji, fontSize = 24.sp, modifier = Modifier.background(PaleBlue, RoundedCornerShape(14.dp)).padding(12.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title, color = Navy, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(desc, color = Muted, fontSize = 11.sp)
                        }
                        Text("←", color = Blue, fontSize = 20.sp)
                    }
                }
            }
            EmptyCard("🔎", "المحتوى غير موصول بعد", "لن نعرض أسماء أو صورًا أو إحصائيات وهمية.")
        }
    }
}

@Composable
private fun MoreScreen(onExplore: () -> Unit, onFavorites: () -> Unit, onNotifications: () -> Unit, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header()
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("المزيد", color = Navy, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("إعدادات وتجربة KORAX", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(18.dp))
            MoreRow("استكشف", "الأندية واللاعبون والبطولات", "🌍", onExplore)
            MoreRow("المفضلة", "الأندية واللاعبون والبطولات التي تتابعها", "♡", onFavorites)
            MoreRow("مركز الإشعارات", "كل التنبيهات في مكان واحد", "🔔", onNotifications)
            MoreRow("الإعدادات", "تحكم في تفضيلات التطبيق", "⚙️", onSettings)
            Spacer(Modifier.height(18.dp))
            EmptyCard("✨", "KORAX • BEYOND FOOTBALL", "تجربة كرة قدم منظمة بمعلومات من مصادرها الحقيقية عند ربط الخدمات.")
        }
    }
}

@Composable
private fun MoreRow(title: String, description: String, emoji: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable(onClick = onClick), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Paper), border = BorderStroke(1.dp, Border)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 21.sp, modifier = Modifier.background(PaleBlue, RoundedCornerShape(12.dp)).padding(10.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Navy, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(description, color = Muted, fontSize = 11.sp)
            }
            Text("←", color = Blue, fontSize = 18.sp)
        }
    }
}

@Composable
private fun SimpleScreen(title: String, message: String, description: String, emoji: String) {
    Column(Modifier.fillMaxSize()) {
        Header()
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(title, color = Navy, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(17.dp))
            EmptyCard(emoji, message, description)
        }
    }
}

@Composable
private fun SettingsScreen() {
    var news by rememberSaveable { mutableStateOf(true) }
    var matches by rememberSaveable { mutableStateOf(true) }
    var quiet by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header()
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("الإعدادات", color = Navy, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("خصص تجربتك بالطريقة التي تناسبك", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(18.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Paper), border = BorderStroke(1.dp, Border)) {
                Column(Modifier.padding(16.dp)) {
                    Text("الإشعارات", color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    SettingToggle("أخبار كرة القدم", "آخر العناوين من المصادر", news) { news = it }
                    SettingToggle("تنبيهات المباريات", "الأحداث المهمة للمفضلة", matches) { matches = it }
                    SettingToggle("الوضع الهادئ", "إيقاف التنبيهات غير الضرورية", quiet) { quiet = it }
                }
            }
            Spacer(Modifier.height(14.dp))
            EmptyCard("ℹ️", "عن التطبيق", "KORAX – BEYOND FOOTBALL\nنسخة واجهة وتصميم مع أخبار RSS مباشرة. بيانات المباريات غير مفعّلة في هذه النسخة.")
        }
    }
}

@Composable
private fun SettingToggle(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Navy, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = Muted, fontSize = 10.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private data class NewsArticle(
    val title: String,
    val summary: String,
    val url: String,
    val source: String,
    val published: String,
    val imageUrl: String? = null,
    val publishedMillis: Long = 0L
)

private data class NewsState(val articles: List<NewsArticle> = emptyList(), val loading: Boolean = false)

private object NewsRepository {
    private data class Feed(val source: String, val url: String)
    private val feeds = listOf(
        Feed("اليوم السابع", "https://www.youm7.com/rss/SectionRss?SectionID=298"),
        Feed("الإخبارية", "https://alikhbariya.net/feeds/topics/football.xml"),
        Feed("أخبار Google", "https://news.google.com/rss/search?q=%D9%83%D8%B1%D8%A9+%D8%A7%D9%84%D9%82%D8%AF%D9%85&hl=ar&gl=EG&ceid=EG:ar")
    )
    private const val CACHE_NAME = "korax_arabic_news"
    private const val CACHE_KEY = "articles"

    suspend fun load(context: Context): List<NewsArticle> = coroutineScope {
        val remote = async(Dispatchers.IO) { runCatching { fetchBackend() }.getOrDefault(emptyList()) }.await()
        if (remote.isNotEmpty()) {
            saveCache(context, remote)
            return@coroutineScope remote
        }
        val articles = feeds.map { feed ->
            async(Dispatchers.IO) { runCatching { fetch(feed) }.getOrDefault(emptyList()) }
        }.awaitAll().flatten()
            .filter { isArabic(it.title) }
            .distinctBy { it.url }
            .sortedByDescending { it.publishedMillis }
            .take(60)
        if (articles.isNotEmpty()) {
            saveCache(context, articles)
            articles
        } else {
            readCache(context)
        }
    }

    private fun fetchBackend(): List<NewsArticle> {
        val base = BuildConfig.KOORA_API_BASE_URL.trim().trimEnd('/')
        if (!base.startsWith("https://")) return emptyList()
        val connection = (URL(base + "/api/news?per_page=50").openConnection() as HttpURLConnection).apply {
            connectTimeout = 7000
            readTimeout = 9000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) error("News API HTTP " + connection.responseCode)
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val array = root.optJSONArray("data") ?: JSONArray()
            return (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val title = clean(item.optString("title"))
                val link = item.optString("url")
                if (title.isBlank() || !isArabic(title) || !link.startsWith("https://")) return@mapNotNull null
                val rawDate = item.optString("publishedAt")
                NewsArticle(
                    title = title,
                    summary = clean(item.optString("summary")).take(300),
                    url = link,
                    source = item.optString("source", "مصدر رياضي"),
                    published = dateLabel(parseDate(rawDate)),
                    imageUrl = item.optString("imageUrl").takeIf { it.startsWith("https://") },
                    publishedMillis = parseDate(rawDate)
                )
            }.distinctBy { it.url }.take(60)
        } finally {
            connection.disconnect()
        }
    }

    private fun fetch(feed: Feed): List<NewsArticle> {
        val connection = (URL(feed.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 9000
            setRequestProperty("User-Agent", "KORAX-Android/0.3")
            setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*")
        }
        try {
            if (connection.responseCode !in 200..299) error("RSS HTTP " + connection.responseCode)
            connection.inputStream.use { input ->
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                parser.setInput(input, null)
                val result = mutableListOf<NewsArticle>()
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name.substringAfter(':').lowercase(Locale.ROOT) in setOf("item", "entry")) {
                        parseItem(parser, feed.source)?.let(result::add)
                    }
                    event = parser.next()
                }
                return result
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseItem(parser: XmlPullParser, source: String): NewsArticle? {
        var title = ""
        var link = ""
        var summary = ""
        var rawSummary = ""
        var date = ""
        var image = ""
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.END_TAG && parser.name.substringAfter(':').lowercase(Locale.ROOT) in setOf("item", "entry")) break
            if (event != XmlPullParser.START_TAG) continue
            when (parser.name.substringAfter(':').lowercase(Locale.ROOT)) {
                "title" -> title = parser.nextText()
                "link" -> link = parser.getAttributeValue(null, "href") ?: parser.nextText()
                "description", "summary", "encoded" -> {
                    rawSummary = parser.nextText()
                    summary = rawSummary
                    if (image.isBlank()) image = extractImage(rawSummary)
                }
                "pubdate", "published", "updated", "date" -> date = parser.nextText()
                "thumbnail", "content" -> {
                    val u = parser.getAttributeValue(null, "url")
                        ?: parser.getAttributeValue("http://search.yahoo.com/mrss/", "url")
                    if (!u.isNullOrBlank() && u.startsWith("https://")) image = u
                }
                "enclosure" -> {
                    val type = parser.getAttributeValue(null, "type").orEmpty()
                    val u = parser.getAttributeValue(null, "url").orEmpty()
                    if (type.startsWith("image") && u.startsWith("https://")) image = u
                }
            }
        }
        title = clean(title)
        summary = clean(summary)
        link = link.trim()
        if (title.isBlank() || !isArabic(title) || !link.startsWith("https://")) return null
        val millis = parseDate(date)
        return NewsArticle(title, summary.take(300), link, source, dateLabel(millis), image.takeIf { it.startsWith("https://") }, millis)
    }

    private fun extractImage(html: String): String {
        val lower = html.lowercase(Locale.ROOT)
        val marker = "src="
        val start = lower.indexOf("<img")
        if (start < 0) return ""
        val src = lower.indexOf(marker, start)
        if (src < 0) return ""
        var valueStart = src + marker.length
        while (valueStart < html.length && html[valueStart].isWhitespace()) valueStart++
        if (valueStart >= html.length) return ""
        val quote = html[valueStart]
        if (quote == '"' || quote == '\'') valueStart++
        val end = if (quote == '"' || quote == '\'') html.indexOf(quote, valueStart) else html.indexOfAny(charArrayOf(' ', '>'), valueStart)
        if (end <= valueStart) return ""
        return html.substring(valueStart, end).replace("&amp;", "&").trim()
    }

    private fun isArabic(value: String): Boolean = value.any { it in '\u0600'..'\u06FF' }

    private fun clean(text: String): String =
        Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT).toString().replace(Regex("\\s+"), " ").trim()

    private fun parseDate(value: String): Long {
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, d MMM yyyy HH:mm:ss zzz",
            "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
        )
        for (pattern in patterns) {
            try {
                val date = SimpleDateFormat(pattern, Locale.US).parse(value)
                if (date != null) return date.time
            } catch (_: Exception) { }
        }
        return 0L
    }

    private fun dateLabel(millis: Long): String =
        if (millis <= 0L) "من المصدر" else SimpleDateFormat("d MMM • h:mm a", Locale("ar")).format(Date(millis))

    private fun saveCache(context: Context, articles: List<NewsArticle>) {
        runCatching {
            val array = JSONArray()
            articles.take(60).forEach { article ->
                array.put(JSONObject()
                    .put("title", article.title)
                    .put("summary", article.summary)
                    .put("url", article.url)
                    .put("source", article.source)
                    .put("published", article.published)
                    .put("imageUrl", article.imageUrl ?: "")
                    .put("publishedMillis", article.publishedMillis))
            }
            context.getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE)
                .edit().putString(CACHE_KEY, array.toString()).apply()
        }
    }

    private fun readCache(context: Context): List<NewsArticle> = runCatching {
        val raw = context.getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE).getString(CACHE_KEY, null)
            ?: return@runCatching emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val title = item.optString("title")
            val url = item.optString("url")
            if (title.isBlank() || !url.startsWith("https://")) return@mapNotNull null
            NewsArticle(
                title, item.optString("summary"), url, item.optString("source"),
                item.optString("published"), item.optString("imageUrl").takeIf { it.startsWith("https://") },
                item.optLong("publishedMillis")
            )
        }
    }.getOrDefault(emptyList())
}

@Composable
private fun NewsScreen() {
    var state by remember { mutableStateOf(NewsState(loading = true)) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    suspend fun refresh() {
        state = NewsState(loading = true)
        val items = runCatching { NewsRepository.load(context) }.getOrDefault(emptyList())
        state = NewsState(items, loading = false)
    }
    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("أخبار كرة القدم", color = Navy, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                Text("عناوين من مصادر رياضية مباشرة", color = Muted, fontSize = 11.sp)
            }
            Surface(shape = CircleShape, color = Paper, border = BorderStroke(1.dp, Border)) {
                IconButton(onClick = { scope.launch { refresh() } }) {
                    Icon(Icons.Filled.Refresh, "تحديث الأخبار", tint = Blue)
                }
            }
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Blue)
                    Spacer(Modifier.height(12.dp))
                    Text("بنحمّل آخر الأخبار من المصادر...", color = Muted, fontSize = 12.sp)
                }
            }
            state.articles.isEmpty() -> Column(Modifier.padding(20.dp)) {
                EmptyCard("📰", "تعذر تحميل الأخبار الآن", "تحقق من اتصال الإنترنت ثم أعد المحاولة. المصادر: BBC Sport وSky Sports.")
                Spacer(Modifier.height(12.dp))
                Button(onClick = { scope.launch { refresh() } }) { Text("إعادة المحاولة") }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("● مباشر من المصادر", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("\${state.articles.size} خبر", color = Muted, fontSize = 11.sp)
                    }
                }
                items(state.articles, key = { it.url }) { article ->
                    NewsCard(article) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.url))) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsCard(article: NewsArticle, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Paper), border = BorderStroke(1.dp, Border)) {
        Column {
            if (!article.imageUrl.isNullOrBlank()) {
                coil.compose.AsyncImage(
                    model = article.imageUrl,
                    contentDescription = article.title,
                    modifier = Modifier.fillMaxWidth().height(175.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Box(Modifier.fillMaxWidth().height(130.dp).background(Brush.horizontalGradient(listOf(PaleBlue, PaleGreen))),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚽", fontSize = 38.sp)
                        Text("صورة الخبر غير متاحة من المصدر", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Column(Modifier.padding(15.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(article.source, color = Blue, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(PaleBlue, RoundedCornerShape(7.dp)).padding(horizontal = 8.dp, vertical = 5.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(article.published, color = Muted, fontSize = 10.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(article.title, color = Navy, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (article.summary.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(article.summary, color = Muted, fontSize = 11.sp, lineHeight = 17.sp,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(9.dp))
                Text("اقرأ الخبر من المصدر  ←", color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
