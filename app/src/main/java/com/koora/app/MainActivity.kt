package com.koora.app

import android.content.Context
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val Ink = Color(0xFF090B10)
private val Panel = Color(0xFF141923)
private val Panel2 = Color(0xFF1C2430)
private val Gold = Color(0xFFE5B85C)
private val Green = Color(0xFF33C48D)
private val Muted = Color(0xFF9AA5B5)
private val Bright = Color(0xFFF4F6FA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent { KooraTheme { KooraApp(applicationContext) } }
    }
}

@Composable
private fun KooraTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold,
            onPrimary = Ink,
            secondary = Green,
            background = Ink,
            surface = Panel,
            onSurface = Bright,
            onBackground = Bright,
            outline = Color(0xFF303A49)
        ),
        content = content
    )
    }
}

private data class FootballRecord(
    val id: String,
    val kind: String,
    val title: String,
    val subtitle: String = "",
    val imageUrl: String? = null,
    val home: String? = null,
    val away: String? = null,
    val score: String? = null,
    val status: String? = null,
    val kickoff: String? = null,
    val facts: List<Pair<String, String>> = emptyList(),
    val events: List<String> = emptyList(),
    val lineups: List<String> = emptyList(),
    val statistics: List<Pair<String, String>> = emptyList(),
    val description: String? = null
) {
    val favoriteKey: String get() = "$kind:$id"
}

private data class FootballData(
    val matches: List<FootballRecord> = emptyList(),
    val teams: List<FootballRecord> = emptyList(),
    val players: List<FootballRecord> = emptyList(),
    val competitions: List<FootballRecord> = emptyList(),
    val configured: Boolean = false,
    val error: Boolean = false
) {
    val all: List<FootballRecord>
        get() = matches + teams + players + competitions
}

private class KooraRepository {
    private val baseUrl = BuildConfig.KOORA_API_BASE_URL.trim().trimEnd('/')
    val configured: Boolean get() = baseUrl.startsWith("https://")

    suspend fun loadAll(): FootballData = withContext(Dispatchers.IO) {
        if (!configured) return@withContext FootballData(configured = false)
        try {
            val matches = fetchList("matches", "match")
            val teams = fetchList("teams", "team")
            val players = fetchList("players", "player")
            val competitions = fetchList("competitions", "competition")
            FootballData(matches, teams, players, competitions, configured = true)
        } catch (_: Exception) {
            FootballData(configured = true, error = true)
        }
    }

    suspend fun detail(kind: String, id: String): FootballRecord? = withContext(Dispatchers.IO) {
        if (!configured) return@withContext null
        val path = when (kind) {
            "match" -> "matches"
            "team" -> "teams"
            "player" -> "players"
            "competition" -> "competitions"
            else -> return@withContext null
        }
        try {
            val json = request("$baseUrl/api/$path/$id")
            if (json is JSONObject) parseRecord(json, kind) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchList(path: String, kind: String): List<FootballRecord> {
        val json = request("$baseUrl/api/$path")
        val array = when (json) {
            is JSONArray -> json
            is JSONObject -> json.optJSONArray("data")
                ?: json.optJSONArray(path)
                ?: JSONArray()
            else -> JSONArray()
        }
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { parseRecord(it, kind) }
        }
    }

    private fun request(url: String): Any? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) error("Backend returned ${connection.responseCode}")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return if (body.trimStart().startsWith("[")) JSONArray(body) else JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRecord(json: JSONObject, kind: String): FootballRecord {
        fun nestedName(key: String): String {
            val value = json.opt(key)
            return when (value) {
                is JSONObject -> value.optString("name", value.optString("shortName"))
                is String -> value
                else -> ""
            }
        }
        val home = nestedName("homeTeam").ifBlank { json.optString("home", "") }
        val away = nestedName("awayTeam").ifBlank { json.optString("away", "") }
        val title = when (kind) {
            "match" -> listOf(home, away).filter { it.isNotBlank() }.joinToString(" × ")
            else -> json.optString("name", json.optString("title", ""))
        }.ifBlank { "معلومة غير متاحة" }
        val score = if (json.has("homeScore") && json.has("awayScore")) {
            "${json.opt("homeScore")} - ${json.opt("awayScore")}"
        } else json.optString("score", "").ifBlank { "" }
        val competition = nestedName("competition").ifBlank { json.optString("competitionName", "") }
        val subtitle = when (kind) {
            "match" -> competition
            "player" -> listOf(json.optString("position", ""), nestedName("team")).filter { it.isNotBlank() }.joinToString(" • ")
            "team" -> listOf(json.optString("country", ""), json.optString("stadium", "")).filter { it.isNotBlank() }.joinToString(" • ")
            else -> json.optString("country", "")
        }
        val image = json.optString("imageUrl", "").ifBlank {
            json.optString("logoUrl", "").ifBlank { json.optString("image_path", "").ifBlank { nestedImage(json, "homeTeam") } }
        }.takeIf { it.startsWith("https://") }
        val factKeys = listOf(
            "country" to "الدولة", "nationality" to "الجنسية", "position" to "المركز",
            "date_of_birth" to "تاريخ الميلاد", "age" to "العمر", "height" to "الطول",
            "weight" to "الوزن", "shirt_number" to "رقم القميص", "founded" to "سنة التأسيس",
            "stadium" to "الملعب", "capacity" to "السعة", "name" to "الاسم",
            "starting_at" to "موعد المباراة", "round" to "الجولة", "type" to "النوع"
        )
        val facts = factKeys.mapNotNull { (key, label) ->
            val value = json.opt(key)
            when (value) {
                null, JSONObject.NULL -> null
                is JSONObject -> null
                is JSONArray -> null
                else -> value.toString().takeIf { it.isNotBlank() && it != "null" }?.let { label to it }
            }
        }.distinctBy { it.first }.take(12)

        val eventArray = json.optJSONArray("events") ?: json.optJSONArray("matchEvents") ?: JSONArray()
        val events = (0 until eventArray.length()).mapNotNull { i ->
            val event = eventArray.optJSONObject(i) ?: return@mapNotNull null
            val minute = event.optString("minute", event.optString("time", event.optString("display_time", "")))
            val typeObj = event.optJSONObject("type")
            val type = event.optString("type_name", typeObj?.optString("name").orEmpty()).ifBlank { "حدث" }
            val player = event.optJSONObject("player")?.optString("name").orEmpty()
                .ifBlank { event.optString("player_name", "") }
            val team = event.optJSONObject("team")?.optString("name").orEmpty()
            listOf(minute, type, player, team).filter { it.isNotBlank() }.joinToString(" • ")
                .takeIf { it.isNotBlank() }
        }.take(40)

        val lineupArray = json.optJSONArray("lineups") ?: json.optJSONArray("lineup") ?: JSONArray()
        val lineups = (0 until lineupArray.length()).mapNotNull { i ->
            val item = lineupArray.optJSONObject(i) ?: return@mapNotNull null
            val player = item.optJSONObject("player")?.optString("name").orEmpty()
                .ifBlank { item.optString("player_name", item.optString("name", "")) }
            val team = item.optJSONObject("team")?.optString("name").orEmpty()
            val position = item.optString("position_name", item.optString("position", ""))
            listOf(player, position, team).filter { it.isNotBlank() }.joinToString(" • ")
                .takeIf { it.isNotBlank() }
        }.take(44)

        val statsArray = json.optJSONArray("statistics") ?: JSONArray()
        val statistics = (0 until statsArray.length()).mapNotNull { i ->
            val item = statsArray.optJSONObject(i) ?: return@mapNotNull null
            val typeObj = item.optJSONObject("type")
            val label = item.optString("name", typeObj?.optString("name").orEmpty())
                .ifBlank { item.optString("type_name", "") }
            val valueObj = item.optJSONObject("data")
            val value = valueObj?.optString("value").orEmpty()
                .ifBlank { item.optString("value", "") }
            if (label.isBlank() || value.isBlank()) null else label to value
        }.take(30)

        val description = json.optString("description", "").ifBlank {
            json.optString("summary", "").ifBlank { json.optString("short_description", "") }
        }.takeIf { it.isNotBlank() }

        return FootballRecord(
            id = json.optString("id", json.optString("externalId", "")),
            kind = kind,
            title = title,
            subtitle = subtitle,
            imageUrl = image,
            home = home.ifBlank { null },
            away = away.ifBlank { null },
            score = score.ifBlank { null },
            status = json.optString("status", "").ifBlank { null },
            kickoff = json.optString("startTime", json.optString("utcDate", json.optString("starting_at", ""))).ifBlank { null },
            facts = facts,
            events = events,
            lineups = lineups,
            statistics = statistics,
            description = description
        )
    }

    private fun nestedImage(json: JSONObject, key: String): String {
        val child = json.optJSONObject(key) ?: return ""
        return child.optString("crest", child.optString("logoUrl", child.optString("image_path", "")))
    }
}

private class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("koora_local", Context.MODE_PRIVATE)

    fun favorites(): Set<String> = prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()
    fun saveFavorites(values: Set<String>) { prefs.edit().putStringSet("favorites", values.toSet()).apply() }
    fun bool(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    fun setBool(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
}

@Composable
private fun KooraApp(context: Context) {
    val nav = rememberNavController()
    val store = remember { LocalStore(context) }
    val repository = remember { KooraRepository() }
    var data by remember { mutableStateOf(FootballData(configured = repository.configured)) }
    var loading by remember { mutableStateOf(true) }
    var favorites by remember { mutableStateOf(store.favorites()) }

    LaunchedEffect(Unit) {
        loading = true
        data = repository.loadAll()
        loading = false
    }

    fun toggleFavorite(record: FootballRecord) {
        val updated = favorites.toMutableSet()
        if (!updated.add(record.favoriteKey)) updated.remove(record.favoriteKey)
        favorites = updated
        store.saveFavorites(updated)
    }

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: "home"
    val showBottomBar = currentRoute in setOf("home", "matches", "explore", "favorites", "settings")

    Scaffold(
        containerColor = Ink,
        contentWindowInsets = WindowInsets.navigationBars,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = Panel, contentColor = Bright) {
                    val tabs = listOf(
                        Triple("home", "الرئيسية", Icons.Filled.Home),
                        Triple("matches", "المباريات", Icons.Filled.CalendarMonth),
                        Triple("explore", "اكتشف", Icons.Filled.Explore),
                        Triple("favorites", "المفضلة", Icons.Filled.Favorite),
                        Triple("settings", "الإعدادات", Icons.Filled.Settings)
                    )
                    tabs.forEach { (route, label, icon) ->
                        NavigationBarItem(
                            selected = currentRoute == route,
                            onClick = {
                                nav.navigate(route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, maxLines = 1) },
                            alwaysShowLabel = false
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") {
                HomeScreen(data, loading, favorites, ::toggleFavorite, nav)
            }
            composable("matches") {
                MatchesScreen(data, loading, favorites, ::toggleFavorite, nav)
            }
            composable("explore") {
                ExploreScreen(data, loading, favorites, ::toggleFavorite, nav)
            }
            composable("favorites") {
                FavoritesScreen(data, favorites, ::toggleFavorite, nav)
            }
            composable("settings") {
                SettingsScreen(store, nav)
            }
            composable("notifications") {
                NotificationsScreen(nav)
            }
            composable(
                "detail/{kind}/{id}",
                arguments = listOf(navArgument("kind") { type = NavType.StringType }, navArgument("id") { type = NavType.StringType })
            ) { entry ->
                val kind = entry.arguments?.getString("kind").orEmpty()
                val id = entry.arguments?.getString("id").orEmpty()
                val record = data.all.firstOrNull { it.kind == kind && it.id == id }
                DetailScreen(record, kind, id, repository, favorites, ::toggleFavorite) {
                    nav.popBackStack()
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    data: FootballData,
    loading: Boolean,
    favorites: Set<String>,
    onFavorite: (FootballRecord) -> Unit,
    nav: NavHostController
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Header(nav)
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("كرة القدم.. كلها هنا", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Bright)
                Text("تابع ما يهمك فقط", color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
            Surface(color = Color(0xFF28251C), shape = RoundedCornerShape(14.dp)) {
                Text("BEYOND FOOTBALL", color = Gold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(22.dp))
        SectionHeader("المباريات", "عرض الكل") { nav.navigate("matches") }
        when {
            loading -> LoadingBlock()
            data.matches.isEmpty() -> EmptyPanel(
                title = if (data.configured) "لا توجد مباريات متاحة الآن" else "مصدر المباريات غير متصل",
                message = if (data.configured) "لن نعرض نتائج غير مؤكدة. تحقق مرة أخرى لاحقاً." else "سيظهر جدول المباريات الحقيقي عند ربط خادم البيانات المرخّص."
            )
            else -> data.matches.take(5).forEach { MatchCard(it) { nav.navigate("detail/${it.kind}/${it.id}") } }
        }
        Spacer(Modifier.height(22.dp))
        SectionHeader("البطولات", "استكشف") { nav.navigate("explore") }
        if (data.competitions.isEmpty()) {
            EmptyPanel("البطولات ستظهر هنا", "نعرض البطولات المتاحة من مصدر البيانات المتصل فقط.")
        } else {
            data.competitions.take(6).forEach { RecordRow(it, favorites.contains(it.favoriteKey), { onFavorite(it) }) {
                nav.navigate("detail/${it.kind}/${it.id}")
            } }
        }
        Spacer(Modifier.height(22.dp))
        SectionHeader("الأندية", "كل الأندية") { nav.navigate("explore") }
        if (data.teams.isEmpty()) EmptyPanel("لا توجد أندية محمّلة", "ستظهر الأندية وصورها الحقيقية بعد توصيل دليل الأندية.")
        else data.teams.take(5).forEach { RecordRow(it, favorites.contains(it.favoriteKey), { onFavorite(it) }) {
            nav.navigate("detail/${it.kind}/${it.id}")
        } }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MatchesScreen(
    data: FootballData,
    loading: Boolean,
    favorites: Set<String>,
    onFavorite: (FootballRecord) -> Unit,
    nav: NavHostController
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Header()
        Text("المباريات", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Bright)
        Text("مباشرة وقادمة ونتائج مؤكدة", color = Muted)
        Spacer(Modifier.height(16.dp))
        when {
            loading -> LoadingBlock()
            data.matches.isEmpty() -> EmptyPanel(
                if (data.configured) "لا توجد مباريات في المصدر حالياً" else "بيانات المباريات غير متصلة",
                "لا نضع نتائج أو توقيتات تجريبية مكان البيانات الحقيقية."
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(data.matches, key = { it.id }) { record ->
                    MatchCard(record) { nav.navigate("detail/${record.kind}/${record.id}") }
                }
            }
        }
    }
}

@Composable
private fun ExploreScreen(
    data: FootballData,
    loading: Boolean,
    favorites: Set<String>,
    onFavorite: (FootballRecord) -> Unit,
    nav: NavHostController
) {
    var category by remember { mutableStateOf("team") }
    var query by remember { mutableStateOf("") }
    val records = when (category) {
        "player" -> data.players
        "competition" -> data.competitions
        else -> data.teams
    }.filter { it.title.contains(query, ignoreCase = true) || it.subtitle.contains(query, ignoreCase = true) }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Header()
        Text("اكتشف كرة القدم", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Bright)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("ابحث عن لاعب أو نادٍ أو بطولة") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("team" to "الأندية", "player" to "اللاعبون", "competition" to "البطولات").forEach { (value, label) ->
                FilterChip(selected = category == value, onClick = { category = value }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(12.dp))
        when {
            loading -> LoadingBlock()
            records.isEmpty() -> EmptyPanel(
                if (data.configured) "لا توجد نتائج مطابقة" else "دليل البيانات غير متصل",
                "سيظهر هنا المحتوى الحقيقي فقط، ويمكنك فتح صفحة كل عنصر وإضافته للمفضلة."
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(records, key = { "${it.kind}:${it.id}" }) { record ->
                    RecordRow(record, favorites.contains(record.favoriteKey), { onFavorite(record) }) {
                        nav.navigate("detail/${record.kind}/${record.id}")
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesScreen(
    data: FootballData,
    favorites: Set<String>,
    onFavorite: (FootballRecord) -> Unit,
    nav: NavHostController
) {
    var selectedKind by remember { mutableStateOf("all") }
    val saved = data.all.filter { favorites.contains(it.favoriteKey) }
    val filtered = when (selectedKind) {
        "team" -> saved.filter { it.kind == "team" }
        "player" -> saved.filter { it.kind == "player" }
        "competition" -> saved.filter { it.kind == "competition" }
        else -> saved
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Header(nav)
        Text("مفضلتك", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Bright)
        Text("اختياراتك محفوظة على هذا الجهاز، بدون حساب.", color = Muted)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "الكل", "team" to "الأندية", "player" to "اللاعبون", "competition" to "البطولات").forEach { (value, label) ->
                FilterChip(selected = selectedKind == value, onClick = { selectedKind = value }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(12.dp))
        if (filtered.isEmpty()) {
            EmptyPanel(
                if (saved.isEmpty()) "قائمتك لم تبدأ بعد" else "لا توجد عناصر في هذا القسم",
                "افتح «اكتشف» واضغط النجمة بجانب اللاعب أو النادي أو البطولة لإضافته هنا."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(filtered, key = { it.favoriteKey }) { record ->
                    RecordRow(record, true, { onFavorite(record) }) {
                        nav.navigate("detail/${record.kind}/${record.id}")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(store: LocalStore, nav: NavHostController) {
    var enabled by remember { mutableStateOf(store.bool("alerts_enabled", true)) }
    var goals by remember { mutableStateOf(store.bool("alerts_goals", true)) }
    var starts by remember { mutableStateOf(store.bool("alerts_starts", true)) }
    var news by remember { mutableStateOf(store.bool("alerts_news", false)) }
    var general by remember { mutableStateOf(store.bool("alerts_general", true)) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Header()
        Text("الإعدادات", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Bright)
        Text("تحكم في تجربة KOORA والتنبيهات التي تهمك.", color = Muted)
        Spacer(Modifier.height(18.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Notifications, tint = Gold, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("تفضيلات الإشعارات", fontWeight = FontWeight.Bold, color = Bright)
                }
                Spacer(Modifier.height(8.dp))
                PreferenceRow("تفعيل التنبيهات", enabled) {
                    enabled = it; store.setBool("alerts_enabled", it)
                }
                PreferenceRow("أهداف الفرق المفضلة", goals, enabled) {
                    goals = it; store.setBool("alerts_goals", it)
                }
                PreferenceRow("بداية المباريات", starts, enabled) {
                    starts = it; store.setBool("alerts_starts", it)
                }
                PreferenceRow("الأخبار العاجلة", news, enabled) {
                    news = it; store.setBool("alerts_news", it)
                }
                PreferenceRow("تنبيهات عامة محدودة عند عدم اختيار مفضلة", general, enabled) {
                    general = it; store.setBool("alerts_general", it)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        EmptyPanel(
            "تخصيص الإشعارات محفوظ",
            "سيتم إرسال تنبيهات المفضلة فقط بعد ربط خدمة Push بالخادم. لا يتم تفعيل بث إشعارات عن بُعد قبل إعداد مفاتيح الخدمة وموافقة المستخدم."
        )
        Spacer(Modifier.height(18.dp))
        Text("KOORA • BEYOND FOOTBALL", color = Gold, fontWeight = FontWeight.SemiBold)
        Text("نسخة تأسيسية قيد التطوير — لا تمثل إصداراً نهائياً للنشر.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun NotificationsScreen(nav: NavHostController) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Header(nav)
        Text("مركز الإشعارات", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Bright)
        Text("تنبيهاتك المهمة في مكان واحد.", color = Muted)
        Spacer(Modifier.height(18.dp))
        EmptyPanel(
            "لا توجد إشعارات جديدة",
            "عندما يتم ربط خدمة الإشعارات، ستظهر هنا أهداف وأحداث فرقك المفضلة والأخبار التي اخترت متابعتها."
        )
        Spacer(Modifier.height(14.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().clickable { nav.navigate("settings") }
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Settings, contentDescription = null, tint = Gold)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("إعدادات التنبيهات", color = Bright, fontWeight = FontWeight.SemiBold)
                    Text("اختر الأهداف وبداية المباريات والأخبار التي تريدها", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Text("›", color = Gold, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@Composable
private fun DetailScreen(
    initial: FootballRecord?,
    kind: String,
    id: String,
    repository: KooraRepository,
    favorites: Set<String>,
    onFavorite: (FootballRecord) -> Unit,
    onBack: () -> Unit
) {
    var record by remember(initial, kind, id) { mutableStateOf(initial) }
    var loading by remember { mutableStateOf(initial == null) }
    var selectedSection by remember(kind, id) { mutableStateOf("overview") }
    LaunchedEffect(kind, id) {
        if (record == null) {
            record = repository.detail(kind, id)
            loading = false
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع", tint = Bright) }
            Text("ملف التفاصيل", color = Bright, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("KOORA", color = Gold, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(10.dp))
        when {
            loading -> LoadingBlock()
            record == null -> EmptyPanel("التفاصيل غير متاحة", "لم تصل بيانات مؤكدة لهذا العنصر من الخادم.")
            else -> {
                val item = record!!
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.imageUrl != null) {
                        AsyncImage(model = item.imageUrl, contentDescription = item.title, modifier = Modifier.size(76.dp).clip(RoundedCornerShape(20.dp)))
                    } else {
                        Surface(color = Panel2, shape = RoundedCornerShape(20.dp)) {
                            Icon(Icons.Filled.SportsSoccer, contentDescription = null, tint = Gold, modifier = Modifier.padding(22.dp).size(32.dp))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.headlineSmall, color = Bright, fontWeight = FontWeight.Bold)
                        if (item.subtitle.isNotBlank()) Text(item.subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
                        Text(
                            when (item.kind) {
                                "match" -> "مباراة"
                                "team" -> "نادي / منتخب"
                                "player" -> "لاعب"
                                else -> "بطولة"
                            },
                            color = Gold,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (item.kind == "match") {
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(item.status ?: "حالة المباراة غير متاحة", color = Green, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Text(item.score ?: "— : —", color = Gold, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                            Text(item.kickoff ?: "موعد المباراة غير متاح", color = Muted)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { onFavorite(item) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (favorites.contains(item.favoriteKey)) Panel2 else Gold,
                        contentColor = if (favorites.contains(item.favoriteKey)) Bright else Ink
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(if (favorites.contains(item.favoriteKey)) Icons.Filled.Star else Icons.Outlined.StarBorder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (favorites.contains(item.favoriteKey)) "إزالة من المفضلة" else "إضافة إلى المفضلة")
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = selectedSection == "overview", onClick = { selectedSection = "overview" }, label = { Text("نظرة عامة") })
                    if (item.kind == "match") {
                        FilterChip(selected = selectedSection == "events", onClick = { selectedSection = "events" }, label = { Text("الأحداث") })
                        FilterChip(selected = selectedSection == "lineups", onClick = { selectedSection = "lineups" }, label = { Text("التشكيل") })
                    }
                    FilterChip(selected = selectedSection == "stats", onClick = { selectedSection = "stats" }, label = { Text("الإحصائيات") })
                }
                Spacer(Modifier.height(12.dp))
                when (selectedSection) {
                    "events" -> {
                        if (item.events.isEmpty()) EmptyPanel("لا توجد أحداث متاحة", "ستظهر الأهداف والبطاقات والتبديلات عندما يرسلها مصدر المباراة.")
                        else item.events.forEachIndexed { index, event -> DetailLine(index + 1, event, "حدث المباراة") }
                    }
                    "lineups" -> {
                        if (item.lineups.isEmpty()) EmptyPanel("التشكيل غير متاح بعد", "لا نعرض تشكيلاً متوقعاً على أنه رسمي. ستظهر الأسماء بعد تأكيدها من المصدر.")
                        else item.lineups.forEachIndexed { index, player -> DetailLine(index + 1, player, "لاعب") }
                    }
                    "stats" -> {
                        if (item.statistics.isEmpty()) EmptyPanel("الإحصائيات غير متاحة", "ستظهر الأرقام مثل الاستحواذ والتسديدات والتمريرات عندما يوفرها مصدر البيانات.")
                        else item.statistics.forEach { (label, value) -> FactRow(label, value) }
                    }
                    else -> {
                        if (!item.description.isNullOrBlank()) {
                            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                                Text(item.description, color = Bright, modifier = Modifier.padding(16.dp))
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                        if (item.facts.isEmpty()) EmptyPanel("لا توجد معلومات إضافية", "نحافظ على دقة المحتوى؛ لن نملأ الصفحة بمعلومات غير متوفرة.")
                        else item.facts.forEach { (label, value) -> FactRow(label, value) }
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Muted, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(value, color = Bright, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DetailLine(number: Int, title: String, subtitle: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp)) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Panel2, shape = CircleShape) {
                Text(number.toString(), color = Gold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = Bright, fontWeight = FontWeight.Medium)
                Text(subtitle, color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun Header(nav: NavHostController? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = RoundedCornerShape(13.dp), color = Gold) {
            Icon(Icons.Filled.SportsSoccer, contentDescription = null, tint = Ink, modifier = Modifier.padding(9.dp).size(22.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("KOORA", color = Bright, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
            Text("BEYOND FOOTBALL", color = Gold, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { nav?.navigate("notifications") }) {
            Icon(Icons.Filled.Notifications, contentDescription = "مركز الإشعارات", tint = Gold)
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Bright, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(action, color = Gold, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onClick).padding(6.dp))
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun MatchCard(record: FootballRecord, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFF252E3B))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(record.status ?: "الموعد غير متاح", color = Green, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                if (record.subtitle.isNotBlank()) Text(record.subtitle, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(record.home ?: record.title, color = Bright, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(record.score ?: "—", color = Gold, fontWeight = FontWeight.Bold)
                Text(record.away ?: "", color = Bright, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
            if (!record.kickoff.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(record.kickoff, color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun RecordRow(record: FootballRecord, favorite: Boolean, onFavorite: () -> Unit, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(Panel2), contentAlignment = Alignment.Center) {
                if (record.imageUrl != null) {
                    AsyncImage(model = record.imageUrl, contentDescription = record.title, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(13.dp)))
                } else {
                    Icon(if (record.kind == "player") Icons.Filled.Star else Icons.Filled.SportsSoccer, contentDescription = null, tint = Gold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).clickable(onClick = onClick)) {
                Text(record.title, color = Bright, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (record.subtitle.isNotBlank()) Text(record.subtitle, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onFavorite) {
                Icon(if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder, contentDescription = if (favorite) "إزالة من المفضلة" else "إضافة للمفضلة", tint = if (favorite) Gold else Muted)
            }
            Text("›", color = Gold, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 6.dp))
        }
    }
}

@Composable
private fun EmptyPanel(title: String, message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.SportsSoccer, contentDescription = null, tint = Gold, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, color = Bright, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(message, color = Muted, style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun LoadingBlock() {
    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Gold, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun PreferenceRow(title: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = if (enabled) Bright else Muted, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
