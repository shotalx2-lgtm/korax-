package com.koora.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import java.util.UUID

private val FxGold = Color(0xFFD5FF00)
private val FxPanel = Color(0xFF141923)
private val FxPanel2 = Color(0xFF1C2430)
private val FxText = Color(0xFFF4F6FA)
private val FxMuted = Color(0xFF9AA5B5)
private val FxGreen = Color(0xFFD5FF00)

private val RequiredFantasyLeagues = listOf(
    "الدوري الإنجليزي", "الدوري الإسباني", "الدوري الفرنسي",
    "الدوري الإيطالي", "الدوري الألماني", "الدوري السعودي"
)

private fun fantasyCoverageSummary(players: List<FantasyPlayer>): String {
    val available = players.map { it.league }.filter { it.isNotBlank() }.distinct()
    val missing = RequiredFantasyLeagues.filterNot { it in available }
    return if (missing.isEmpty()) "تم تحميل بيانات الهدافين وصانعي الأهداف من الدوريات الستة فقط؛ سوق اللاعبين الكامل واحتساب نقاط الجولة المباشرة غير متاحين من المصدر الحالي."
    else "بيانات الهدافين وصانعي الأهداف غير متاحة لهذه الدوريات: " + missing.joinToString("، ") + ". كما أن المصدر لا يوفر سوقًا كاملًا لكل اللاعبين."
}

private data class FantasyPlayer(
    val id: String,
    val name: String,
    val club: String,
    val position: String,
    val price: Double,
    val points: Int,
    val imageUrl: String? = null,
    val league: String = ""
)

private data class FantasyState(
    val catalogue: List<FantasyPlayer> = emptyList(),
    val ownedIds: List<String> = emptyList(),
    val budget: Double = 100.0,
    val teamName: String = "فريقي",
    val formation: String = "4-3-3",
    val captainId: String = "",
    val viceId: String = "",
    val transferLog: List<String> = emptyList()
) {
    fun player(id: String) = catalogue.firstOrNull { it.id == id }
    fun owned() = ownedIds.mapNotNull(::player)
    fun cost() = owned().sumOf { it.price }
    fun points() = owned().sumOf { it.points } + (player(captainId)?.points ?: 0)
    fun count(position: String) = owned().count { it.position == position }
}

private class FantasyLocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("korax_fantasy_local", Context.MODE_PRIVATE)

    fun load(): FantasyState {
        val raw = prefs.getString("state", null) ?: return FantasyState()
        return decode(raw)
    }

    fun decode(raw: String): FantasyState = runCatching {
            val root = JSONObject(raw)
            val playersJson = root.optJSONArray("catalogue") ?: JSONArray()
            val players = (0 until playersJson.length()).mapNotNull { index ->
                val item = playersJson.optJSONObject(index) ?: return@mapNotNull null
                FantasyPlayer(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    club = item.optString("club"),
                    position = item.optString("position"),
                    price = item.optDouble("price"),
                    points = item.optInt("points"),
                    imageUrl = item.optString("imageUrl").takeIf { it.startsWith("https://") },
                    league = item.optString("league")
                ).takeIf { it.id.isNotBlank() && it.name.isNotBlank() }
            }
            val ownedJson = root.optJSONArray("owned") ?: JSONArray()
            val owned = (0 until ownedJson.length()).map { ownedJson.optString(it) }
            val logJson = root.optJSONArray("log") ?: JSONArray()
            val log = (0 until logJson.length()).map { logJson.optString(it) }
            FantasyState(
                catalogue = players,
                ownedIds = owned,
                budget = root.optDouble("budget", 100.0),
                teamName = root.optString("teamName", "فريقي"),
                formation = root.optString("formation", "4-3-3"),
                captainId = root.optString("captainId"),
                viceId = root.optString("viceId"),
                transferLog = log
            )
        }.getOrDefault(FantasyState())

    fun importRaw(raw: String) {
        runCatching { JSONObject(raw) }.onSuccess {
            prefs.edit().putString("state", raw).apply()
        }
    }

    fun exportRaw(): String? = prefs.getString("state", null)

    fun exportCloudState(state: FantasyState): String {
        val owned = JSONArray()
        state.ownedIds.forEach { owned.put(it) }
        val log = JSONArray()
        state.transferLog.forEach { log.put(it) }
        return JSONObject()
            .put("owned", owned)
            .put("budget", state.budget)
            .put("teamName", state.teamName)
            .put("formation", state.formation)
            .put("captainId", state.captainId)
            .put("viceId", state.viceId)
            .put("log", log)
            .toString()
    }

    fun save(state: FantasyState) {
        val players = JSONArray()
        state.catalogue.forEach { p ->
            players.put(JSONObject().put("id", p.id).put("name", p.name).put("club", p.club)
                .put("position", p.position).put("price", p.price).put("points", p.points)
                .put("imageUrl", p.imageUrl ?: "").put("league", p.league))
        }
        val owned = JSONArray()
        state.ownedIds.forEach { owned.put(it) }
        val log = JSONArray()
        state.transferLog.forEach { log.put(it) }
        val root = JSONObject()
            .put("catalogue", players)
            .put("owned", owned)
            .put("budget", state.budget)
            .put("teamName", state.teamName)
            .put("formation", state.formation)
            .put("captainId", state.captainId)
            .put("viceId", state.viceId)
            .put("log", log)
        prefs.edit().putString("state", root.toString()).apply()
    }
}

private suspend fun loadFantasyPlayersFromApi(): List<FantasyPlayer> = withContext(Dispatchers.IO) {
    val base = BuildConfig.KOORA_API_BASE_URL.trim().trimEnd('/')
    require(base.startsWith("https://")) { "مصدر البيانات غير مربوط بعد." }
    val connection = (URL(base + "/api/fantasy/players").openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000
        readTimeout = 10000
        setRequestProperty("Accept", "application/json")
    }
    try {
        if (connection.responseCode !in 200..299) error("تعذر تحميل اللاعبين: HTTP " + connection.responseCode)
        val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val array = root.optJSONArray("data") ?: JSONArray()
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            val name = item.optString("name").trim()
            val positionValue = item.opt("position")
            val rawPosition = when (positionValue) {
                is JSONObject -> positionValue.optString("name")
                else -> positionValue?.toString().orEmpty()
            }.lowercase(Locale.ROOT)
            val position = when {
                rawPosition.contains("goal") || rawPosition.contains("keeper") || rawPosition == "gk" -> "GK"
                rawPosition.contains("defen") || rawPosition == "def" -> "DEF"
                rawPosition.contains("mid") -> "MID"
                rawPosition.contains("attack") || rawPosition.contains("forward") || rawPosition.contains("strik") || rawPosition == "fwd" -> "FWD"
                else -> return@mapNotNull null
            }
            val teamValue = item.opt("team")
            val club = item.optString("club").ifBlank {
                when (teamValue) {
                    is JSONObject -> teamValue.optString("name")
                    is String -> teamValue
                    else -> ""
                }
            }.ifBlank { item.optString("teamName", "نادي غير محدد") }
            if (id.isBlank() || name.isBlank()) return@mapNotNull null
            val initialPrice = when (position) {
                "GK" -> 4.5
                "DEF" -> 5.0
                "MID" -> 6.0
                else -> 6.5
            }
            FantasyPlayer(
                id = "provider-" + id,
                name = name,
                club = club,
                position = position,
                price = item.optDouble("price", initialPrice),
                points = item.optInt("points", item.optInt("fantasyPoints", 0)),
                imageUrl = item.optString("imageUrl").takeIf { it.startsWith("https://") },
                league = item.optString("league")
            )
        }.distinctBy { it.id }
    } finally {
        connection.disconnect()
    }
}

@Composable
fun FantasyScreen() {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { FantasyLocalStore(context) }
    val firebaseConfigured = remember { FirebaseApp.getApps(context).isNotEmpty() }
    val auth: FirebaseAuth? = remember(firebaseConfigured) {
        if (firebaseConfigured) runCatching { FirebaseAuth.getInstance() }.getOrNull() else null
    }
    var signedInUid by remember(auth) { mutableStateOf(auth?.currentUser?.uid) }
    var cloudReady by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf(store.load()) }
    var section by remember { mutableStateOf("team") }
    var query by remember { mutableStateOf("") }
    var formName by remember { mutableStateOf("") }
    var formClub by remember { mutableStateOf("") }
    var formPrice by remember { mutableStateOf("") }
    var formPoints by remember { mutableStateOf("0") }
    var formPosition by remember { mutableStateOf("MID") }
    var message by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val apiConfigured = BuildConfig.KOORA_API_BASE_URL.trim().startsWith("https://")

    DisposableEffect(auth) {
        if (auth != null) {
            val listener = FirebaseAuth.AuthStateListener { signedInUid = it.currentUser?.uid }
            auth.addAuthStateListener(listener)
            onDispose { auth.removeAuthStateListener(listener) }
        } else onDispose { }
    }

    LaunchedEffect(signedInUid) {
        cloudReady = false
        val uid = signedInUid
        if (!firebaseConfigured || uid.isNullOrBlank()) return@LaunchedEffect
        runCatching {
            val document = FirebaseFirestore.getInstance()
                .collection("fantasyTeams").document(uid).get().await()
            val cloudJson = document.getString("stateJson")
            if (!cloudJson.isNullOrBlank()) {
                state = store.decode(cloudJson).copy(catalogue = state.catalogue)
                store.save(state)
                message = "تمت استعادة تشكيلتك من حسابك."
            } else {
                val localJson = store.exportCloudState(state)
                if (localJson.isNotBlank()) {
                    FirebaseFirestore.getInstance().collection("fantasyTeams").document(uid)
                        .set(mapOf("stateJson" to localJson, "updatedAt" to FieldValue.serverTimestamp())).await()
                }
            }
        }.onFailure {
            message = "تعذر تحميل الحفظ السحابي: " + (it.localizedMessage ?: "تحقق من إعداد Firestore وقواعد الأمان.")
        }
        cloudReady = true
    }

    LaunchedEffect(state, signedInUid, cloudReady) {
        val uid = signedInUid
        if (!cloudReady || uid.isNullOrBlank()) return@LaunchedEffect
        delay(700)
        runCatching {
            FirebaseFirestore.getInstance().collection("fantasyTeams").document(uid)
                .set(
                    mapOf(
                        "stateJson" to store.exportCloudState(state),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        }.onFailure {
            message = "تعذر مزامنة الفريق: " + (it.localizedMessage ?: "تحقق من اتصال الإنترنت.")
        }
    }

    androidx.compose.runtime.LaunchedEffect(apiConfigured) {
        if (apiConfigured && state.catalogue.isEmpty()) {
            importing = true
            val result = runCatching { loadFantasyPlayersFromApi() }
            val fetched = result.getOrDefault(emptyList())
            if (fetched.isNotEmpty()) {
                val next = state.copy(catalogue = fetched)
                state = next
                store.save(next)
                message = "تم تحميل " + fetched.size + " لاعبًا. " + fantasyCoverageSummary(fetched)
            } else if (result.isFailure) {
                message = result.exceptionOrNull()?.message ?: "تعذر تحميل سوق اللاعبين."
            } else {
                message = "مصدر البيانات لم يُرجع لاعبين متاحين حاليًا. جرّب التحديث لاحقًا؛ لن نضيف أسماء أو نقاطًا وهمية."
            }
            importing = false
        }
    }

    fun commit(next: FantasyState) {
        state = next
        store.save(next)
    }

    fun adjustPoints(player: FantasyPlayer, delta: Int) {
        val updated = state.catalogue.map { current ->
            if (current.id == player.id) current.copy(points = (current.points + delta).coerceIn(-1000, 1000)) else current
        }
        commit(state.copy(
            catalogue = updated,
            transferLog = (listOf("تعديل نقاط ${player.name}: ${if (delta > 0) "+" else ""}$delta") + state.transferLog).take(50)
        ))
    }

    val owned = state.owned()
    val remaining = state.catalogue.filter { it.id !in state.ownedIds }
        .filter { it.name.contains(query, true) || it.club.contains(query, true) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("KORAX FANTASY", color = FxGold, fontWeight = FontWeight.Black, fontSize = 21.sp)
                Text("إدارة فريقك ومنافستك", color = FxMuted, fontSize = 12.sp)
            }
            Surface(color = FxPanel2, shape = RoundedCornerShape(12.dp)) {
                Text(if (signedInUid != null && cloudReady) "سحابي" else "محلي", color = FxGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = FxPanel),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFF303A49))
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(state.teamName, color = FxText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric("الميزانية", String.format("%.1f م", state.budget))
                    Metric("قيمة الفريق", String.format("%.1f م", state.cost()))
                    Metric("النقاط المسجلة", state.points().toString())
                }
                Spacer(Modifier.height(12.dp))
                Text("المجموعة: ${owned.size}/15 لاعبًا", color = FxMuted, fontSize = 12.sp)
                Text("اللاعبون المتاحون حاليًا من المصدر هم الهدافون وصنّاع الأهداف فقط، وليسوا قائمة كاملة لكل لاعبي الدوريات. لذلك قد لا تكتمل تشكيلة 15 لاعبًا، والنقاط المعروضة ليست نقاط جولة مباشرة.", color = FxMuted, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = FxPanel),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFF303A49))
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text("استيراد اللاعبين الحقيقيين", color = FxText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("يتم تحميل لاعبي الدوريات الستة مع صورهم وإحصائيات الموسم. الأسعار الأساسية وقواعد احتساب نقاط الموسم محددة داخل KORAX وليست أسعارًا رسمية من المزود.", color = FxMuted, fontSize = 10.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = apiConfigured && !importing,
                    onClick = {
                        scope.launch {
                            importing = true
                            message = "جارٍ تحميل اللاعبين من المصدر..."
                            val result = runCatching { loadFantasyPlayersFromApi() }
                            result.onSuccess { fetched ->
                                if (fetched.isEmpty()) {
                                    message = "المصدر لم يُرجع لاعبين متاحين. تحقق من اشتراك مزود البيانات."
                                } else {
                                    val existingById = state.catalogue.associateBy { it.id }
                                    val refreshed = fetched.map { fresh ->
                                        val previous = existingById[fresh.id]
                                        if (previous == null) fresh else fresh.copy(
                                            price = previous.price,
                                            points = previous.points
                                        )
                                    }
                                    val refreshedIds = refreshed.map { it.id }.toSet()
                                    val retained = state.catalogue.filter { it.id !in refreshedIds }
                                    commit(state.copy(catalogue = retained + refreshed))
                                    message = "تم تحديث بيانات " + refreshed.size + " لاعبًا مع الاحتفاظ بأسعارك. " + fantasyCoverageSummary(refreshed)
                                    section = "market"
                                }
                            }.onFailure { error ->
                                message = error.message ?: "تعذر الاتصال بمصدر اللاعبين."
                            }
                            importing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = FxGold, contentColor = Color.Black)
                ) {
                    Text(if (importing) "جارٍ التحميل..." else "تحديث سوق اللاعبين")
                }
                if (!apiConfigured) {
                    Text("يلزم نشر خادم KORAX وربط رابط HTTPS في إعدادات البناء لتفعيل الاستيراد التلقائي.", color = FxMuted, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (state.catalogue.isEmpty()) {
            InfoPanel("سوق اللاعبين غير متاح حاليًا", "لم يصل للتطبيق أي لاعب من مزود البيانات. استخدم تحديث سوق اللاعبين لاحقًا؛ لن نعرض لاعبين وهميين بدل البيانات الحقيقية.")
            Spacer(Modifier.height(10.dp))
        } else {
            InfoPanel("حدود بيانات الفانتازي الحالية", "القائمة مقتصرة على الهدافين وصنّاع الأهداف من المصدر، ولا تتضمن كل الحراس والمدافعين ولا احتساب نقاط الجولة لحظة بلحظة.")
            Spacer(Modifier.height(10.dp))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("team" to "فريقي", "market" to "السوق", "formation" to "التشكيلة", "points" to "النقاط", "admin" to "إضافة للسوق").forEach { (key, label) ->
                FilterChip(selected = section == key, onClick = { section = key }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(12.dp))
        if (message.isNotBlank()) {
            Text(message, color = FxGreen, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        }

        when (section) {
            "market" -> {
                Text("سوق اللاعبين", color = FxText, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text("قائمة اللاعبين المستوردة من المصدر أو المضافة يدويًا إلى هذا الجهاز.", color = FxMuted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text("ابحث عن لاعب أو نادٍ") })
                Spacer(Modifier.height(8.dp))
                if (remaining.isEmpty()) {
                    InfoPanel("لا يوجد لاعبون في السوق حاليًا", "اربط مصدر البيانات ثم اضغط تحديث سوق اللاعبين، أو أضف بيانات موثوقة من تبويب الإدارة.")
                } else {
                    remaining.forEach { player ->
                        PlayerCard(player, owned = false, onAction = {
                            val candidate = FantasyRulePlayer(player.id, player.club, player.position, player.price, player.points)
                            val validation = FantasyRules.validateAdd(
                                owned.map { FantasyRulePlayer(it.id, it.club, it.position, it.price, it.points) },
                                candidate,
                                state.budget
                            )
                            if (!validation.allowed) {
                                message = validation.reason
                            } else {
                                commit(state.copy(
                                    ownedIds = state.ownedIds + player.id,
                                    budget = state.budget - player.price,
                                    transferLog = (listOf("شراء: ${player.name}") + state.transferLog).take(50)
                                ))
                                message = "تم شراء ${player.name} وحفظ العملية على الجهاز."
                            }
                        }, actionLabel = "شراء")
                    }
                }
            }
            "team" -> {
                Text("فريقي", color = FxText, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = state.teamName, onValueChange = { commit(state.copy(teamName = it.take(28))) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("اسم فريقك") })
                Spacer(Modifier.height(10.dp))
                listOf("GK", "DEF", "MID", "FWD").forEach { position ->
                    val players = owned.filter { it.position == position }
                    Text("${positionLabel(position)} • ${players.size}/${mapOf("GK" to 2, "DEF" to 5, "MID" to 5, "FWD" to 3).getValue(position)}",
                        color = FxGold, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(vertical = 5.dp))
                    if (players.isEmpty()) Text("لا يوجد لاعب في هذا المركز بعد.", color = FxMuted, fontSize = 11.sp)
                    players.forEach { player ->
                        PlayerCard(player, owned = true, onAction = {
                            commit(state.copy(
                                ownedIds = state.ownedIds - player.id,
                                budget = state.budget + player.price,
                                captainId = if (state.captainId == player.id) "" else state.captainId,
                                viceId = if (state.viceId == player.id) "" else state.viceId,
                                transferLog = (listOf("بيع: ${player.name}") + state.transferLog).take(50)
                            ))
                            message = "تم بيع ${player.name} وإرجاع قيمته للميزانية."
                        }, actionLabel = "بيع")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                            Button(onClick = {
                                commit(state.copy(captainId = player.id, viceId = if (state.viceId == player.id) "" else state.viceId))
                                message = "تم تعيين ${player.name} قائدًا."
                            }, colors = ButtonDefaults.buttonColors(containerColor = if (state.captainId == player.id) FxGold else FxPanel2)) {
                                Text(if (state.captainId == player.id) "القائد ✓" else "تعيين قائد")
                            }
                            Button(onClick = {
                                commit(state.copy(viceId = player.id, captainId = if (state.captainId == player.id) "" else state.captainId))
                                message = "تم تعيين ${player.name} نائبًا."
                            }, colors = ButtonDefaults.buttonColors(containerColor = if (state.viceId == player.id) FxGreen else FxPanel2)) {
                                Text(if (state.viceId == player.id) "النائب ✓" else "تعيين نائب")
                            }
                        }
                    }
                }
                Text("سجل الانتقالات", color = FxGold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                if (state.transferLog.isEmpty()) Text("لا توجد انتقالات بعد.", color = FxMuted, fontSize = 11.sp)
                else state.transferLog.take(10).forEach { Text("• $it", color = FxMuted, fontSize = 11.sp) }
            }
            "formation" -> {
                Text("التشكيلة والخطة", color = FxText, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text("اختر خطة اللعب. يتم عرض الأساسيين حسب المراكز المتاحة في فريقك.", color = FxMuted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                val formations = listOf("4-3-3", "4-4-2", "3-4-3", "3-5-2", "4-5-1", "5-3-2", "5-4-1")
                formations.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { formation ->
                            FilterChip(selected = state.formation == formation,
                                onClick = {
                                val validation = FantasyRules.validateFormation(formation)
                                if (validation.allowed) {
                                    commit(state.copy(formation = formation))
                                    message = "تم حفظ خطة $formation."
                                } else message = validation.reason
                            },
                                label = { Text(formation) })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                val parts = state.formation.split("-").mapNotNull(String::toIntOrNull)
                val goalkeeper = owned.filter { it.position == "GK" }.take(1)
                val defenders = owned.filter { it.position == "DEF" }.take(parts.getOrElse(0) { 4 })
                val midfielders = owned.filter { it.position == "MID" }.take(parts.getOrElse(1) { 3 })
                val forwards = owned.filter { it.position == "FWD" }.take(parts.getOrElse(2) { 3 })
                Text("الأساسيون • ${(goalkeeper + defenders + midfielders + forwards).size}/11", color = FxGold, fontWeight = FontWeight.Bold)
                (goalkeeper + defenders + midfielders + forwards).forEachIndexed { index, p ->
                    Text("${index + 1}. ${p.name} — ${positionLabel(p.position)}", color = FxText, fontSize = 12.sp, modifier = Modifier.padding(vertical = 3.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text("البدلاء", color = FxGold, fontWeight = FontWeight.Bold)
                val starters = (goalkeeper + defenders + midfielders + forwards).map { it.id }.toSet()
                owned.filter { it.id !in starters }.forEach { Text("• ${it.name} — ${positionLabel(it.position)}", color = FxMuted, fontSize = 12.sp) }
                if (goalkeeper.size + defenders.size + midfielders.size + forwards.size < 11) {
                    InfoPanel("التشكيلة غير مكتملة", "اشترِ لاعبين كافيين لكل مركز من السوق حتى يتكوّن 11 لاعبًا أساسيًا.")
                }
            }
            "points" -> {
                Text("نظام النقاط", color = FxText, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text("نقاط الموسم محسوبة تلقائيًا من إحصائيات اللاعبين المتاحة لدى مزود البيانات وفق قواعد KORAX.", color = FxMuted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                owned.forEach { player ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(player.name, color = FxText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("${player.club} • ${positionLabel(player.position)}", color = FxMuted, fontSize = 10.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(player.points.toString() + " نقطة", color = FxGold, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (owned.isNotEmpty()) {
                    InfoPanel("مجموع الفريق: ${state.points()} نقطة", "هذه نقاط الموسم من إحصائيات المزود وفق نظام KORAX. مضاعفة القائد تُطبق على مجموع النقاط المعروض.")
                }
                if (owned.isEmpty()) InfoPanel("لا توجد نقاط بعد", "أضف لاعبين إلى السوق ثم اشترِ تشكيلتك.")
            }
            else -> {
                Text("إضافة لاعب إلى السوق", color = FxText, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text("استخدم بيانات لاعبين وقيمًا معتمدة من مصدر المسابقة فقط.", color = FxMuted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = formName, onValueChange = { formName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("اسم اللاعب") })
                OutlinedTextField(value = formClub, onValueChange = { formClub = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("اسم النادي") })
                OutlinedTextField(value = formPrice, onValueChange = { formPrice = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("السعر بالمليون") })
                OutlinedTextField(value = formPoints, onValueChange = { formPoints = it.filter { c -> c.isDigit() || c == '-' }.take(5) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("النقاط المسجلة لهذا اللاعب") })
                Text("المركز", color = FxGold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 5.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("GK", "DEF", "MID", "FWD").forEach { position ->
                        FilterChip(selected = formPosition == position, onClick = { formPosition = position }, label = { Text(positionLabel(position)) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    val price = formPrice.toDoubleOrNull()
                    val points = formPoints.toIntOrNull()
                    when {
                        formName.isBlank() || formClub.isBlank() -> message = "اكتب اسم اللاعب والنادي."
                        price == null || price <= 0 || price > 100 -> message = "أدخل سعرًا صحيحًا أكبر من صفر."
                        points == null -> message = "أدخل نقاطًا صحيحة."
                        state.catalogue.any { it.name.equals(formName.trim(), true) && it.club.equals(formClub.trim(), true) } -> message = "هذا اللاعب موجود بالفعل."
                        else -> {
                            val p = FantasyPlayer(UUID.randomUUID().toString(), formName.trim(), formClub.trim(), formPosition, price, points)
                            commit(state.copy(catalogue = state.catalogue + p))
                            formName = ""; formClub = ""; formPrice = ""; formPoints = "0"
                            message = "تمت إضافة اللاعب إلى السوق المحلي."
                            section = "market"
                        }
                    }
                }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = FxGold, contentColor = Color.Black)) {
                    Text("حفظ اللاعب في السوق")
                }
                Spacer(Modifier.height(8.dp))
                InfoPanel("قواعد التشكيلة الافتراضية", "15 لاعبًا: حارسا مرمى، 5 مدافعين، 5 وسط، 3 مهاجمين. الميزانية 100 مليون، والحد الأقصى 3 لاعبين من النادي الواحد.")
            }
        }
        Text(
            "Powered by SportScore",
            color = FxMuted,
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).clickable {
                uriHandler.openUri("https://sportscore.com/")
            }.padding(vertical = 7.dp)
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, color = FxMuted, fontSize = 10.sp)
        Text(value, color = FxText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun PlayerCard(player: FantasyPlayer, owned: Boolean, onAction: () -> Unit, actionLabel: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = FxPanel),
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, Color(0xFF303A49))
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!player.imageUrl.isNullOrBlank()) {
                coil.compose.AsyncImage(
                    model = player.imageUrl,
                    contentDescription = player.name,
                    modifier = Modifier.width(48.dp).height(56.dp).padding(end = 7.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            }
            Column(Modifier.weight(1f)) {
                Text(player.name, color = FxText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("${player.club} • ${positionLabel(player.position)}", color = FxMuted, fontSize = 10.sp)
                if (player.league.isNotBlank()) Text(player.league, color = FxMuted, fontSize = 9.sp)
                Text(String.format("%.1f مليون", player.price) + " • ${player.points} نقطة موسم", color = FxGold, fontSize = 10.sp)
            }
            Button(onClick = onAction, colors = ButtonDefaults.buttonColors(containerColor = if (owned) FxPanel2 else FxGold, contentColor = if (owned) FxText else Color.Black)) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun InfoPanel(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = FxPanel2),
        shape = RoundedCornerShape(15.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = FxGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(5.dp))
            Text(body, color = FxMuted, fontSize = 11.sp, lineHeight = 17.sp)
        }
    }
}

private fun positionLabel(position: String): String = when (position) {
    "GK" -> "حارس"
    "DEF" -> "دفاع"
    "MID" -> "وسط"
    "FWD" -> "هجوم"
    else -> position
}
