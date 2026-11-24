package com.koora.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

private val DBg get() = KoraxThemePalette.background
private val DCard get() = KoraxThemePalette.card
private val DCard2 get() = KoraxThemePalette.card2
private val DText get() = KoraxThemePalette.text
private val DMuted get() = KoraxThemePalette.muted
private val DBorder get() = KoraxThemePalette.border
private val DAccent get() = KoraxThemePalette.accent

private val favoriteTypes = listOf(
    "all" to "الكل",
    "league" to "دوري",
    "tournament" to "بطولة",
    "club" to "نادي",
    "national" to "منتخب",
    "player" to "لاعب",
    "coach" to "مدرب"
)

private fun encodeQuery(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

private fun JSONObject.firstString(vararg keys: String): String {
    for (key in keys) {
        val value = opt(key)
        when (value) {
            null, JSONObject.NULL -> Unit
            is JSONObject -> {
                val nested = value.optString("name").ifBlank { value.optString("title") }
                if (nested.isNotBlank()) return nested
            }
            else -> value.toString().takeIf { it.isNotBlank() && it != "null" }?.let { return it }
        }
    }
    return ""
}

private fun JSONObject.firstImage(): String {
    val keys = listOf("imageUrl", "image_path", "imagePath", "image_url", "logoUrl", "logo", "logo_url", "logo_path", "crest", "crestUrl", "crest_url", "badge", "emblem", "photo", "photo_url", "thumbnail")
    return keys.firstNotNullOfOrNull { key ->
        optString(key).takeIf { it.startsWith("https://") }
    }.orEmpty()
}

private fun JSONObject.asFavorite(kind: String): KoraxFavorite? {
    val nested = optJSONObject("team") ?: optJSONObject("player")
        ?: optJSONObject("competition") ?: optJSONObject("league") ?: this
    val name = nested.firstString("name", "title", "common_name", "display_name", "short_name")
    val urlValue = nested.firstString("url", "href", "link")
    val urlSlug = Regex("/([A-Za-z0-9_-]{4,})/?(?:\\?.*)?$").find(urlValue)?.groupValues?.getOrNull(1).orEmpty()
    val slug = nested.firstString("slug", "url_slug", "team_slug", "player_slug", "competition_slug", "league_slug")
        .ifBlank { urlSlug }
        .ifBlank { nested.firstString("id") }
    val id = nested.firstString("id").ifBlank { slug }
    if (name.isBlank() || slug.isBlank() || id.isBlank()) return null
    val country = nested.firstString("country", "country_name", "nationality")
    return KoraxFavorite(
        id = id,
        slug = slug,
        name = name,
        kind = kind,
        imageUrl = nested.firstImage(),
        country = country,
        notify = true
    )
}

private suspend fun searchFavoriteItems(kind: String, query: String): List<KoraxFavorite> {
    if (kind == "all") {
        if (query.length < 2) return emptyList()
        return listOf("league", "tournament", "club", "national", "player", "coach")
            .flatMap { searchFavoriteItems(it, query) }
            .distinctBy { it.kind + ":" + it.id }
            .take(60)
    }
    val encoded = encodeQuery(query)
    val path = when (kind) {
        "league", "tournament" -> if (query.length >= 2) "/api/competitions?search=$encoded" else "/api/competitions"
        "player" -> if (query.length >= 2) "/api/players?search=$encoded&types=player" else return emptyList()
        "coach" -> if (query.length >= 2) "/api/players?search=$encoded&types=coach" else return emptyList()
        else -> if (query.length >= 2) "/api/teams?search=$encoded" else return emptyList()
    }
    val payload = apiJson(path)
    val rows = payload.optJSONArray("data") ?: JSONArray()
    val localCompetitionFallback = kind in setOf("league", "tournament") &&
        payload.optString("source") != "sportscore-search"
    return (0 until rows.length()).mapNotNull { index ->
        val row = rows.optJSONObject(index) ?: return@mapNotNull null
        val competitionSlug = row.firstString(
            "slug", "url_slug", "competition_slug", "league_slug", "id"
        ).lowercase()
        val declaredCompetitionKind = row.optString("kind").ifBlank {
            if (competitionSlug in setOf("champions-league", "world-cup")) "tournament" else "league"
        }
        val teamDescriptor = listOf(
            row.optString("type"), row.optString("entity_type"), row.optString("team_type"),
            row.optString("category"), row.optString("gender")
        ).joinToString(" ").lowercase()
        val appearsNational = row.optBoolean("national") || row.optBoolean("is_national") ||
            row.optBoolean("national_team") ||
            Regex("national[_ -]team|منتخب").containsMatchIn(teamDescriptor)
        val actualKind = when (kind) {
            "league", "tournament" -> declaredCompetitionKind
            "club", "national" -> if (
                row.optString("koraxKind") == "national" || appearsNational
            ) "national" else "club"
            else -> kind
        }
        row.asFavorite(actualKind)
    }.filter { it.kind == kind }
        .filter {
            !localCompetitionFallback || query.isBlank() ||
                it.name.contains(query, ignoreCase = true) ||
                it.country.contains(query, ignoreCase = true)
        }
        .distinctBy { it.kind + ":" + it.id }.take(50)
}

@Composable
private fun DiscoveryHeader(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع", tint = DAccent)
            }
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = DText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = DMuted, fontSize = 10.sp)
        }
        if (onSearch != null) {
            IconButton(onClick = onSearch, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Search, contentDescription = "بحث في المفضلة", tint = DAccent)
            }
        }
        if (onRefresh != null) {
            IconButton(onClick = onRefresh, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = "تحديث", tint = DAccent)
            }
        }
    }
}

@Composable
internal fun KoraxFavoriteSelectionScreen(
    isFirstRun: Boolean,
    onFinish: () -> Unit,
    onOpenProfile: (KoraxFavorite) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val selected = remember {
        mutableStateListOf<KoraxFavorite>().apply { addAll(KoraxFavoriteStore.all(context)) }
    }
    var kind by rememberSaveable { mutableStateOf(if (isFirstRun) "league" else "all") }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<KoraxFavorite>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun search() {
        loading = true
        error = ""
        val found = runCatching { searchFavoriteItems(kind, query.trim()) }
        results = found.getOrDefault(emptyList())
        error = found.exceptionOrNull()?.message.orEmpty()
        loading = false
    }

    LaunchedEffect(kind) {
        query = ""
        search()
    }
    LaunchedEffect(kind, query) {
        if (query.trim().length >= 2) {
            kotlinx.coroutines.delay(420)
            search()
        }
    }

    Column(Modifier.fillMaxSize().background(DBg)) {
        DiscoveryHeader(
            if (isFirstRun) "كوّن عالمك الكروي" else "استكشف كرة القدم",
            if (isFirstRun) "اختر الدوريات والأندية واللاعبين لتصلك أهم تنبيهاتهم" else "ابحث عن أي دوري أو بطولة أو نادٍ أو منتخب أو لاعب",
            onBack = if (isFirstRun) null else onFinish
        )
        if (isFirstRun) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("تابع ما يهمك فقط", color = DText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("اختياراتك تحفظ على هذا الجهاز ويمكن تعديلها لاحقًا من المفضلة.", color = DMuted, fontSize = 10.sp)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            favoriteTypes.forEach { (key, label) ->
                val categoryIcon = when (key) {
                    "all" -> "🔎"
                    "league" -> "🏆"
                    "tournament" -> "🥇"
                    "club" -> "⚽"
                    "national" -> "🌍"
                    "coach" -> "🧑‍🏫"
                    else -> "👤"
                }
                FilterChip(
                    selected = kind == key,
                    onClick = { kind = key },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(categoryIcon, fontSize = 11.sp)
                            Spacer(Modifier.width(4.dp))
                            Text(label, fontSize = 10.sp, maxLines = 1)
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DAccent,
                        selectedLabelColor = Color.Black,
                        labelColor = DMuted
                    )
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(15.dp),
                placeholder = {
                    Text(
                        when (kind) {
                            "all" -> "ابحث عن لاعب أو نادٍ أو منتخب أو دوري..."
                            "league" -> "ابحث عن دوري مثل الإنجليزي..."
                            "tournament" -> "ابحث عن بطولة مثل كأس العالم..."
                            "club" -> "ابحث عن نادٍ مثل الأهلي..."
                            "national" -> "ابحث عن منتخب مثل مصر..."
                            "coach" -> "ابحث عن مدرب مثل جوارديولا..."
                            else -> "ابحث عن لاعب مثل محمد صلاح..."
                        },
                        color = DMuted,
                        fontSize = 11.sp
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = DAccent) }
            )
            Spacer(Modifier.width(7.dp))
            Button(
                onClick = { scope.launch { search() } },
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DAccent, contentColor = Color.Black),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)
            ) { Text("بحث", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
        }

        if (query.isBlank() && kind in setOf("all", "club", "national", "player")) {
            val quick = when (kind) {
                "all" -> listOf("محمد صلاح", "Real Madrid", "مصر", "الدوري الإنجليزي", "Champions League")
                "club" -> listOf("الأهلي", "الزمالك", "Real Madrid", "Barcelona", "Liverpool")
                "national" -> listOf("مصر", "Brazil", "Argentina", "France", "Morocco")
                else -> listOf("محمد صلاح", "Messi", "Ronaldo", "Mbappe", "Haaland")
            }
            Text("عمليات بحث سريعة", color = DMuted, fontSize = 10.sp, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                quick.take(3).forEach { value ->
                    FilterChip(
                        selected = false,
                        onClick = { query = value },
                        label = { Text(value, fontSize = 9.sp) }
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                quick.drop(3).forEach { value ->
                    FilterChip(
                        selected = false,
                        onClick = { query = value; scope.launch { searchFavoriteItems(kind, value).also { results = it } } },
                        label = { Text(value, fontSize = 9.sp) }
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (query.isBlank()) "العناصر المتاحة" else "اقتراحات البحث", color = DText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text("المحدد: ${selected.size}", color = DAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        when {
            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DAccent)
            }
            error.isNotBlank() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("تعذر البحث: $error", color = DMuted, fontSize = 11.sp, modifier = Modifier.padding(20.dp))
            }
            results.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    if (query.trim().length < 2) "اكتب حرفين على الأقل لعرض اقتراحات فورية" else "لا توجد نتائج مطابقة في البيانات المتاحة حاليًا",
                    color = DMuted, fontSize = 11.sp
                )
            }
            else -> LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(results, key = { it.kind + ":" + it.id }) { item ->
                    val exists = selected.any { it.kind == item.kind && it.id == item.id }
                    FavoriteSearchResultCard(
                        item = item,
                        selected = exists,
                        onOpen = { onOpenProfile(item) },
                        onToggle = {
                            if (exists) selected.removeAll { it.kind == item.kind && it.id == item.id }
                            else selected.add(item)
                            KoraxFavoriteStore.save(context, selected)
                        }
                    )
                }
            }
        }

        if (selected.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().background(DCard).padding(horizontal = 13.dp, vertical = 9.dp)
            ) {
                Text("مفضلتك", color = DText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.height(5.dp))
                selected.take(4).forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.name, color = DText, fontSize = 10.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(favoriteTypes.firstOrNull { it.first == item.kind }?.second ?: "متابعة", color = DMuted, fontSize = 9.sp)
                        TextButton(onClick = {
                            selected.remove(item)
                            KoraxFavoriteStore.save(context, selected)
                        }) { Text("إزالة", color = DMuted, fontSize = 9.sp) }
                    }
                }
                if (selected.size > 4) Text("+ ${selected.size - 4} عناصر أخرى", color = DMuted, fontSize = 9.sp)
            }
        }

        Button(
            onClick = {
                KoraxFavoriteStore.save(context, selected)
                if (isFirstRun) KoraxFavoriteStore.setSetupComplete(context, true)
                onFinish()
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DAccent, contentColor = Color.Black)
        ) {
            Text(if (isFirstRun) "ابدأ استخدام KORAX" else "حفظ اختياراتي", fontWeight = FontWeight.ExtraBold)
        }
        if (isFirstRun) {
            TextButton(onClick = {
                KoraxFavoriteStore.save(context, selected)
                KoraxFavoriteStore.setSetupComplete(context, true)
                onFinish()
            }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("تخطي الآن واختيار المفضلة لاحقًا", color = DMuted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun FavoriteSearchResultCard(
    item: KoraxFavorite,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DCard),
        border = BorderStroke(1.dp, if (selected) DAccent else DBorder)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(49.dp).clip(RoundedCornerShape(12.dp)).background(DCard2),
                contentAlignment = Alignment.Center
            ) {
                if (item.imageUrl.isNotBlank() && KoraxSettingsStore.getBoolean(
                        androidx.compose.ui.platform.LocalContext.current, "show_images", true
                    ) && !KoraxSettingsStore.getBoolean(
                        androidx.compose.ui.platform.LocalContext.current, "data_saver", false
                    )
                ) {
                    coil.compose.AsyncImage(
                        model = item.imageUrl, contentDescription = item.name,
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(item.name.take(1), color = DAccent, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                Text(item.name, color = DText, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(
                        favoriteTypes.firstOrNull { it.first == item.kind }?.second ?: "فريق",
                        item.country
                    ).filter { it.isNotBlank() }.joinToString(" • "),
                    color = DMuted, fontSize = 9.sp
                )
            }
            IconButton(onClick = onToggle) {
                Icon(
                    if (selected) Icons.Filled.Check else Icons.Filled.Favorite,
                    contentDescription = if (selected) "إزالة من المفضلة" else "إضافة للمفضلة",
                    tint = if (selected) DAccent else DMuted
                )
            }
        }
    }
}

@Composable
internal fun KoraxFavoritesScreen(
    onOpenProfile: (KoraxFavorite) -> Unit,
    onAddMore: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var favorites by remember { mutableStateOf(KoraxFavoriteStore.all(context)) }
    var filter by rememberSaveable { mutableStateOf("all") }
    LaunchedEffect(Unit) { favorites = KoraxFavoriteStore.all(context) }
    val visibleFavorites = if (filter == "all") favorites else favorites.filter { it.kind == filter }
    Column(Modifier.fillMaxSize().background(DBg)) {
        DiscoveryHeader(
            "المفضلة",
            "تابع كل ما يهمك من مكان واحد",
            onRefresh = { favorites = KoraxFavoriteStore.all(context) },
            onSearch = onAddMore
        )
        LazyRow(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(favoriteTypes) { (key, label) ->
                val categoryIcon = when (key) {
                    "all" -> "✦"
                    "league" -> "🏆"
                    "tournament" -> "🥇"
                    "club" -> "⚽"
                    "national" -> "🌍"
                    else -> "👤"
                }
                FilterChip(
                    selected = filter == key,
                    onClick = { filter = key },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(categoryIcon, fontSize = 11.sp)
                            Spacer(Modifier.width(4.dp))
                            Text(if (key == "all") "الكل" else label, fontSize = 10.sp)
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DAccent,
                        selectedLabelColor = Color.Black,
                        labelColor = DMuted
                    )
                )
            }
        }
        if (favorites.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("♡", color = DAccent, fontSize = 42.sp, fontWeight = FontWeight.Black)
                    Text("لم تضف مفضلات بعد", color = DText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("اختر أنديتك ودورياتك ولاعبيك لتظهر تحديثاتهم هنا.", color = DMuted, fontSize = 10.sp, modifier = Modifier.padding(10.dp))
                    Button(onClick = onAddMore, colors = ButtonDefaults.buttonColors(containerColor = DAccent, contentColor = Color.Black)) {
                        Text("اختيار المفضلة")
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${visibleFavorites.size} عنصر في هذا القسم", color = DMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onAddMore) { Text("إضافة عناصر +", color = DAccent) }
            }
            if (visibleFavorites.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("لا توجد عناصر في هذا القسم بعد", color = DMuted, fontSize = 11.sp)
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 13.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleFavorites, key = { it.kind + ":" + it.id }) { item ->
                        FavoriteItemCard(
                            item = item,
                            onOpen = { onOpenProfile(item) },
                            onNotification = { enabled ->
                                KoraxFavoriteStore.updateNotification(context, item, enabled)
                                favorites = KoraxFavoriteStore.all(context)
                            },
                            onRemove = {
                                KoraxFavoriteStore.toggle(context, item)
                                favorites = KoraxFavoriteStore.all(context)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteItemCard(
    item: KoraxFavorite,
    onOpen: () -> Unit,
    onNotification: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DCard),
        border = BorderStroke(1.dp, DBorder)
    ) {
        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(45.dp).clip(RoundedCornerShape(11.dp)).background(DCard2), contentAlignment = Alignment.Center) {
                if (item.imageUrl.isNotBlank() && KoraxSettingsStore.getBoolean(
                        androidx.compose.ui.platform.LocalContext.current, "show_images", true
                    )
                ) coil.compose.AsyncImage(model = item.imageUrl, contentDescription = item.name, modifier = Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
                else Text(item.name.take(1), color = DAccent, fontSize = 19.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, color = DText, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(favoriteTypes.firstOrNull { it.first == item.kind }?.second ?: "فريق", item.country)
                        .filter { it.isNotBlank() }.joinToString(" • "),
                    color = DMuted, fontSize = 9.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("تنبيه هذا العنصر", color = DMuted, fontSize = 9.sp)
                    Switch(checked = item.notify, onCheckedChange = onNotification)
                }
            }
            TextButton(onClick = onRemove) { Text("إزالة", color = DMuted, fontSize = 9.sp) }
        }
    }
}

private data class ProfileFact(val label: String, val value: String)

private val profileLabels = mapOf(
    "country" to "الدولة", "nationality" to "الجنسية", "founded" to "سنة التأسيس",
    "venue" to "الملعب", "stadium" to "الملعب", "capacity" to "السعة",
    "coach" to "المدرب", "manager" to "المدرب", "position" to "المركز",
    "height" to "الطول", "weight" to "الوزن", "date_of_birth" to "تاريخ الميلاد",
    "birthdate" to "تاريخ الميلاد", "birth_date" to "تاريخ الميلاد", "website" to "الموقع الرسمي", "age" to "العمر", "shirt_number" to "رقم القميص",
    "market_value" to "القيمة السوقية", "price" to "القيمة السوقية",
    "appearances" to "المشاركات", "goals" to "الأهداف", "assists" to "التمريرات الحاسمة",
    "yellow_cards" to "البطاقات الصفراء", "red_cards" to "البطاقات الحمراء",
    "wins" to "الانتصارات", "draws" to "التعادلات", "losses" to "الهزائم",
    "points" to "النقاط", "played" to "لعب", "goal_difference" to "فارق الأهداف",
    "matches_with_lineup" to "مباريات ظهر في تشكيلتها", "matches_in_cache" to "مباريات ضمن البيانات المحفوظة"
)

private fun collectProfileFacts(value: JSONObject, depth: Int = 0): List<ProfileFact> {
    if (depth > 3) return emptyList()
    val output = mutableListOf<ProfileFact>()
    val iterator = value.keys()
    while (iterator.hasNext()) {
        val key = iterator.next()
        val item = value.opt(key) ?: continue
        when (item) {
            is JSONObject -> {
                val directName = item.firstString("name", "title")
                if (directName.isNotBlank() && key in setOf("country", "nationality", "venue", "stadium", "coach", "manager", "position")) {
                    output.add(ProfileFact(profileLabels[key] ?: key, directName))
                } else output.addAll(collectProfileFacts(item, depth + 1))
            }
            is JSONArray -> Unit
            JSONObject.NULL -> Unit
            else -> {
                val text = item.toString().trim()
                if (text.isNotBlank() && text != "null" && key !in setOf("id", "slug", "url", "image_path", "imageUrl", "logoUrl", "name", "title", "short_code")) {
                    val label = profileLabels[key] ?: key.replace('_', ' ').replaceFirstChar { it.uppercase() }
                    output.add(ProfileFact(label, text))
                }
            }
        }
    }
    return output.distinctBy { it.label }.take(32)
}

private fun unwrapProfile(payload: JSONObject): JSONObject {
    val data = payload.opt("data")
    val root = when (data) {
        is JSONObject -> data
        is JSONArray -> data.optJSONObject(0) ?: JSONObject()
        else -> payload
    }
    for (key in listOf("team", "player", "competition", "league")) {
        root.optJSONObject(key)?.let { return it }
    }
    return root
}

private fun findProfileImage(value: JSONObject, depth: Int = 0): String {
    if (depth > 4) return ""
    val direct = value.firstImage()
    if (direct.isNotBlank()) return direct
    val iterator = value.keys()
    while (iterator.hasNext()) {
        when (val child = value.opt(iterator.next())) {
            is JSONObject -> {
                val found = findProfileImage(child, depth + 1)
                if (found.isNotBlank()) return found
            }
            else -> Unit
        }
    }
    return ""
}

private fun rosterItems(value: JSONObject): List<KoraxFavorite> {
    val keys = listOf("players", "squad", "squads", "lineup", "members")
    for (key in keys) {
        val array = value.optJSONArray(key) ?: continue
        val result = (0 until array.length()).mapNotNull { index ->
            val row = array.optJSONObject(index) ?: return@mapNotNull null
            val favorite = row.asFavorite("player") ?: row.optJSONObject("player")?.asFavorite("player")
            favorite
        }
        if (result.isNotEmpty()) return result
    }
    return emptyList()
}

@Composable
internal fun KoraxEntityProfileScreen(
    favorite: KoraxFavorite,
    onBack: () -> Unit,
    onOpenPlayer: (KoraxFavorite) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var profile by remember(favorite.kind, favorite.slug) { mutableStateOf<JSONObject?>(null) }
    var loading by remember(favorite.kind, favorite.slug) { mutableStateOf(true) }
    var error by remember(favorite.kind, favorite.slug) { mutableStateOf("") }
    var following by remember(favorite.kind, favorite.slug) { mutableStateOf(KoraxFavoriteStore.isFavorite(context, favorite)) }
    var notify by remember(favorite.kind, favorite.slug) {
        mutableStateOf(KoraxFavoriteStore.all(context).firstOrNull { it.kind == favorite.kind && it.id == favorite.id }?.notify ?: true)
    }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        loading = true
        error = ""
        val endpoint = when (favorite.kind) {
            "league", "tournament" -> "/api/competitions/${encodeQuery(favorite.slug)}"
            "player", "coach" -> "/api/players/${encodeQuery(favorite.slug)}"
            else -> "/api/teams/${encodeQuery(favorite.slug)}"
        }
        val result = runCatching { apiJson(endpoint) }
        profile = result.getOrNull()?.let(::unwrapProfile)
        error = result.exceptionOrNull()?.message.orEmpty()
        loading = false
    }
    LaunchedEffect(favorite.kind, favorite.slug) { refresh() }

    val root = profile
    val image = root?.let(::findProfileImage).orEmpty().ifBlank { favorite.imageUrl }
    val facts = root?.let(::collectProfileFacts).orEmpty()
    val roster = root?.let(::rosterItems).orEmpty()

    Column(Modifier.fillMaxSize().background(DBg)) {
        DiscoveryHeader(
            when (favorite.kind) {
                "league" -> "ملف الدوري"
                "tournament" -> "ملف البطولة"
                "club" -> "ملف النادي"
                "national" -> "ملف المنتخب"
                "coach" -> "ملف المدرب"
                else -> "ملف اللاعب"
            },
            "معلومات وإحصائيات من مصدر البيانات",
            onBack = onBack,
            onRefresh = { scope.launch { refresh() } }
        )
        if (loading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DAccent)
            }
        } else if (root == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("تعذر تحميل الملف الشخصي\n$error", color = DMuted, fontSize = 11.sp, modifier = Modifier.padding(20.dp))
            }
        } else {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)
            ) {
                val profileName = root.firstString("name", "title").ifBlank { favorite.name }
                val profileType = favoriteTypes.firstOrNull { it.first == favorite.kind }?.second ?: "فريق"
                val profileCountry = root.firstString("country", "country_name", "nationality").ifBlank { favorite.country }
                val profilePosition = root.firstString("position", "position_name", "role")
                val profileClub = root.firstString("team_name", "club_name", "team")
                val profileSubtitle = listOf(profileType, profileCountry, profilePosition, profileClub)
                    .filter { it.isNotBlank() }.distinct().joinToString(" • ")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = DCard),
                    border = BorderStroke(1.dp, DBorder)
                ) {
                    Box(
                        Modifier.fillMaxWidth()
                            .background(Brush.horizontalGradient(listOf(Color(0xFF1D2B49), DCard)))
                            .padding(14.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(104.dp).clip(RoundedCornerShape(18.dp)).background(DCard2),
                                contentAlignment = Alignment.Center
                            ) {
                                if (image.isNotBlank() && KoraxSettingsStore.getBoolean(context, "show_images", true)) {
                                    coil.compose.AsyncImage(
                                        model = image, contentDescription = profileName,
                                        modifier = Modifier.fillMaxSize().padding(5.dp), contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Text(profileName.take(1), color = DAccent, fontSize = 42.sp, fontWeight = FontWeight.Black)
                                }
                            }
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text(profileType.uppercase(Locale.ROOT), color = DAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(5.dp))
                                Text(profileName, color = DText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (profileSubtitle.isNotBlank()) {
                                    Spacer(Modifier.height(5.dp))
                                    Text(profileSubtitle, color = DMuted, fontSize = 9.sp, lineHeight = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        if (following) {
                                            KoraxFavoriteStore.toggle(context, favorite)
                                            following = false
                                        } else {
                                            KoraxFavoriteStore.toggle(context, favorite.copy(notify = notify))
                                            following = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (following) DCard2 else DAccent,
                                        contentColor = if (following) DText else Color.Black
                                    ),
                                    shape = RoundedCornerShape(11.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    Text(if (following) "✓ في المفضلة" else "♡ أضف للمفضلة", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                }
                                if (following) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("تنبيهات هذا العنصر", color = DMuted, fontSize = 9.sp)
                                        Switch(checked = notify, onCheckedChange = {
                                            notify = it
                                            KoraxFavoriteStore.updateNotification(context, favorite, it)
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
                if (facts.isNotEmpty()) {
                    Text("المعلومات والتفاصيل", color = DText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        facts.forEach { fact ->
                            Row(
                                Modifier.fillMaxWidth().background(DCard, RoundedCornerShape(12.dp)).padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(fact.label, color = DMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                                Text(fact.value, color = DText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (favorite.kind == "league" || favorite.kind == "tournament") {
                    Text("جدول الترتيب", color = DText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                    val entries = findTableEntries(root)
                    if (entries.isEmpty()) {
                        Text("لم يوفر المصدر جدول ترتيب لهذه البطولة حاليًا.", color = DMuted, fontSize = 10.sp)
                    } else {
                        entries.take(30).forEachIndexed { index, row ->
                            val team = row.firstString("name", "team_name", "participant_name").ifBlank { "فريق ${index + 1}" }
                            val pts = row.firstString("points", "pts")
                            val played = row.firstString("played", "matches", "played_matches")
                            val rank = row.firstString("position", "rank").ifBlank { (index + 1).toString() }
                            Row(Modifier.fillMaxWidth().background(DCard, RoundedCornerShape(11.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(rank, color = DAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(25.dp))
                                Text(team, color = DText, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("لعب ${played.ifBlank { "—" }}", color = DMuted, fontSize = 9.sp)
                                Spacer(Modifier.width(8.dp))
                                Text("${pts.ifBlank { "—" }} نقطة", color = DAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                if (roster.isNotEmpty()) {
                    Text(if (favorite.kind == "club" || favorite.kind == "national") "لاعبون ظهروا في التشكيلات المتاحة" else "اللاعبون", color = DText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                    roster.take(35).forEach { player ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenPlayer(player) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(DCard2), contentAlignment = Alignment.Center) {
                                if (player.imageUrl.isNotBlank()) coil.compose.AsyncImage(model = player.imageUrl, contentDescription = player.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                else Text(player.name.take(1), color = DAccent, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(9.dp))
                            Text(player.name, color = DText, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            Text("عرض الملف ←", color = DAccent, fontSize = 9.sp)
                        }
                        Divider(color = DBorder)
                    }
                }
                Text(
                    "المعلومات المعروضة هي الحقول التي أعادها مزوّد البيانات لهذا العنصر. قد لا تتوفر بعض الإحصائيات أو الصور لكل موسم أو بطولة.",
                    color = DMuted, fontSize = 9.sp, modifier = Modifier.padding(vertical = 15.dp)
                )
                Text(
                    "Powered by SportScore",
                    color = DMuted,
                    fontSize = 9.sp,
                    modifier = Modifier.fillMaxWidth().clickable {
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://sportscore.com/"))) }
                    }.padding(vertical = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

private fun findTableEntries(root: JSONObject): List<JSONObject> {
    val keys = listOf("standings", "table", "rows", "teams", "data", "standings_table")
    for (key in keys) {
        when (val value = root.opt(key)) {
            is JSONArray -> {
                val rows = (0 until value.length()).mapNotNull { value.optJSONObject(it) }
                if (rows.isNotEmpty()) return rows
            }
            is JSONObject -> findTableEntries(value).takeIf { it.isNotEmpty() }?.let { return it }
        }
    }
    val iterator = root.keys()
    while (iterator.hasNext()) {
        val value = root.opt(iterator.next())
        if (value is JSONObject) findTableEntries(value).takeIf { it.isNotEmpty() }?.let { return it }
        if (value is JSONArray) {
            for (index in 0 until value.length()) {
                val child = value.optJSONObject(index) ?: continue
                findTableEntries(child).takeIf { it.isNotEmpty() }?.let { return it }
            }
        }
    }
    return emptyList()
}

@Composable
internal fun KoraxSettingsScreen(
    onBack: () -> Unit,
    onOpenNotifications: () -> Unit,
    onManageFavorites: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    var theme by remember { mutableStateOf(KoraxSettingsStore.theme(context)) }
    var showImages by remember { mutableStateOf(KoraxSettingsStore.getBoolean(context, "show_images", true)) }
    var dataSaver by remember { mutableStateOf(KoraxSettingsStore.getBoolean(context, "data_saver", false)) }
    var backgroundUpdates by remember { mutableStateOf(KoraxSettingsStore.getBoolean(context, "background_updates", true)) }
    var sounds by remember { mutableStateOf(KoraxSmartAlerts.soundEnabled(context)) }
    var infoDialog by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(DBg).verticalScroll(rememberScrollState()).padding(horizontal = 15.dp)) {
        DiscoveryHeader("الإعدادات", "تحكم كامل في تجربة KORAX", onBack = onBack)
        Text("المظهر", color = DText, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 7.dp))
        Card(colors = CardDefaults.cardColors(containerColor = DCard), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, DBorder)) {
            Column(Modifier.padding(12.dp)) {
                listOf("dark" to "داكن", "light" to "فاتح", "system" to "حسب الجهاز").forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().clickable {
                        theme = value
                        KoraxSettingsStore.setTheme(context, value)
                        KoraxThemePalette.applyLightMode(
                            when (value) {
                                "light" -> true
                                "system" -> !systemDark
                                else -> false
                            }
                        )
                    }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = DText, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        if (theme == value) Text("✓", color = DAccent, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        Text("التنبيهات", color = DText, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 17.dp, bottom = 7.dp))
        SettingsRow("إعدادات الإشعارات الذكية", "أنواع الأحداث والأصوات وسجل التنبيهات", onOpenNotifications)
        SettingsSwitchRow("المؤثرات الصوتية", "أصوات مستقلة لأحداث كرة القدم", sounds, {
            sounds = it
            KoraxSmartAlerts.setSoundEnabled(context, it)
        })
        SettingsSwitchRow("التحديث في الخلفية", "فحص المباريات حتى عند عدم فتح التطبيق", backgroundUpdates, {
            backgroundUpdates = it
            KoraxSettingsStore.setBoolean(context, "background_updates", it)
            KoraxSmartAlerts.setBackgroundUpdates(context, it)
        })
        Text("البيانات والعرض", color = DText, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 17.dp, bottom = 7.dp))
        SettingsSwitchRow("عرض الصور والشعارات", "إظهار صور الأخبار والفرق واللاعبين", showImages, {
            showImages = it
            KoraxSettingsStore.setBoolean(context, "show_images", it)
        })
        SettingsSwitchRow("توفير البيانات", "إيقاف تحميل الصور لتقليل استهلاك الإنترنت", dataSaver, {
            dataSaver = it
            KoraxSettingsStore.setBoolean(context, "data_saver", it)
            if (it) {
                showImages = false
                KoraxSettingsStore.setBoolean(context, "show_images", false)
            }
        })
        Text("المتابعة", color = DText, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 17.dp, bottom = 7.dp))
        SettingsRow("إدارة المفضلة", "الأندية والدوريات والبطولات واللاعبون والتنبيهات الخاصة بهم", onManageFavorites)
        SettingsRow("الخصوصية والبيانات", "المفضلة والتعليقات المحلية محفوظة على هذا الجهاز", { infoDialog = "privacy" })
        SettingsRow("حول KORAX", "مصادر النتائج والأخبار وحقوق البيانات", { infoDialog = "about" })
        Spacer(Modifier.height(25.dp))
    }
    infoDialog?.let { key ->
        val title = if (key == "privacy") "الخصوصية والبيانات" else "حول KORAX"
        val body = if (key == "privacy") {
            "المفضلة والإعجابات وعناصر الحفظ والتعليقات الحالية تُخزّن محليًا على هذا الجهاز. لا يوجد تسجيل دخول أو مزامنة بين الأجهزة في هذه النسخة. لا تكتب معلومات شخصية في التعليقات."
        } else {
            "يجمع KORAX الأخبار من موجزات الناشرين، ويستخدم SportScore للمباريات والبطولات وملفات الفرق واللاعبين. قد تختلف الصور والإحصائيات حسب ما يقدمه المصدر، وتُفتح المقالات الكاملة لدى الناشر حفاظًا على حقوقه."
        }
        AlertDialog(
            onDismissRequest = { infoDialog = null },
            title = { Text(title, color = DText, fontWeight = FontWeight.Bold) },
            text = { Text(body, color = DMuted, fontSize = 12.sp, lineHeight = 19.sp) },
            confirmButton = {
                TextButton(onClick = { infoDialog = null }) {
                    Text("حسنًا", color = DAccent, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = DCard
        )
    }
}

@Composable
private fun SettingsRow(title: String, detail: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DCard),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, DBorder)
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = DText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(detail, color = DMuted, fontSize = 9.sp)
            }
            Text("←", color = DAccent)
        }
    }
}

@Composable
private fun SettingsSwitchRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
        colors = CardDefaults.cardColors(containerColor = DCard),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, DBorder)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = DText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(detail, color = DMuted, fontSize = 9.sp)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
