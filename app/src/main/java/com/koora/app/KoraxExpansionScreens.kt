package com.koora.app

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val XBg get() = KoraxThemePalette.background
private val XCard get() = KoraxThemePalette.card
private val XCard2 get() = KoraxThemePalette.card2
private val XText get() = KoraxThemePalette.text
private val XMuted get() = KoraxThemePalette.muted
private val XBorder get() = KoraxThemePalette.border
private val XAccent get() = KoraxThemePalette.accent

private fun JSONObject.readText(vararg keys: String): String {
    for (key in keys) {
        val value = opt(key)
        when (value) {
            null, JSONObject.NULL -> Unit
            is JSONObject -> {
                val nested = value.optString("name").ifBlank { value.optString("title") }
                if (nested.isNotBlank()) return nested
            }
            is JSONArray -> if (value.length() > 0) return value.toString()
            else -> value.toString().takeIf { it.isNotBlank() && it != "null" }?.let { return it }
        }
    }
    return ""
}

private fun JSONObject.readImage(vararg keys: String): String {
    val aliases = listOf(
        "imageUrl", "image_path", "imagePath", "image_url", "photo_url", "photo",
        "thumbnail", "logoUrl", "logo", "logo_url", "logo_path",
        "crest", "crestUrl", "crest_url", "badge", "emblem"
    )
    return (keys.toList() + aliases).distinct()
        .firstNotNullOfOrNull { key -> optString(key).takeIf { it.startsWith("https://") } }
        .orEmpty()
}

private fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")

internal object KoraxMatchWatchStore {
    private const val PREFS = "korax_match_watch_v1"
    private const val KEY = "watched_matches"

    fun isWatching(context: Context, matchId: String): Boolean {
        if (matchId.isBlank()) return false
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet())?.contains(matchId) == true
    }

    fun toggle(context: Context, matchId: String): Boolean {
        if (matchId.isBlank()) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = prefs.getStringSet(KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        val watching = if (next.contains(matchId)) { next.remove(matchId); false }
            else { next.add(matchId); true }
        prefs.edit().putStringSet(KEY, next).apply()
        return watching
    }
}

internal object KoraxOfflineStore {
    private const val PREFS = "korax_offline_articles_v1"
    private const val KEY = "articles"

    fun all(context: Context): List<NewsItem> = runCatching {
        val array = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        (0 until array.length()).mapNotNull { index ->
            val row = array.optJSONObject(index) ?: return@mapNotNull null
            val url = row.optString("url")
            val title = row.optString("title")
            val summary = row.optString("summary")
            val source = row.optString("source")
            if (url.isBlank() || title.isBlank()) return@mapNotNull null
            if (!FootballNewsFilter.isFootballNews(title, summary, source)) return@mapNotNull null
            NewsItem(
                row.optString("id", url), title, summary, url,
                source, row.optString("date"),
                row.optString("image").takeIf { it.startsWith("https://") },
                row.optString("category", "football")
            )
        }
    }.getOrDefault(emptyList())

    fun setSaved(context: Context, article: NewsItem, saved: Boolean) {
        val next = all(context).filterNot { it.url == article.url }.toMutableList()
        if (saved) next.add(article)
        val array = JSONArray()
        next.takeLast(100).forEach { item ->
            array.put(JSONObject()
                .put("id", item.id)
                .put("title", item.title)
                .put("summary", item.summary)
                .put("url", item.url)
                .put("source", item.source)
                .put("date", item.date)
                .put("image", item.image ?: "")
                .put("category", item.category))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }

    fun remove(context: Context, url: String) {
        val array = JSONArray()
        all(context).filterNot { it.url == url }.forEach { item ->
            array.put(JSONObject()
                .put("id", item.id).put("title", item.title).put("summary", item.summary)
                .put("url", item.url).put("source", item.source).put("date", item.date)
                .put("image", item.image ?: "").put("category", item.category))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}

private data class ToolEntity(
    val id: String,
    val slug: String,
    val name: String,
    val subtitle: String,
    val image: String,
    val raw: JSONObject
)

private data class ToolMatch(
    val id: String,
    val home: String,
    val away: String,
    val homeImage: String,
    val awayImage: String,
    val score: String,
    val status: String,
    val time: String,
    val competition: String
)

@Composable
internal fun KoraxExpansionScreen(
    onBack: () -> Unit,
    onOpenMatch: (String) -> Unit,
    onOpenArticle: (NewsItem) -> Unit
) {
    var page by rememberSaveable { mutableStateOf("home") }
    val back: () -> Unit = { page = "home" }
    when (page) {
        "home" -> ToolHome(
            onBack = onBack,
            onCompare = { page = "compare" },
            onAssistant = { page = "assistant" },
            onArchive = { page = "archive" },
            onHealth = { page = "health" },
            onOffline = { page = "offline" },
            onTopics = { page = "topics" },
            onTrends = { page = "trends" },
            onLive = { page = "live" },
            onBackup = { page = "backup" }
        )
        "compare" -> KoraxComparePage(onBack = back)
        "assistant" -> KoraxAssistantPage(onBack = back, onOpenArticle = onOpenArticle)
        "archive" -> KoraxArchivePage(onBack = back, onOpenMatch = onOpenMatch)
        "health" -> KoraxHealthPage(onBack = back)
        "offline" -> KoraxOfflinePage(onBack = back, onOpenArticle = onOpenArticle)
        "topics" -> KoraxTopicPage(onBack = back, onOpenArticle = onOpenArticle)
        "trends" -> KoraxTrendsPage(onBack = back, onOpenArticle = onOpenArticle)
        "live" -> KoraxLiveMonitorPage(onBack = back, onOpenMatch = onOpenMatch)
        "backup" -> KoraxBackupPage(onBack = back)
        else -> ToolHome(
            onBack = onBack, onCompare = { page = "compare" }, onAssistant = { page = "assistant" },
            onArchive = { page = "archive" }, onHealth = { page = "health" }, onOffline = { page = "offline" },
            onTopics = { page = "topics" }, onTrends = { page = "trends" }, onLive = { page = "live" },
            onBackup = { page = "backup" }
        )
    }
}

@Composable
private fun ToolHeader(title: String, subtitle: String, onBack: () -> Unit, onRefresh: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "رجوع", tint = XAccent) }
        Column(Modifier.weight(1f)) {
            Text(title, color = XText, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Text(subtitle, color = XMuted, fontSize = 9.sp)
        }
        if (onRefresh != null) IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "تحديث", tint = XAccent) }
    }
}

@Composable
private fun ToolHome(
    onBack: () -> Unit,
    onCompare: () -> Unit,
    onAssistant: () -> Unit,
    onArchive: () -> Unit,
    onHealth: () -> Unit,
    onOffline: () -> Unit,
    onTopics: () -> Unit,
    onTrends: () -> Unit,
    onLive: () -> Unit,
    onBackup: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(XBg)) {
        ToolHeader("مختبر KORAX", "أدوات إضافية تعتمد على RSS وSportScore الحاليين", onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item { ToolEntry("مركز المباريات المتزامنة", "متابعة كل المباريات المباشرة وفتح تفاصيل اللقاء", "⚽", onLive) }
            item { ToolEntry("أرشيف ونتائج حسب التاريخ", "اختيار تاريخ سابق ومراجعة النتائج المتاحة من SportScore", "📅", onArchive) }
            item { ToolEntry("مقارنة اللاعبين والأندية", "مقارنة الحقول التي يرجعها المصدر دون افتراض أرقام ناقصة", "⚖️", onCompare) }
            item { ToolEntry("مساعد البحث الكروي", "بحث موحد في أخبار RSS وملفات اللاعبين والأندية والبطولات", "🔎", onAssistant) }
            item { ToolEntry("الإصابات والانتقالات", "تجميع الأخبار ذات الصلة من موجزات RSS الحالية", "📰", onTopics) }
            item { ToolEntry("الأكثر تكرارًا في الأخبار", "موضوعات متكررة داخل الأخبار المحمّلة، وليس تصويتًا اجتماعيًا", "📈", onTrends) }
            item { ToolEntry("حالة مصادر البيانات", "فحص اتصال Worker والأخبار والبطولات والمباريات", "🛡️", onHealth) }
            item { ToolEntry("القراءة دون اتصال", "قراءة ملخصات الأخبار التي حفظتها داخل التطبيق", "📚", onOffline) }
            item { ToolEntry("نسخة احتياطية محلية", "تصدير المفضلة والإعدادات والتعليقات والمحفوظات", "💾", onBackup) }
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = XCard),
                    border = BorderStroke(1.dp, XBorder),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(13.dp)) {
                        Text("مصادر البيانات والميزات المتاحة", color = XAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "KORAX يعمل دون تسجيل دخول ودون لعبة فانتازيا. المفضلة والتعليقات والحفظ والنسخ الاحتياطي محلية على جهازك. تنبيهات الخلفية يستخدم لها Android WorkManager وقد تتأخر بسبب إعدادات البطارية والاتصال. التشكيلات والإحصائيات والتفاصيل تظهر فقط عندما يوفرها SportScore؛ ولا ينشئ التطبيق بيانات غير متاحة.",
                            color = XMuted, fontSize = 10.sp, lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolEntry(title: String, detail: String, icon: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = XCard),
        border = BorderStroke(1.dp, XBorder),
        shape = RoundedCornerShape(15.dp)
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(XCard2), contentAlignment = Alignment.Center) {
                Text(icon, fontSize = 21.sp)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = XText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(detail, color = XMuted, fontSize = 9.sp, lineHeight = 14.sp)
            }
            Text("←", color = XAccent, fontSize = 18.sp)
        }
    }
}

@Composable
private fun KoraxComparePage(onBack: () -> Unit) {
    var kind by rememberSaveable { mutableStateOf("players") }
    var leftQuery by rememberSaveable { mutableStateOf("") }
    var rightQuery by rememberSaveable { mutableStateOf("") }
    var leftOptions by remember { mutableStateOf<List<ToolEntity>>(emptyList()) }
    var rightOptions by remember { mutableStateOf<List<ToolEntity>>(emptyList()) }
    var left by remember { mutableStateOf<ToolEntity?>(null) }
    var right by remember { mutableStateOf<ToolEntity?>(null) }
    var leftProfile by remember { mutableStateOf<JSONObject?>(null) }
    var rightProfile by remember { mutableStateOf<JSONObject?>(null) }
    var busy by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun search(query: String): List<ToolEntity> {
        if (query.trim().length < 2) return emptyList()
        val endpoint = if (kind == "players") "players" else "teams"
        val payload = apiJson("/api/" + endpoint + "?search=" + query.trim().urlEncoded())
        val rows = payload.optJSONArray("data") ?: JSONArray()
        return (0 until rows.length()).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            val name = row.readText("name", "common_name", "display_name", "title")
            val slug = row.readText("slug", "url_slug", "player_slug", "team_slug", "id")
            if (name.isBlank() || slug.isBlank()) return@mapNotNull null
            ToolEntity(
                row.readText("id").ifBlank { slug }, slug, name,
                row.readText("position", "nationality", "country", "type"),
                row.readImage("imageUrl", "image_path", "image_url", "photo_url", "logoUrl"),
                row
            )
        }
    }

    suspend fun loadProfile(entity: ToolEntity): JSONObject? {
        val endpoint = if (kind == "players") "players" else "teams"
        return runCatching { apiJson("/api/" + endpoint + "/" + entity.slug.urlEncoded()).optJSONObject("data") }.getOrNull()
    }

    Column(Modifier.fillMaxSize().background(XBg)) {
        ToolHeader("مقارنة", "اختر لاعبين أو ناديين من نتائج SportScore", onBack)
        Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FilterChip(selected = kind == "players", onClick = {
                kind = "players"; left = null; right = null; leftProfile = null; rightProfile = null
                leftOptions = emptyList(); rightOptions = emptyList()
            }, label = { Text("اللاعبون") })
            FilterChip(selected = kind == "teams", onClick = {
                kind = "teams"; left = null; right = null; leftProfile = null; rightProfile = null
                leftOptions = emptyList(); rightOptions = emptyList()
            }, label = { Text("الأندية والمنتخبات") })
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
            OutlinedTextField(leftQuery, { leftQuery = it }, Modifier.fillMaxWidth(), label = { Text("الطرف الأول") }, singleLine = true)
            Button(
                onClick = { scope.launch {
                    busy = "left"; error = ""
                    val result = runCatching { search(leftQuery) }
                    leftOptions = result.getOrDefault(emptyList())
                    error = result.exceptionOrNull()?.message.orEmpty()
                    busy = ""
                } },
                enabled = leftQuery.trim().length >= 2 && busy.isBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                colors = ButtonDefaults.buttonColors(containerColor = XAccent, contentColor = Color.Black)
            ) { Text(if (busy == "left") "جارٍ البحث..." else "ابحث عن الطرف الأول") }
            leftOptions.take(8).forEach { entity ->
                EntityChoice(entity, left?.id == entity.id) {
                    left = entity; leftOptions = emptyList(); leftProfile = null
                    scope.launch { leftProfile = loadProfile(entity) }
                }
            }
            OutlinedTextField(rightQuery, { rightQuery = it }, Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text("الطرف الثاني") }, singleLine = true)
            Button(
                onClick = { scope.launch {
                    busy = "right"; error = ""
                    val result = runCatching { search(rightQuery) }
                    rightOptions = result.getOrDefault(emptyList())
                    error = result.exceptionOrNull()?.message.orEmpty()
                    busy = ""
                } },
                enabled = rightQuery.trim().length >= 2 && busy.isBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                colors = ButtonDefaults.buttonColors(containerColor = XAccent, contentColor = Color.Black)
            ) { Text(if (busy == "right") "جارٍ البحث..." else "ابحث عن الطرف الثاني") }
            rightOptions.take(8).forEach { entity ->
                EntityChoice(entity, right?.id == entity.id) {
                    right = entity; rightOptions = emptyList(); rightProfile = null
                    scope.launch { rightProfile = loadProfile(entity) }
                }
            }
            if (error.isNotBlank()) Text(error, color = Color(0xFFFF7373), fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
            if (left != null || right != null) {
                Spacer(Modifier.height(15.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EntitySummaryCard(left, Modifier.weight(1f))
                    EntitySummaryCard(right, Modifier.weight(1f))
                }
            }
            if (left != null && right != null) {
                Spacer(Modifier.height(15.dp))
                Text("المقارنة بالأرقام المتاحة", color = XText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                val fields = if (kind == "players") {
                    listOf(
                        "المركز" to listOf("position", "position_name", "detailed_position"),
                        "الجنسية" to listOf("nationality", "country"),
                        "العمر" to listOf("age"),
                        "الطول" to listOf("height"),
                        "الوزن" to listOf("weight"),
                        "رقم القميص" to listOf("shirt_number", "jersey_number"),
                        "النادي" to listOf("team", "team_name")
                    )
                } else {
                    listOf(
                        "الدولة" to listOf("country", "country_name"),
                        "الملعب" to listOf("stadium", "venue"),
                        "سنة التأسيس" to listOf("founded", "foundation"),
                        "عدد اللاعبين" to listOf("players_count", "squad_size")
                    )
                }
                val a = leftProfile ?: left!!.raw
                val b = rightProfile ?: right!!.raw
                fields.forEach { (label, keys) ->
                    val va = a.readText(*keys.toTypedArray()).ifBlank { left!!.raw.readText(*keys.toTypedArray()) }
                    val vb = b.readText(*keys.toTypedArray()).ifBlank { right!!.raw.readText(*keys.toTypedArray()) }
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(va.ifBlank { "غير متاح" }, color = XText, fontSize = 10.sp, modifier = Modifier.weight(1f), maxLines = 2)
                        Text(label, color = XAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(85.dp))
                        Text(vb.ifBlank { "غير متاح" }, color = XText, fontSize = 10.sp, modifier = Modifier.weight(1f), maxLines = 2)
                    }
                    Divider(color = XBorder)
                }
                Text("المقارنة تعرض الحقول التي أعادها المصدر فقط، ولا تعتبر القيم غير المتاحة صفرًا.", color = XMuted, fontSize = 9.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 9.dp))
            }
        }
    }
}

@Composable
private fun EntityChoice(entity: ToolEntity, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 5.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) XCard2 else XCard).clickable(onClick = onClick).padding(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (entity.image.isNotBlank()) coil.compose.AsyncImage(entity.image, entity.name, Modifier.size(30.dp), contentScale = ContentScale.Crop)
        else Box(Modifier.size(30.dp).background(XCard2, CircleShape), contentAlignment = Alignment.Center) {
            Text(entity.name.take(1), color = XAccent, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(entity.name, color = XText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            if (entity.subtitle.isNotBlank()) Text(entity.subtitle, color = XMuted, fontSize = 9.sp)
        }
        Text("اختيار", color = XAccent, fontSize = 9.sp)
    }
}

@Composable
private fun EntitySummaryCard(entity: ToolEntity?, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = XCard), border = BorderStroke(1.dp, XBorder), shape = RoundedCornerShape(13.dp)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (entity?.image?.isNotBlank() == true) coil.compose.AsyncImage(entity.image, entity.name, Modifier.size(55.dp), contentScale = ContentScale.Fit)
            else Box(Modifier.size(55.dp).background(XCard2, CircleShape), contentAlignment = Alignment.Center) {
                Text(entity?.name?.take(1) ?: "?", color = XAccent, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
            Spacer(Modifier.height(7.dp))
            Text(entity?.name ?: "لم يتم الاختيار", color = XText, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun KoraxArchivePage(onBack: () -> Unit, onOpenMatch: (String) -> Unit) {
    var selectedDate by rememberSaveable { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var matches by remember { mutableStateOf<List<ToolMatch>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        loading = true; error = ""
        val result = runCatching {
            val rows = apiJson("/api/matches?date=" + selectedDate).rows()
            (0 until rows.length()).mapNotNull { i -> rows.optJSONObject(i)?.toToolMatch() }
        }
        matches = result.getOrDefault(emptyList())
        error = result.exceptionOrNull()?.message.orEmpty()
        loading = false
    }
    LaunchedEffect(selectedDate) { refresh() }

    Column(Modifier.fillMaxSize().background(XBg)) {
        ToolHeader("أرشيف المباريات", "نتائج ومواعيد التاريخ المختار من SportScore", onBack, onRefresh = { scope.launch { refresh() } })
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(selectedDate, { selectedDate = it.take(10) }, Modifier.weight(1f), label = { Text("التاريخ YYYY-MM-DD") }, singleLine = true)
            Spacer(Modifier.width(6.dp))
            Column {
                TextButton(onClick = { selectedDate = shiftDate(selectedDate, -1) }) { Text("اليوم السابق", color = XAccent, fontSize = 9.sp) }
                TextButton(onClick = { selectedDate = shiftDate(selectedDate, 1) }) { Text("اليوم التالي", color = XAccent, fontSize = 9.sp) }
            }
        }
        when {
            loading -> BusyPanel("تحميل المباريات...")
            error.isNotBlank() -> InfoPanel("تعذر تحميل الأرشيف", error)
            matches.isEmpty() -> InfoPanel("لا توجد مباريات لهذا التاريخ", "قد لا يوفر المصدر مباريات في اليوم المحدد.")
            else -> LazyColumn(contentPadding = PaddingValues(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(matches, key = { it.id }) { match -> ToolMatchCard(match) { onOpenMatch(match.id) } }
            }
        }
    }
}

private fun shiftDate(raw: String, days: Int): String {
    val calendar = Calendar.getInstance()
    val parsed = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(raw) }.getOrNull()
    if (parsed != null) calendar.time = parsed
    calendar.add(Calendar.DAY_OF_YEAR, days)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
}

private fun JSONObject.toToolMatch(): ToolMatch? {
    val homeObj = optJSONObject("homeTeam")
    val awayObj = optJSONObject("awayTeam")
    val home = homeObj?.optString("name").orEmpty().ifBlank { optString("home") }
    val away = awayObj?.optString("name").orEmpty().ifBlank { optString("away") }
    if (home.isBlank() || away.isBlank()) return null
    val hs = opt("homeScore"); val ascore = opt("awayScore")
    val score = if (hs != null && hs != JSONObject.NULL && ascore != null && ascore != JSONObject.NULL) hs.toString() + " - " + ascore.toString() else "VS"
    val id = optString("id").ifBlank { home + "-" + away + "-" + optString("startTime") }
    return ToolMatch(
        id, home, away,
        homeObj?.readImage("crest", "logoUrl", "imageUrl", "image_path", "image_url", "logo", "logo_url", "badge").orEmpty(),
        awayObj?.readImage("crest", "logoUrl", "imageUrl", "image_path", "image_url", "logo", "logo_url", "badge").orEmpty(),
        score, readText("status").ifBlank { "الحالة غير متاحة" },
        readText("startTime"), optJSONObject("competition")?.optString("name").orEmpty().ifBlank { optString("competitionName") }
    )
}

@Composable
private fun ToolMatchCard(match: ToolMatch, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = XCard), border = BorderStroke(1.dp, XBorder), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(11.dp)) {
            Row {
                Text(match.competition.ifBlank { "بطولة" }, color = XAccent, fontSize = 9.sp, modifier = Modifier.weight(1f))
                Text(match.status, color = XMuted, fontSize = 9.sp)
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(match.home, color = XText, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(1f), maxLines = 2)
                Text(match.score, color = XAccent, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text(match.away, color = XText, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(1f), maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
            if (match.time.isNotBlank()) Text(match.time, color = XMuted, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
            Text("تفاصيل المباراة ←", color = XAccent, fontSize = 9.sp, modifier = Modifier.align(Alignment.End).padding(top = 5.dp))
        }
    }
}

@Composable
private fun KoraxLiveMonitorPage(onBack: () -> Unit, onOpenMatch: (String) -> Unit) {
    var matches by remember { mutableStateOf<List<ToolMatch>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    suspend fun refresh() {
        loading = true
        val result = runCatching {
            val rows = apiJson("/api/matches?live=1").rows()
            (0 until rows.length()).mapNotNull { i -> rows.optJSONObject(i)?.toToolMatch() }
        }
        matches = result.getOrDefault(emptyList())
        error = result.exceptionOrNull()?.message.orEmpty()
        loading = false
    }
    LaunchedEffect(Unit) { refresh() }
    Column(Modifier.fillMaxSize().background(XBg)) {
        ToolHeader("المباريات المتزامنة", "قائمة المباريات المباشرة من SportScore", onBack, onRefresh = { scope.launch { refresh() } })
        when {
            loading -> BusyPanel("تحميل المباريات المباشرة...")
            error.isNotBlank() -> InfoPanel("تعذر الاتصال", error)
            matches.isEmpty() -> InfoPanel("لا توجد مباريات مباشرة الآن", "سيظهر المحتوى عند وجود مباريات مباشرة في المصدر.")
            else -> LazyColumn(contentPadding = PaddingValues(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(matches, key = { it.id }) { match -> ToolMatchCard(match) { onOpenMatch(match.id) } }
            }
        }
    }
}

@Composable
internal fun KoraxMatchDetailPage(matchId: String, onBack: () -> Unit) {
    var match by remember(matchId) { mutableStateOf<JSONObject?>(null) }
    var loading by remember(matchId) { mutableStateOf(true) }
    var error by remember(matchId) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    suspend fun refresh() {
        loading = true; error = ""
        val result = runCatching { apiJson("/api/matches/" + matchId.urlEncoded()).optJSONObject("data") }
        match = result.getOrNull()
        error = result.exceptionOrNull()?.message.orEmpty()
        loading = false
    }
    LaunchedEffect(matchId) { refresh() }
    Column(Modifier.fillMaxSize().background(XBg)) {
        ToolHeader("تفاصيل المباراة", "الأحداث والتشكيلات والإحصائيات المتاحة", onBack, onRefresh = { scope.launch { refresh() } })
        when {
            loading -> BusyPanel("تحميل تفاصيل المباراة...")
            match == null -> InfoPanel("تفاصيل المباراة غير متاحة", error.ifBlank { "لم يوفر SportScore تفاصيل إضافية لهذا اللقاء." })
            else -> MatchDetailContent(match!!)
        }
    }
}

@Composable
private fun MatchDetailContent(match: JSONObject) {
    val home = match.optJSONObject("homeTeam")
    val away = match.optJSONObject("awayTeam")
    val homeName = home?.optString("name").orEmpty().ifBlank { match.optString("home") }
    val awayName = away?.optString("name").orEmpty().ifBlank { match.optString("away") }
    val homeId = home?.optString("id").orEmpty()
    val awayId = away?.optString("id").orEmpty()
    val homeLogo = home?.readImage("crest", "logoUrl", "imageUrl", "image_path", "image_url", "logo", "logo_url", "badge").orEmpty()
    val awayLogo = away?.readImage("crest", "logoUrl", "imageUrl", "image_path", "image_url", "logo", "logo_url", "badge").orEmpty()
    val competition = match.optJSONObject("competition")
    val competitionName = competition?.optString("name").orEmpty().ifBlank { match.optString("competitionName") }
    val competitionLogo = competition?.readImage("imageUrl", "image_path", "image_url", "logo", "logo_url").orEmpty()
    val score = if (match.opt("homeScore") != null && match.opt("homeScore") != JSONObject.NULL &&
        match.opt("awayScore") != null && match.opt("awayScore") != JSONObject.NULL)
        match.optString("homeScore") + " - " + match.optString("awayScore") else "VS"
    val events = match.optJSONArray("events") ?: JSONArray()
    val lineups = match.optJSONArray("lineups") ?: JSONArray()
    val stats = match.optJSONArray("statistics") ?: JSONArray()
    val context = LocalContext.current
    val matchId = match.optString("id")
    var watching by remember(matchId) { mutableStateOf(KoraxMatchWatchStore.isWatching(context, matchId)) }
    var detailTab by rememberSaveable(matchId) { mutableStateOf("events") }

    fun belongsTo(row: JSONObject, side: String, teamId: String, teamName: String): Boolean {
        val rowSide = row.readText("side")
        if (rowSide == side) return true
        val rowTeam = row.optJSONObject("team")
        val rowId = row.readText("team_id").ifBlank { rowTeam?.optString("id").orEmpty() }
        val rowName = rowTeam?.optString("name").orEmpty().ifBlank { row.readText("team_name") }
        return (teamId.isNotBlank() && rowId.isNotBlank() && rowId == teamId) ||
            (teamName.isNotBlank() && rowName.equals(teamName, ignoreCase = true))
    }

    val lineupRows = (0 until lineups.length()).mapNotNull { lineups.optJSONObject(it) }
        .filter { it.readText("player_name", "name").isNotBlank() }
    val homePlayers = lineupRows.filter { belongsTo(it, "home", homeId, homeName) && !it.optBoolean("is_substitute") }
    val awayPlayers = lineupRows.filter { belongsTo(it, "away", awayId, awayName) && !it.optBoolean("is_substitute") }
    val homeSubs = lineupRows.filter { belongsTo(it, "home", homeId, homeName) && it.optBoolean("is_substitute") }
    val awaySubs = lineupRows.filter { belongsTo(it, "away", awayId, awayName) && it.optBoolean("is_substitute") }
    val splitLineups = homePlayers.isNotEmpty() || awayPlayers.isNotEmpty()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 13.dp, vertical = 10.dp)) {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = XCard),
            border = BorderStroke(1.dp, XBorder),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                Modifier.fillMaxWidth()
                     .background(Brush.verticalGradient(listOf(XCard2, XCard)))
                    .padding(15.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    if (competitionLogo.isNotBlank()) {
                        coil.compose.AsyncImage(competitionLogo, competitionName, Modifier.size(23.dp), contentScale = ContentScale.Fit)
                        Spacer(Modifier.width(7.dp))
                    }
                    Text(competitionName.ifBlank { "البطولة غير متاحة" }, color = XAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (homeLogo.isNotBlank()) {
                            coil.compose.AsyncImage(homeLogo, homeName, Modifier.size(64.dp), contentScale = ContentScale.Fit)
                        } else {
                            TeamLogoFallback(homeName)
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(homeName, color = XText, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(score, color = XText, fontSize = 29.sp, fontWeight = FontWeight.Black)
                        Text(match.optString("status").ifBlank { "الحالة غير متاحة" }, color = XAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (awayLogo.isNotBlank()) {
                            coil.compose.AsyncImage(awayLogo, awayName, Modifier.size(64.dp), contentScale = ContentScale.Fit)
                        } else {
                            TeamLogoFallback(awayName)
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(awayName, color = XText, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
                    }
                }
                Spacer(Modifier.height(12.dp))
                val venue = match.optJSONObject("venue")?.optString("name").orEmpty().ifBlank { match.optString("venue") }
                val referee = match.optJSONObject("referee")?.optString("name").orEmpty()
                val meta = listOf(venue, referee.takeIf { it.isNotBlank() }?.let { "الحكم $it" }).filter { !it.isNullOrBlank() }
                val kickoff = match.readText("startTime", "kickoff", "date")
                if (kickoff.isNotBlank()) {
                    Text(kickoff.replace("T", " ").take(16), color = XMuted, fontSize = 9.sp)
                }
                if (meta.isNotEmpty()) Text(meta.joinToString(" • "), color = XMuted, fontSize = 9.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }

        Button(
            onClick = { watching = KoraxMatchWatchStore.toggle(context, matchId) },
            enabled = matchId.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (watching) XCard2 else XAccent,
                contentColor = if (watching) XText else Color.Black
            ),
            shape = RoundedCornerShape(13.dp)
        ) {
            Text(if (watching) "✓ تتابع هذه المباراة — إيقاف التنبيه" else "🔔 تابع المباراة ونبّهني بأحداثها", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            listOf(
                "events" to "الأحداث",
                "lineups" to "التشكيلة",
                "stats" to "الإحصائيات",
                "details" to "التفاصيل"
            ).forEach { (key, label) ->
                FilterChip(
                    selected = detailTab == key,
                    onClick = { detailTab = key },
                    modifier = Modifier.weight(1f),
                    label = { Text(label, fontSize = 9.sp, maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = XAccent,
                        selectedLabelColor = Color.Black,
                        labelColor = XMuted
                    )
                )
            }
        }

        when (detailTab) {
            "events" -> {
                Text("أحداث المباراة", color = XText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 8.dp))
                if (events.length() == 0) {
                    Text("لم يوفر المصدر أحداثًا تفصيلية لهذا اللقاء.", color = XMuted, fontSize = 10.sp)
                } else {
                    repeat(events.length()) { index ->
                        val row = events.optJSONObject(index) ?: return@repeat
                        val minute = row.readText("minute").ifBlank { "—" }
                        val type = row.readText("type_name", "type").ifBlank { "حدث" }
                        val player = row.readText("player_name", "player")
                        val detail = row.readText("detail")
                        val kind = type.lowercase(Locale.ROOT)
                        val eventSymbol = when {
                            kind.contains("goal") || kind.contains("هدف") -> "⚽"
                            kind.contains("yellow") || kind.contains("أصفر") || kind.contains("اصفر") -> "🟨"
                            kind.contains("red") || kind.contains("أحمر") || kind.contains("احمر") -> "🟥"
                            kind.contains("sub") || kind.contains("تبديل") -> "🔁"
                            else -> "•"
                        }
                        Card(
                            Modifier.fillMaxWidth().padding(bottom = 5.dp),
                            colors = CardDefaults.cardColors(containerColor = XCard),
                            border = BorderStroke(1.dp, XBorder),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(minute + "'", color = XAccent, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.width(34.dp))
                                Text(eventSymbol, fontSize = 15.sp)
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(type, color = XText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text(listOf(player, detail).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "تفاصيل اللاعب غير متاحة" }, color = XMuted, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
            "lineups" -> {
                Text("التشكيلات الرسمية", color = XText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 8.dp))
                when {
                    lineupRows.isEmpty() -> Text("التشكيلات غير متاحة من المصدر لهذا اللقاء.", color = XMuted, fontSize = 10.sp)
                    splitLineups -> {
                        if (homePlayers.isNotEmpty() || homeSubs.isNotEmpty()) {
                            MatchLineupSide(homeName, homePlayers, homeSubs, match.optString("homeFormation"))
                        }
                        if (awayPlayers.isNotEmpty() || awaySubs.isNotEmpty()) {
                            if (homePlayers.isNotEmpty() || homeSubs.isNotEmpty()) Spacer(Modifier.height(9.dp))
                            MatchLineupSide(awayName, awayPlayers, awaySubs, match.optString("awayFormation"))
                        }
                    }
                    else -> {
                        Text("أرسل المصدر أسماء لاعبين دون تحديد الفريق؛ نعرضها كما وصلت دون تخمين.", color = XMuted, fontSize = 9.sp)
                        lineupRows.forEach { row ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(row.readText("shirt_number").ifBlank { "—" }, color = XAccent, fontSize = 10.sp, modifier = Modifier.width(28.dp))
                                Text(row.readText("player_name", "name"), color = XText, fontSize = 10.sp, modifier = Modifier.weight(1f))
                                Text(row.readText("position_name").ifBlank { "المركز غير متاح" }, color = XMuted, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
            "stats" -> {
                Text("إحصائيات المباراة", color = XText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 8.dp))
                if (stats.length() == 0) {
                    Text("الإحصائيات غير متاحة من المصدر لهذا اللقاء.", color = XMuted, fontSize = 10.sp)
                } else {
                    val statRows = (0 until stats.length()).mapNotNull { stats.optJSONObject(it) }
                        .filter { it.readText("name", "type_name", "type").isNotBlank() }
                        .groupBy { it.readText("name", "type_name", "type") }
                    statRows.forEach { (name, rows) ->
                        val homeValue = rows.firstOrNull { it.readText("team").equals(homeName, true) }?.readText("value", "data").orEmpty()
                        val awayValue = rows.firstOrNull { it.readText("team").equals(awayName, true) }?.readText("value", "data").orEmpty()
                        if (homeValue.isNotBlank() || awayValue.isNotBlank()) {
                            Column(Modifier.fillMaxWidth().background(XCard, RoundedCornerShape(12.dp)).padding(horizontal = 11.dp, vertical = 9.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(homeValue.ifBlank { "—" }, color = XText, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text(name, color = XMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.5f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    Text(awayValue.ifBlank { "—" }, color = XText, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                }
                                val homeNumber = statNumber(homeValue)
                                val awayNumber = statNumber(awayValue)
                                if (homeNumber != null && awayNumber != null && homeNumber >= 0f && awayNumber >= 0f) {
                                    val total = (homeNumber + awayNumber).coerceAtLeast(0.001f)
                                    val firstWeight = (homeNumber / total).coerceAtLeast(0.02f)
                                    val secondWeight = (awayNumber / total).coerceAtLeast(0.02f)
                                    Row(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(XCard2)) {
                                        Box(Modifier.weight(firstWeight).fillMaxHeight().background(XAccent))
                                        Box(Modifier.weight(secondWeight).fillMaxHeight().background(Color(0xFF7082A5)))
                                    }
                                }
                            }
                            Spacer(Modifier.height(5.dp))
                        }
                    }
                }
            }
            else -> {
                Text("معلومات اللقاء", color = XText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 8.dp))
                val venue = match.optJSONObject("venue")?.optString("name").orEmpty().ifBlank { match.optString("venue") }
                val referee = match.optJSONObject("referee")?.optString("name").orEmpty()
                val details = listOf(
                    "البطولة" to competitionName,
                    "الحالة" to match.optString("status"),
                    "الموعد" to match.readText("startTime", "kickoff", "date"),
                    "الملعب" to venue,
                    "الحكم" to referee
                ).filter { it.second.isNotBlank() }
                if (details.isEmpty()) {
                    Text("المعلومات الإضافية غير متاحة من المصدر لهذا اللقاء.", color = XMuted, fontSize = 10.sp)
                } else {
                    details.forEach { (label, value) ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp).background(XCard, RoundedCornerShape(10.dp)).padding(11.dp)) {
                            Text(label, color = XMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                            Text(value, color = XText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        if (!match.optBoolean("detailAvailable")) {
            Text("يعرض هذا اللقاء البيانات الأساسية فقط؛ لم تصل تفاصيل إضافية من SportScore بعد.", color = XMuted, fontSize = 9.sp, modifier = Modifier.padding(vertical = 12.dp))
        } else {
            Text("تُعرض فقط البيانات التي أعادها مزوّد البيانات لهذا اللقاء.", color = XMuted, fontSize = 9.sp, modifier = Modifier.padding(vertical = 12.dp))
        }
        Text(
            "Powered by SportScore",
            color = XMuted,
            fontSize = 9.sp,
            modifier = Modifier.fillMaxWidth().clickable {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://sportscore.com/"))) }
            }.padding(vertical = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

private fun statNumber(raw: String): Float? =
    Regex("-?\\d+(?:[.,]\\d+)?").find(raw)?.value?.replace(',', '.')?.toFloatOrNull()

@Composable
private fun TeamLogoFallback(name: String) {
    Box(Modifier.size(56.dp).clip(CircleShape).background(XCard2), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).ifBlank { "?" }, color = XAccent, fontSize = 23.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun MatchLineupSide(
    teamName: String,
    players: List<JSONObject>,
    substitutes: List<JSONObject> = emptyList(),
    formation: String = ""
) {
    val positionGroups = listOf(
        "G" to "حراسة المرمى",
        "D" to "الدفاع",
        "M" to "الوسط",
        "F" to "الهجوم"
    )
    val mapped = players.groupBy { row ->
        when (row.readText("position_name").uppercase(Locale.ROOT)) {
            "G", "GK", "GOALKEEPER" -> "G"
            "D", "DF", "DEFENDER" -> "D"
            "M", "MF", "MIDFIELDER" -> "M"
            "F", "FW", "FORWARD", "ATTACKER" -> "F"
            else -> "?"
        }
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = XCard),
        border = BorderStroke(1.dp, XBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(teamName, color = XText, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                if (formation.isNotBlank()) Text(formation, color = XAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(7.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF0B6346), Color(0xFF0D7952), Color(0xFF0B6346))))
                    .padding(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    positionGroups.forEach { (key, label) ->
                        val group = mapped[key].orEmpty()
                        if (group.isNotEmpty()) {
                            Column {
                                Text(label, color = Color.White.copy(alpha = .78f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                group.chunked(3).forEach { rowPlayers ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        rowPlayers.forEach { row ->
                                            val name = row.readText("player_name", "name")
                                            val number = row.readText("shirt_number")
                                            val image = row.readText("imageUrl", "image_url")
                                            Column(
                                                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF0A4C39).copy(alpha = .92f)).padding(5.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                if (image.startsWith("https://")) {
                                                    coil.compose.AsyncImage(image, name, Modifier.size(26.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                                                } else {
                                                    Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                                                        Text(number.ifBlank { name.take(1) }, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                                Spacer(Modifier.height(3.dp))
                                                Text(name, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, overflow = TextOverflow.Ellipsis)
                                                // Render a player rating only when the provider supplied a numeric value.
                                                val ratingRaw = row.readText("rating", "player_rating", "average_rating")
                                                val rating = Regex("""^\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:/10)?\\s*$""")
                                                    .matchEntire(ratingRaw)?.groupValues?.getOrNull(1)
                                                if (!rating.isNullOrBlank()) {
                                                    Text(
                                                        "★ $rating",
                                                        color = Color.Black,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        modifier = Modifier
                                                            .padding(top = 3.dp)
                                                            .background(XAccent, RoundedCornerShape(8.dp))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        repeat(3 - rowPlayers.size) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val unknown = mapped["?"].orEmpty()
                    if (unknown.isNotEmpty()) {
                        Text("مراكز غير محددة من المصدر", color = Color.White.copy(alpha = .75f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        unknown.forEach { row ->
                            Text(row.readText("player_name", "name"), color = Color.White, fontSize = 9.sp)
                        }
                    }
                    if (substitutes.isNotEmpty()) {
                        Spacer(Modifier.height(3.dp))
                        Text("دكة البدلاء", color = Color.White.copy(alpha = .82f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        substitutes.forEach { row ->
                            val number = row.readText("shirt_number")
                            val name = row.readText("player_name", "name")
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(number.ifBlank { "—" }, color = XAccent, fontSize = 9.sp, modifier = Modifier.width(25.dp))
                                Text(name, color = Color.White, fontSize = 9.sp, modifier = Modifier.weight(1f))
                                Text(row.readText("position_name"), color = Color.White.copy(alpha = .68f), fontSize = 8.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KoraxAssistantPage(onBack: () -> Unit, onOpenArticle: (NewsItem) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var news by remember { mutableStateOf<List<NewsItem>>(emptyList()) }
    var entities by remember { mutableStateOf<List<ToolEntity>>(emptyList()) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(XBg)) {
        ToolHeader("مساعد KORAX", "بحث موحد في مصادر التطبيق الحالية", onBack)
        OutlinedTextField(
            value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            label = { Text("اكتب اسم لاعب أو نادي أو موضوع كروي") }, singleLine = true,
            trailingIcon = { Icon(Icons.Filled.Search, null, tint = XAccent) }
        )
        Button(onClick = {
            scope.launch {
                loading = true; error = ""; news = emptyList(); entities = emptyList()
                val q = query.trim()
                val result = runCatching {
                    val n = apiJson("/api/news?language=ar&per_page=100").rows()
                    val foundNews = (0 until n.length()).mapNotNull { i ->
                        val row = n.optJSONObject(i) ?: return@mapNotNull null
                        val title = row.optString("title")
                        val summary = row.optString("summary")
                        if (!(title + " " + summary).contains(q, true)) return@mapNotNull null
                        val url = row.optString("url")
                        if (!url.startsWith("https://")) return@mapNotNull null
                        NewsItem(row.optString("id", url), title, summary, url, row.optString("source"), row.optString("publishedAt"), row.readImage("imageUrl").ifBlank { "" }.takeIf { it.isNotBlank() }, row.optString("category"))
                    }
                    val foundEntities = mutableListOf<ToolEntity>()
                    listOf("players", "teams", "competitions").forEach { endpoint ->
                        runCatching {
                            val arr = apiJson("/api/" + endpoint + "?search=" + q.urlEncoded()).optJSONArray("data") ?: JSONArray()
                            repeat(arr.length()) { i ->
                                val row = arr.optJSONObject(i) ?: return@repeat
                                val name = row.readText("name", "common_name", "title")
                                val slug = row.readText("slug", "url_slug", "id")
                                if (name.isNotBlank() && slug.isNotBlank()) foundEntities.add(
                                    ToolEntity(row.readText("id").ifBlank { slug }, slug, name, endpoint, row.readImage("imageUrl", "image_path", "logoUrl"), row)
                                )
                            }
                        }
                    }
                    Pair(foundNews.distinctBy { it.url }, foundEntities.distinctBy { it.id })
                }
                news = result.getOrDefault(emptyList<NewsItem>() to emptyList()).first
                entities = result.getOrDefault(emptyList<NewsItem>() to emptyList()).second
                error = result.exceptionOrNull()?.message.orEmpty()
                loading = false
            }
        }, enabled = query.trim().length >= 2 && !loading, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp), colors = ButtonDefaults.buttonColors(containerColor = XAccent, contentColor = Color.Black)) {
            Text(if (loading) "جارٍ البحث في المصادر..." else "ابحث")
        }
        if (error.isNotBlank()) Text(error, color = Color(0xFFFF7373), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 14.dp))
        LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (entities.isNotEmpty()) item { Text("ملفات من SportScore", color = XAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            items(entities) { entity ->
                Row(Modifier.fillMaxWidth().background(XCard, RoundedCornerShape(10.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entity.name, color = XText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(entity.subtitle, color = XMuted, fontSize = 9.sp)
                    }
                }
            }
            if (news.isNotEmpty()) item { Text("أخبار RSS المطابقة", color = XAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 9.dp)) }
            items(news, key = { it.url }) { article ->
                Card(Modifier.fillMaxWidth().clickable { onOpenArticle(article) }, colors = CardDefaults.cardColors(containerColor = XCard), border = BorderStroke(1.dp, XBorder), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text(article.source, color = XAccent, fontSize = 9.sp)
                        Text(article.title, color = XText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (article.summary.isNotBlank()) Text(article.summary, color = XMuted, fontSize = 9.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (!loading && query.length >= 2 && news.isEmpty() && entities.isEmpty()) item { InfoPanel("لا توجد نتائج", "لم يعثر البحث على نتائج مطابقة في الأخبار والملفات المتاحة.") }
        }
    }
}

@Composable
internal fun KoraxTopicPage(onBack: () -> Unit, onOpenArticle: (NewsItem) -> Unit) {
    var topic by rememberSaveable { mutableStateOf("transfers") }
    var articles by remember { mutableStateOf<List<NewsItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    suspend fun refresh() {
        loading = true
        val result = runCatching {
            val rows = apiJson("/api/news?language=ar&per_page=100").rows()
            (0 until rows.length()).mapNotNull { index ->
                val row = rows.optJSONObject(index) ?: return@mapNotNull null
                val title = row.optString("title")
                val summary = row.optString("summary")
                val text = (title + " " + summary).lowercase(Locale.ROOT)
                val match = if (topic == "transfers")
                    listOf("انتقال", "صفقة", "ميركاتو", "إعارة", "مفاوضات", "تجديد عقد", "ضم اللاعب").any(text::contains)
                else listOf("إصابة", "مصاب", "غياب", "إيقاف", "جاهزية", "تأهيل", "injury", "injured", "suspended").any(text::contains)
                if (!match) return@mapNotNull null
                val url = row.optString("url")
                if (!url.startsWith("https://")) return@mapNotNull null
                NewsItem(row.optString("id", url), title, summary, url, row.optString("source"), row.optString("publishedAt"), row.readImage("imageUrl").takeIf { it.isNotBlank() }, row.optString("category"))
            }
        }
        articles = result.getOrDefault(emptyList())
        error = result.exceptionOrNull()?.message.orEmpty()
        loading = false
    }
    LaunchedEffect(topic) { refresh() }
    Column(Modifier.fillMaxSize().background(XBg)) {
        ToolHeader("الإصابات والانتقالات", "أخبار موضوعية من موجزات RSS المعتمدة", onBack, onRefresh = { scope.launch { refresh() } })
        Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = topic == "transfers", onClick = { topic = "transfers" }, label = { Text("الانتقالات") })
            FilterChip(selected = topic == "injuries", onClick = { topic = "injuries" }, label = { Text("الإصابات والإيقافات") })
        }
        if (loading) BusyPanel("تحميل الأخبار...")
        else if (error.isNotBlank()) InfoPanel("تعذر تحميل الأخبار", error)
        else if (articles.isEmpty()) InfoPanel("لا توجد أخبار مطابقة الآن", "لا يقدم SportScore مسار انتقالات مؤكدًا؛ لذلك يعرض هذا القسم أخبار RSS مع اسم الناشر دون اعتبار الشائعة صفقة رسمية.")
        else LazyColumn(contentPadding = PaddingValues(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(articles, key = { it.url }) { item ->
                Card(Modifier.fillMaxWidth().clickable { onOpenArticle(item) }, colors = CardDefaults.cardColors(containerColor = XCard), border = BorderStroke(1.dp, XBorder), shape = RoundedCornerShape(13.dp)) {
                    Column(Modifier.padding(11.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.source, color = XAccent, fontSize = 9.sp)
                            Spacer(Modifier.weight(1f))
                            Text("تقرير صحفي • يحتاج تحقق", color = XMuted, fontSize = 8.sp)
                        }
                        Text(item.title, color = XText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                        if (item.summary.isNotBlank()) Text(item.summary, color = XMuted, fontSize = 9.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun KoraxTrendsPage(onBack: () -> Unit, onOpenArticle: (NewsItem) -> Unit) {
    var articles by remember { mutableStateOf<List<NewsItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    suspend fun refresh() {
        loading = true
        val result = runCatching {
            val rows = apiJson("/api/news?language=ar&per_page=100").rows()
            (0 until rows.length()).mapNotNull { index ->
                val row = rows.optJSONObject(index) ?: return@mapNotNull null
                val url = row.optString("url")
                if (!url.startsWith("https://")) return@mapNotNull null
                NewsItem(row.optString("id", url), row.optString("title"), row.optString("summary"), url, row.optString("source"), row.optString("publishedAt"), row.readImage("imageUrl").takeIf { it.isNotBlank() }, row.optString("category"))
            }.filter { it.title.isNotBlank() }
        }
        articles = result.getOrDefault(emptyList())
        error = result.exceptionOrNull()?.message.orEmpty()
        loading = false
    }
    LaunchedEffect(Unit) { refresh() }
    val stop = setOf("هذا", "هذه", "على", "من", "إلى", "عن", "بعد", "قبل", "مع", "في", "بين", "عبر", "أمام", "خلال", "الذي", "التي", "اليوم", "أمس", "غدا", "كرة", "القدم", "نادي", "فريق", "لاعب", "الدوري", "رسميا", "رسميًا")
    val counts = articles.flatMap { article ->
        Regex("[\u0600-\u06FF]{3,}").findAll(article.title).map { it.value }.toList().distinct()
    }.filterNot { it in stop }.groupingBy { it }.eachCount().entries
        .filter { it.value >= 2 }.sortedByDescending { it.value }.take(15)

    Column(Modifier.fillMaxSize().background(XBg)) {
        ToolHeader("الأكثر تكرارًا", "موضوعات تكررت في عناوين RSS المحمّلة، وليست قياسًا للتداول الاجتماعي", onBack, onRefresh = { scope.launch { refresh() } })
        when {
            loading -> BusyPanel("تحليل عناوين الأخبار...")
            error.isNotBlank() -> InfoPanel("تعذر التحليل", error)
            else -> LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                item {
                    Text("ملخص الأخبار المتاحة", color = XMuted, fontSize = 10.sp)
                    Text(articles.size.toString() + " خبرًا محمّلًا", color = XAccent, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
                items(counts.toList()) { item ->
                    Row(Modifier.fillMaxWidth().background(XCard, RoundedCornerShape(10.dp)).padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.key, color = XText, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text(item.value.toString() + " مرات", color = XAccent, fontSize = 10.sp)
                    }
                }
                item { Text("أحدث الأخبار", color = XText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)) }
                items(articles.take(20), key = { it.url }) { item ->
                    Text(item.title, color = XText, fontSize = 10.sp, modifier = Modifier.fillMaxWidth().clickable { onOpenArticle(item) }.padding(vertical = 7.dp))
                    Divider(color = XBorder)
                }
            }
        }
    }
}

@Composable
private fun KoraxHealthPage(onBack: () -> Unit) {
    var checks by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var lastCheck by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    suspend fun refresh() {
        loading = true
        val results = mutableListOf<Pair<String, String>>()
        suspend fun check(label: String, path: String, countRows: Boolean) {
            val result = runCatching {
                val payload = apiJson(path)
                val count = if (countRows) payload.optJSONArray("data")?.length() ?: 0 else -1
                if (count >= 0) label + " • " + count + " عنصر" else payload.readText("service", "status").ifBlank { "استجابة سليمة" }
            }
            results.add(label to (result.getOrNull() ?: ("غير متاح: " + result.exceptionOrNull()?.message.orEmpty())))
        }
        check("خادم KORAX", "/health", false)
        check("أخبار RSS", "/api/news?language=ar&per_page=5", true)
        check("بطولات SportScore", "/api/competitions", true)
        check("مباريات SportScore", "/api/matches?live=1", true)
        checks = results
        lastCheck = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        loading = false
    }
    LaunchedEffect(Unit) { refresh() }
    Column(Modifier.fillMaxSize().background(XBg)) {
        ToolHeader("حالة مصادر البيانات", "فحص تقني للمصادر الحالية فقط دون إضافة مزود جديد", onBack, onRefresh = { scope.launch { refresh() } })
        if (loading) BusyPanel("فحص الخدمات...")
        else Column(Modifier.fillMaxWidth().padding(14.dp)) {
            checks.forEach { (name, value) ->
                val ok = !value.startsWith("غير متاح")
                Row(Modifier.fillMaxWidth().background(XCard, RoundedCornerShape(11.dp)).padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(if (ok) Color(0xFF45C486) else Color(0xFFE36C6C)))
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, color = XText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(value, color = XMuted, fontSize = 9.sp)
                    }
                    Text(if (ok) "متصل" else "فشل", color = if (ok) Color(0xFF45C486) else Color(0xFFE36C6C), fontSize = 9.sp)
                }
                Spacer(Modifier.height(7.dp))
            }
            Text("آخر فحص محلي: " + lastCheck, color = XMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp))
            Text("هذا الفحص يثبت استجابة المسارات وقت الفحص فقط؛ لا يضمن توافر كل بطولة أو صورة أو إحصائية.", color = XMuted, fontSize = 9.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 9.dp))
        }
    }
}

@Composable
private fun KoraxOfflinePage(onBack: () -> Unit, onOpenArticle: (NewsItem) -> Unit) {
    val context = LocalContext.current
    var saved by remember { mutableStateOf(KoraxOfflineStore.all(context)) }
    Column(Modifier.fillMaxSize().background(XBg)) {
        ToolHeader("القراءة دون اتصال", "ملخصات الأخبار المحفوظة محليًا على هذا الجهاز", onBack, onRefresh = { saved = KoraxOfflineStore.all(context) })
        if (saved.isEmpty()) InfoPanel("لا توجد أخبار محفوظة", "افتح أي خبر واضغط «حفظ» ليتم الاحتفاظ بعنوانه وملخصه ورابطه للقراءة دون اتصال.")
        else LazyColumn(contentPadding = PaddingValues(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(saved, key = { it.url }) { item ->
                Card(colors = CardDefaults.cardColors(containerColor = XCard), border = BorderStroke(1.dp, XBorder), shape = RoundedCornerShape(13.dp)) {
                    Column(Modifier.padding(11.dp)) {
                        Text(item.source, color = XAccent, fontSize = 9.sp)
                        Text(item.title, color = XText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onOpenArticle(item) })
                        if (item.summary.isNotBlank()) Text(item.summary, color = XMuted, fontSize = 9.sp, maxLines = 4)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { KoraxOfflineStore.remove(context, item.url); saved = KoraxOfflineStore.all(context) }) { Text("إزالة", color = Color(0xFFE36C6C)) }
                            TextButton(onClick = { onOpenArticle(item) }) { Text("فتح الخبر", color = XAccent) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KoraxBackupPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }
    val backupKeys = listOf("korax_user_preferences_v1", "korax_general_settings_v1", "korax_news_interactions_v2", "korax_offline_articles_v1", "korax_smart_alerts_v2")
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val result = runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("تعذر قراءة ملف النسخة الاحتياطية")
                val root = JSONObject(raw)
                var restored = 0
                backupKeys.forEach { key ->
                    val group = root.optJSONObject(key) ?: return@forEach
                    val editor = context.getSharedPreferences(key, Context.MODE_PRIVATE).edit().clear()
                    val iterator = group.keys()
                    while (iterator.hasNext()) {
                        val name = iterator.next()
                        when (val value = group.opt(name)) {
                            null, JSONObject.NULL -> Unit
                            is JSONArray -> {
                                val set = (0 until value.length()).map { value.optString(it) }.toSet()
                                editor.putStringSet(name, set)
                            }
                            is String -> editor.putString(name, value)
                            is Boolean -> editor.putBoolean(name, value)
                            is Int -> editor.putInt(name, value)
                            is Long -> editor.putLong(name, value)
                            is Float -> editor.putFloat(name, value)
                            is Double -> editor.putFloat(name, value.toFloat())
                            else -> editor.putString(name, value.toString())
                        }
                    }
                    editor.apply()
                    restored++
                }
                require(restored > 0) { "الملف لا يحتوي على بيانات KORAX معروفة." }
                "تم استيراد بيانات النسخة الاحتياطية. أعد فتح التطبيق لتحديث كل الشاشات."
            }
            message = result.getOrElse { "تعذر استعادة النسخة: " + it.message.orEmpty() }
        }
    }
    Column(Modifier.fillMaxSize().background(XBg)) {
        ToolHeader("النسخة الاحتياطية المحلية", "تصدير بيانات هذا الجهاز دون إنشاء حساب", onBack)
        Column(Modifier.padding(14.dp)) {
            Text("يمكن تصدير المفضلة والإعدادات العامة والتعليقات والمحفوظات المحلية لمشاركتها أو الاحتفاظ بها. لا يشمل هذا ملفات التطبيق أو بيانات خادم خارجية.", color = XMuted, fontSize = 11.sp, lineHeight = 18.sp)
            Button(onClick = {
                val root = JSONObject()
                backupKeys.forEach { key ->
                    val values = context.getSharedPreferences(key, Context.MODE_PRIVATE).all
                    val group = JSONObject()
                    values.forEach { (name, value) ->
                        when (value) {
                            is Set<*> -> group.put(name, JSONArray(value.map { it.toString() }))
                            null -> group.put(name, JSONObject.NULL)
                            else -> group.put(name, value)
                        }
                    }
                    root.put(key, group)
                }
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_SUBJECT, "KORAX local backup")
                    putExtra(Intent.EXTRA_TEXT, root.toString(2))
                }
                runCatching { context.startActivity(Intent.createChooser(share, "حفظ النسخة الاحتياطية")) }
                    .onSuccess { message = "تم فتح خيارات مشاركة النسخة الاحتياطية." }
                    .onFailure { message = it.message.orEmpty() }
            }, modifier = Modifier.fillMaxWidth().padding(top = 15.dp), colors = ButtonDefaults.buttonColors(containerColor = XAccent, contentColor = Color.Black)) {
                Text("تصدير نسخة احتياطية")
            }
            OutlinedButton(
                onClick = { restoreLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("استعادة نسخة احتياطية") }
            if (message.isNotBlank()) Text(message, color = XMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
            Text("تنبيه: الاستعادة تستبدل مجموعات التفضيلات المحلية المعروفة بالبيانات الموجودة في الملف. لا توجد مزامنة تلقائية بين الأجهزة في النسخة الحالية. احتفظ بالملف في مكان آمن، فقد يحتوي على تعليقاتك وتفضيلاتك.", color = XMuted, fontSize = 9.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 13.dp))
        }
    }
}

@Composable
private fun BusyPanel(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = XAccent)
            Text(text, color = XMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 9.dp))
        }
    }
}

@Composable
private fun InfoPanel(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = XCard), border = BorderStroke(1.dp, XBorder), shape = RoundedCornerShape(15.dp)) {
            Column(Modifier.padding(15.dp)) {
                Text(title, color = XText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(detail, color = XMuted, fontSize = 10.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}


@Composable
internal fun KoraxCompetitionsHubPage(
    onBack: () -> Unit,
    onOpenProfile: (KoraxFavorite) -> Unit
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var competitions by remember { mutableStateOf<List<KoraxFavorite>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun refresh(search: String = query) {
        loading = true
        error = ""
        val endpoint = if (search.trim().length >= 2)
            "/api/competitions?search=" + search.trim().urlEncoded()
        else "/api/competitions"
        val result = runCatching {
            val payload = apiJson(endpoint)
            val rows = payload.optJSONArray("data") ?: JSONArray()
            (0 until rows.length()).mapNotNull { index ->
                val row = rows.optJSONObject(index) ?: return@mapNotNull null
                val name = row.readText("name", "title", "common_name")
                val slug = row.readText("slug", "competition_slug", "league_slug", "url_slug", "id")
                val id = row.readText("id").ifBlank { slug }
                if (name.isBlank() || slug.isBlank() || id.isBlank()) return@mapNotNull null
                val rawKind = row.readText("kind", "type", "entity_type").lowercase(Locale.ROOT)
                val kind = if (rawKind.contains("tournament") || rawKind.contains("cup") || rawKind.contains("championship")) "tournament" else "league"
                KoraxFavorite(
                    id = id,
                    slug = slug,
                    name = name,
                    kind = kind,
                    imageUrl = row.readImage("imageUrl", "image_path", "logoUrl", "logo"),
                    country = row.readText("country", "country_name")
                )
            }.distinctBy { it.kind + ":" + it.id }
        }
        competitions = result.getOrDefault(emptyList())
        error = result.exceptionOrNull()?.message.orEmpty()
        loading = false
    }

    LaunchedEffect(Unit) { refresh("") }

    Column(Modifier.fillMaxSize().background(XBg)) {
        ToolHeader("البطولات والدوريات", "المسابقات المتاحة من SportScore", onBack, onRefresh = { scope.launch { refresh() } })
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(13.dp),
                placeholder = { Text("ابحث عن دوري أو بطولة", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = XAccent) }
            )
            Spacer(Modifier.width(7.dp))
            Button(
                onClick = { scope.launch { refresh(query) } },
                enabled = query.trim().length >= 2 && !loading,
                colors = ButtonDefaults.buttonColors(containerColor = XAccent, contentColor = Color.Black)
            ) { Text("بحث", fontSize = 11.sp) }
        }
        when {
            loading -> BusyPanel("تحميل البطولات والدوريات من SportScore...")
            error.isNotBlank() -> InfoPanel("تعذر تحميل البطولات", error)
            competitions.isEmpty() -> InfoPanel(
                "لم تصل مسابقات من المصدر",
                "جرّب البحث باسم دوري أو بطولة. لن نعرض مسابقات وهمية إذا لم يرجعها المصدر."
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text("${competitions.size} مسابقة متاحة من المصدر", color = XAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                items(competitions, key = { it.kind + ":" + it.id }) { item ->
                    Card(
                        Modifier.fillMaxWidth().clickable { onOpenProfile(item) },
                        colors = CardDefaults.cardColors(containerColor = XCard),
                        border = BorderStroke(1.dp, XBorder),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(45.dp).clip(RoundedCornerShape(11.dp)).background(XCard2),
                                contentAlignment = Alignment.Center
                            ) {
                                if (item.imageUrl.isNotBlank() && KoraxSettingsStore.getBoolean(LocalContext.current, "show_images", true)) {
                                    coil.compose.AsyncImage(item.imageUrl, item.name, Modifier.fillMaxSize().padding(5.dp), contentScale = ContentScale.Fit)
                                } else {
                                    Text("🏆", fontSize = 21.sp)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = XText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    listOf(if (item.kind == "tournament") "بطولة" else "دوري", item.country)
                                        .filter { it.isNotBlank() }.joinToString(" • "),
                                    color = XMuted, fontSize = 9.sp
                                )
                            }
                            Text("الترتيب والتفاصيل ←", color = XAccent, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
        Text(
            "Powered by SportScore",
            color = XMuted,
            fontSize = 9.sp,
            modifier = Modifier.fillMaxWidth().clickable {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://sportscore.com/"))) }
            }.padding(vertical = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
