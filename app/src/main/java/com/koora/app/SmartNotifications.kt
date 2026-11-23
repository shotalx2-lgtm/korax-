package com.koora.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit

internal data class AlertHistoryItem(
    val kind: String,
    val title: String,
    val message: String,
    val createdAt: Long
)

internal data class AlertSetting(
    val key: String,
    val title: String,
    val description: String
)

private val alertSettings = listOf(
    AlertSetting("goal", "الأهداف", "هدف جديد مع اسم اللاعب والنتيجة والدقيقة"),
    AlertSetting("assist", "التمريرات الحاسمة", "إشعار منفصل عند توفر اسم صانع الهدف"),
    AlertSetting("var", "مراجعات VAR", "مراجعة تقنية الفيديو وقرارات الحكم"),
    AlertSetting("cancel", "إلغاء الأهداف", "تنبيه مستقل عند إلغاء هدف أو احتسابه"),
    AlertSetting("red_card", "البطاقات الحمراء", "الطرد والإنذار الثاني"),
    AlertSetting("yellow_card", "البطاقات الصفراء", "تنبيه بالبطاقات الصفراء"),
    AlertSetting("penalty", "ركلات الجزاء", "ركلات الجزاء والتصديات والترجيح"),
    AlertSetting("kickoff", "بداية المباراة", "إشعار انطلاق اللقاء"),
    AlertSetting("halftime", "نهاية الشوط الأول", "تنبيه الاستراحة بين الشوطين"),
    AlertSetting("fulltime", "نهاية المباراة", "النتيجة النهائية فور توفرها"),
    AlertSetting("substitution", "التبديلات", "دخول وخروج اللاعبين"),
    AlertSetting("injury", "الإصابات", "توقف اللعب وعلاج اللاعبين"),
    AlertSetting("news", "الأخبار العاجلة", "أخبار كرة القدم العربية مع صورة الخبر عند توفرها")
)

internal object KoraxSmartAlerts {
    private const val PREFS = "korax_smart_alerts_v2"
    private const val HISTORY_KEY = "history"
    private const val MATCH_STATE_KEY = "match_state"
    private const val SEEN_EVENTS_KEY = "seen_events"
    private const val SEEN_NEWS_KEY = "seen_news"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context, key: String): Boolean =
        prefs(context).getBoolean("enabled_$key", true)

    fun setEnabled(context: Context, key: String, enabled: Boolean) {
        prefs(context).edit().putBoolean("enabled_$key", enabled).apply()
    }

    fun soundEnabled(context: Context): Boolean =
        prefs(context).getBoolean("sounds_enabled", true)

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("sounds_enabled", enabled).apply()
        ensureChannels(context)
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun setBackgroundUpdates(context: Context, enabled: Boolean) {
        if (enabled) scheduleBackground(context)
        else WorkManager.getInstance(context).cancelUniqueWork("korax_smart_alerts")
    }

    fun scheduleBackground(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<KoraxAlertWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "korax_smart_alerts",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val audio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val configs = listOf(
            Triple("goal", "هدف جديد", R.raw.korax_goal),
            Triple("assist", "تمريرة حاسمة", R.raw.korax_assist),
            Triple("var", "مراجعة VAR", R.raw.korax_var),
            Triple("cancel", "إلغاء هدف", R.raw.korax_cancel),
            Triple("red_card", "البطاقات الحمراء", R.raw.korax_red_card),
            Triple("yellow_card", "البطاقات الصفراء", R.raw.korax_yellow_card),
            Triple("penalty", "ركلات الجزاء", R.raw.korax_penalty),
            Triple("kickoff", "بداية المباراة", R.raw.korax_kickoff),
            Triple("halftime", "نهاية الشوط الأول", R.raw.korax_halftime),
            Triple("fulltime", "نهاية المباراة", R.raw.korax_fulltime),
            Triple("substitution", "التبديلات", R.raw.korax_substitution),
            Triple("injury", "الإصابات", R.raw.korax_injury),
            Triple("news", "الأخبار العاجلة", R.raw.korax_news)
        )

        configs.forEach { (key, label, soundResource) ->
            listOf(true, false).forEach { withSound ->
                val channel = NotificationChannel(
                    channelId(key, withSound),
                    if (withSound) label else "$label - بدون صوت",
                    if (key == "news" || key == "yellow_card") NotificationManager.IMPORTANCE_DEFAULT
                    else NotificationManager.IMPORTANCE_HIGH
                )
                channel.description = "تنبيهات KORAX الخاصة بـ $label"
                channel.enableVibration(true)
                channel.vibrationPattern = longArrayOf(0, 180, 90, 180)
                channel.setShowBadge(true)
                if (withSound) {
                    channel.setSound(
                        Uri.parse("android.resource://${context.packageName}/$soundResource"),
                        audio
                    )
                } else {
                    channel.setSound(null, null)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun channelId(kind: String, withSound: Boolean): String =
        "korax_${kind}_v2_" + if (withSound) "sound" else "silent"

    suspend fun poll(context: Context) = withContext(Dispatchers.IO) {
        val base = BuildConfig.KOORA_API_BASE_URL.trim().trimEnd('/')
        if (!base.startsWith("https://")) return@withContext
        ensureChannels(context)

        val mergedMatches = LinkedHashMap<String, JSONObject>()
        runCatching {
            val liveRows = fetchJson("$base/api/matches?live=1").optJSONArray("data") ?: JSONArray()
            for (index in 0 until liveRows.length()) {
                val match = liveRows.optJSONObject(index) ?: continue
                val id = match.optString("id")
                if (id.isNotBlank()) mergedMatches[id] = match
            }
        }
        val localDate = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
        runCatching {
            val datedRows = fetchJson("$base/api/matches?date=$localDate").optJSONArray("data") ?: JSONArray()
            for (index in 0 until datedRows.length()) {
                val match = datedRows.optJSONObject(index) ?: continue
                val id = match.optString("id")
                if (id.isNotBlank() && !mergedMatches.containsKey(id)) mergedMatches[id] = match
            }
        }
        if (mergedMatches.isNotEmpty()) {
            val combinedRows = JSONArray()
            mergedMatches.values.forEach(combinedRows::put)
            processMatchUpdates(context, JSONObject().put("data", combinedRows))
        }

        val preferences = prefs(context)
        val lastNewsPoll = preferences.getLong("last_news_poll", 0L)
        if (System.currentTimeMillis() - lastNewsPoll >= 240_000L) {
            runCatching {
                val newsPayload = fetchJson("$base/api/news?language=ar&per_page=40")
                processNews(context, newsPayload)
                preferences.edit().putLong("last_news_poll", System.currentTimeMillis()).apply()
            }
        }
    }

    private fun fetchJson(address: String): JSONObject {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 7000
            readTimeout = 9000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "KORAX-Android/0.5")
        }
        return try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun processMatchUpdates(context: Context, payload: JSONObject) {
        val rows = payload.optJSONArray("data") ?: JSONArray()
        if (rows.length() == 0) return
        val preferences = prefs(context)
        val seeded = preferences.getBoolean("matches_seeded", false)
        val states = JSONObject(preferences.getString(MATCH_STATE_KEY, "{}") ?: "{}")
        val seenEvents = readSet(preferences.getString(SEEN_EVENTS_KEY, "[]"))
        val nextStates = JSONObject(states.toString())

        for (index in 0 until rows.length()) {
            val match = rows.optJSONObject(index) ?: continue
            val matchId = match.optString("id").ifBlank { continue }
            val home = match.optJSONObject("homeTeam")
            val away = match.optJSONObject("awayTeam")
            val events = match.optJSONArray("events") ?: JSONArray()
            val old = states.optJSONObject(matchId)
            if (seeded && old != null) {
                val previousStatus = old.optString("status").lowercase(Locale.ROOT)
                val currentStatus = match.optString("status").lowercase(Locale.ROOT)
                val wasLive = previousStatus.contains("live") || previousStatus.contains("in play") ||
                    previousStatus.contains("1st half") || previousStatus.contains("2nd half") ||
                    previousStatus.contains("مباشر") || previousStatus.contains("الشوط")
                val isLive = currentStatus.contains("live") || currentStatus.contains("in play") ||
                    currentStatus.contains("1st half") || currentStatus.contains("2nd half") ||
                    currentStatus.contains("مباشر") || currentStatus.contains("الشوط")
                val wasHalfTime = previousStatus.contains("half time") || previousStatus.contains("halftime") ||
                    previousStatus.contains("استراحة")
                val isHalfTime = currentStatus.contains("half time") || currentStatus.contains("halftime") ||
                    currentStatus.contains("استراحة")
                val wasFinished = previousStatus.contains("finished") || previousStatus == "ft" ||
                    previousStatus.contains("full time") || previousStatus.contains("ended") ||
                    previousStatus.contains("انتهت")
                val isFinished = currentStatus.contains("finished") || currentStatus == "ft" ||
                    currentStatus.contains("full time") || currentStatus.contains("ended") ||
                    currentStatus.contains("انتهت")
                when {
                    !wasFinished && isFinished -> notifyMatchStatus(context, "fulltime", match)
                    !wasHalfTime && isHalfTime -> notifyMatchStatus(context, "halftime", match)
                    !wasLive && isLive -> notifyMatchStatus(context, "kickoff", match)
                }
            }
            val oldHomeScore = old?.optInt("homeScore", -1) ?: -1
            val oldAwayScore = old?.optInt("awayScore", -1) ?: -1
            val homeScore = scoreOrUnknown(match.opt("homeScore"))
            val awayScore = scoreOrUnknown(match.opt("awayScore"))
            var newGoalCount = 0
            var explicitCancel = false

            for (eventIndex in 0 until events.length()) {
                val event = events.optJSONObject(eventIndex) ?: continue
                val minute = event.optString("minute")
                val type = event.optString("type_name", "حدث")
                val detail = event.optString("detail")
                val playerId = event.optString("player_id")
                val teamId = event.optString("team_id")
                val key = FootballAlertClassifier.fingerprint(
                    matchId, minute, type, playerId, teamId, detail
                )
                val kind = FootballAlertClassifier.classify(type, detail)
                if (kind == "cancel") explicitCancel = true
                if (!seeded) {
                    seenEvents.add(key)
                    continue
                }
                if (!seenEvents.add(key)) continue
                if (kind == "goal") newGoalCount++
                if (kind != null && matchesFavorite(context, match, event)) {
                    notifyEvent(context, kind, match, event)
                }
            }

            if (seeded && old != null && oldHomeScore >= 0 && oldAwayScore >= 0 &&
                homeScore >= 0 && awayScore >= 0) {
                val homeDelta = homeScore - oldHomeScore
                val awayDelta = awayScore - oldAwayScore
                val increase = homeDelta.coerceAtLeast(0) + awayDelta.coerceAtLeast(0)
                if (increase > newGoalCount) {
                    repeat((increase - newGoalCount).coerceAtMost(3)) {
                        notifyGenericGoal(context, match)
                    }
                }
                if ((homeDelta < 0 || awayDelta < 0) && !explicitCancel) {
                    notifyEvent(
                        context, "cancel", match,
                        JSONObject().put("type_name", "VAR - Goal cancelled")
                            .put("detail", "تم تعديل النتيجة بعد مراجعة الحكم")
                    )
                }
            }

            nextStates.put(matchId, JSONObject()
                .put("homeScore", homeScore)
                .put("awayScore", awayScore)
                .put("status", match.optString("status")))
        }

        while (nextStates.length() > 80) {
            val keys = nextStates.keys()
            if (!keys.hasNext()) break
            nextStates.remove(keys.next())
        }
        preferences.edit()
            .putString(MATCH_STATE_KEY, nextStates.toString())
            .putString(SEEN_EVENTS_KEY, writeSet(seenEvents, 700))
            .putBoolean("matches_seeded", true)
            .apply()
    }

    private fun notifyEvent(context: Context, kind: String, match: JSONObject, event: JSONObject) {
        if (!matchesFavorite(context, match, event)) return
        val home = match.optJSONObject("homeTeam")
        val away = match.optJSONObject("awayTeam")
        val teamId = event.optString("team_id")
        val homeId = home?.optString("id").orEmpty()
        val awayId = away?.optString("id").orEmpty()
        val eventTeam = event.optJSONObject("team")
        val teamName = eventTeam?.optString("name").orEmpty()
            .ifBlank { if (teamId == awayId) away?.optString("name").orEmpty() else home?.optString("name").orEmpty() }
        val teamLogo = event.optString("team_image_url").takeIf { it.startsWith("https://") }
            ?: if (teamId == awayId) away?.optString("crest") else home?.optString("crest")
        val player = event.optString("player_name").ifBlank { "لاعب غير محدد" }
        val assist = event.optString("assist_player_name")
        val minute = event.optString("minute").takeIf { it.isNotBlank() && it != "null" }?.let { "$it′" }
        val competition = match.optJSONObject("competition")
        val competitionName = competition?.optString("name").orEmpty()
            .ifBlank { match.optString("competitionName") }
        val score = scoreLabel(match)
        val matchLabel = listOf(home?.optString("name").orEmpty(), score, away?.optString("name").orEmpty())
            .filter { it.isNotBlank() }.joinToString("  ")
        val title = when (kind) {
            "goal" -> "⚽ هدف جديد!"
            "assist" -> "🎯 تمريرة حاسمة!"
            "var" -> "📺 مراجعة VAR"
            "cancel" -> "🚫 تم إلغاء الهدف"
            "red_card" -> "🟥 بطاقة حمراء"
            "yellow_card" -> "🟨 بطاقة صفراء"
            "penalty" -> "⚽ ركلة جزاء"
            "kickoff" -> "🚩 انطلاق المباراة"
            "halftime" -> "⏸️ نهاية الشوط الأول"
            "fulltime" -> "🏁 نهاية المباراة"
            "substitution" -> "🔄 تبديل في المباراة"
            "injury" -> "🩺 توقف بسبب إصابة"
            else -> "تحديث في المباراة"
        }
        val person = if (kind == "assist" && assist.isNotBlank()) assist else player
        val message = listOf(
            person.takeUnless { it == "لاعب غير محدد" },
            teamName,
            minute,
            competitionName,
            matchLabel
        ).filterNotNull().filter { it.isNotBlank() }.joinToString(" • ")
        val playerImage = event.optString("player_image_url")
            .takeIf { it.startsWith("https://") }.orEmpty()
        val competitionImage = competition?.optString("imageUrl")
            ?.takeIf { it.startsWith("https://") }.orEmpty()
        val iconImage = teamLogo?.takeIf { it.startsWith("https://") } ?: competitionImage
        val key = listOf(match.optString("id"), kind, event.optString("minute"), player, event.optString("detail"))
            .joinToString("|")
        if (isEnabled(context, kind)) {
            send(context, kind, title, message.ifBlank { matchLabel }, iconImage, playerImage, key)
        }

        if (kind == "goal" && assist.isNotBlank() && isEnabled(context, "assist")) {
            send(
                context, "assist", "🎯 تمريرة حاسمة!",
                "$assist • ${teamName.ifBlank { matchLabel }} • ${minute.orEmpty()}",
                iconImage,
                event.optString("assist_player_image_url").takeIf { it.startsWith("https://") }.orEmpty(),
                "$key|assist"
            )
        }
    }

    private fun notifyMatchStatus(context: Context, kind: String, match: JSONObject) {
        if (!isEnabled(context, kind) || !matchesFavorite(context, match, null)) return
        val home = match.optJSONObject("homeTeam")
        val away = match.optJSONObject("awayTeam")
        val competition = match.optJSONObject("competition")
        val title = when (kind) {
            "kickoff" -> "🚩 انطلقت المباراة الآن"
            "halftime" -> "⏸️ نهاية الشوط الأول"
            else -> "🏁 انتهت المباراة"
        }
        val message = listOf(
            home?.optString("name").orEmpty(),
            scoreLabel(match),
            away?.optString("name").orEmpty(),
            competition?.optString("name").orEmpty()
        ).filter { it.isNotBlank() }.joinToString(" • ")
        val logo = home?.optString("crest").orEmpty().ifBlank { away?.optString("crest").orEmpty() }
        send(context, kind, title, message.ifBlank { "تحديث حالة المباراة" }, logo, "", "${match.optString("id")}|$kind|${match.optString("status")}")
    }

    private fun notifyGenericGoal(context: Context, match: JSONObject) {
        if (!isEnabled(context, "goal") || !matchesFavorite(context, match, null)) return
        val home = match.optJSONObject("homeTeam")
        val away = match.optJSONObject("awayTeam")
        val competition = match.optJSONObject("competition")
        val line = listOf(
            home?.optString("name").orEmpty(),
            scoreLabel(match),
            away?.optString("name").orEmpty(),
            competition?.optString("name").orEmpty()
        ).filter { it.isNotBlank() }.joinToString(" • ")
        val logo = home?.optString("crest").orEmpty().ifBlank { away?.optString("crest").orEmpty() }
        send(
            context, "goal", "⚽ تغيّرت النتيجة!",
            line.ifBlank { "تم تسجيل هدف في مباراة مباشرة" },
            logo, "",
            "${match.optString("id")}|score|${scoreLabel(match)}"
        )
    }

    private fun processNews(context: Context, payload: JSONObject) {
        val rows = payload.optJSONArray("data") ?: JSONArray()
        if (rows.length() == 0) return
        val preferences = prefs(context)
        val seeded = preferences.getBoolean("news_seeded", false)
        val seen = readSet(preferences.getString(SEEN_NEWS_KEY, "[]"))
        val fresh = mutableListOf<JSONObject>()
        for (index in 0 until rows.length()) {
            val item = rows.optJSONObject(index) ?: continue
            val key = item.optString("url").ifBlank { item.optString("id") }
            if (key.isBlank()) continue
            if (!seeded) seen.add(key) else if (seen.add(key)) fresh.add(item)
        }
        if (seeded && isEnabled(context, "news")) {
            fresh.take(3).forEach { item ->
                val title = item.optString("title").ifBlank { "خبر كروي جديد" }
                val source = item.optString("source")
                val summary = item.optString("summary")
                val body = listOf(source, summary).filter { it.isNotBlank() }.joinToString(" • ")
                    .ifBlank { "اضغط لقراءة تفاصيل الخبر" }
                val image = item.optString("imageUrl").takeIf { it.startsWith("https://") }.orEmpty()
                val key = item.optString("url").ifBlank { item.optString("id") }
                send(context, "news", "📰 $title", body, image, image, key)
            }
        }
        preferences.edit()
            .putBoolean("news_seeded", true)
            .putString(SEEN_NEWS_KEY, writeSet(seen, 250))
            .apply()
    }

    private fun matchesFavorite(context: Context, match: JSONObject, event: JSONObject?): Boolean {
        if (KoraxMatchWatchStore.isWatching(context, match.optString("id"))) return true
        if (!KoraxFavoriteStore.setupComplete(context)) return false
        val all = KoraxFavoriteStore.all(context)
        if (all.isEmpty()) return true
        val followed = all.filter { it.notify }
        if (followed.isEmpty()) return false

        val home = match.optJSONObject("homeTeam")
        val away = match.optJSONObject("awayTeam")
        val competition = match.optJSONObject("competition")
        val eventTeamId = event?.optString("team_id").orEmpty()
        val eventPlayerId = event?.optString("player_id").orEmpty()
        val eventPlayerName = event?.optString("player_name").orEmpty()
        val assistPlayerId = event?.optString("assist_player_id").orEmpty()
        val assistPlayerName = event?.optString("assist_player_name").orEmpty()
        val homeId = home?.optString("id").orEmpty()
        val awayId = away?.optString("id").orEmpty()
        val homeName = home?.optString("name").orEmpty()
        val awayName = away?.optString("name").orEmpty()
        val competitionId = competition?.optString("id").orEmpty()
        val competitionSlug = competition?.optString("slug").orEmpty()
        val competitionName = competition?.optString("name").orEmpty()

        return followed.any { favorite ->
            when (favorite.kind) {
                "club", "national" ->
                    favorite.id == homeId || favorite.id == awayId ||
                    favorite.slug == homeId || favorite.slug == awayId ||
                    favorite.name.equals(homeName, true) || favorite.name.equals(awayName, true)
                "league", "tournament" ->
                    favorite.id == competitionId || favorite.slug == competitionSlug ||
                    favorite.name.equals(competitionName, true) ||
                    (competitionName.isNotBlank() && competitionName.contains(favorite.name, true))
                "player" -> event != null && (
                    favorite.id == eventPlayerId || favorite.id == assistPlayerId ||
                    favorite.slug == eventPlayerId || favorite.slug == assistPlayerId ||
                    favorite.name.equals(eventPlayerName, true) ||
                    favorite.name.equals(assistPlayerName, true)
                )
                else -> false
            }
        }
    }

    private fun newsMatchesFavorite(context: Context, item: JSONObject): Boolean {
        if (!KoraxFavoriteStore.setupComplete(context)) return false
        val all = KoraxFavoriteStore.all(context)
        if (all.isEmpty()) return true
        val followed = all.filter { it.notify }
        if (followed.isEmpty()) return false
        val text = (item.optString("title") + " " + item.optString("summary")).lowercase(Locale.ROOT)
        val category = item.optString("category").lowercase(Locale.ROOT)
        return followed.any { favorite ->
            text.contains(favorite.name.lowercase(Locale.ROOT)) ||
                (favorite.kind == "league" && category == favorite.slug.lowercase(Locale.ROOT)) ||
                (favorite.kind == "tournament" && text.contains(favorite.name.lowercase(Locale.ROOT)))
        }
    }

    private fun scoreOrUnknown(raw: Any?): Int =
        if (raw == null || raw == JSONObject.NULL) -1 else raw.toString().toIntOrNull() ?: -1

    private fun scoreLabel(match: JSONObject): String {
        val home = scoreOrUnknown(match.opt("homeScore"))
        val away = scoreOrUnknown(match.opt("awayScore"))
        return if (home >= 0 && away >= 0) "$home - $away" else "VS"
    }

    private fun readSet(raw: String?): MutableSet<String> {
        val array = runCatching { JSONArray(raw ?: "[]") }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { array.optString(it).takeIf { value -> value.isNotBlank() } }
            .toMutableSet()
    }

    private fun writeSet(values: Set<String>, maxItems: Int): String {
        val array = JSONArray()
        values.toList().takeLast(maxItems).forEach(array::put)
        return array.toString()
    }

    private fun send(
        context: Context,
        kind: String,
        title: String,
        message: String,
        iconUrl: String,
        pictureUrl: String,
        uniqueKey: String
    ) {
        if (!hasPermission(context)) return
        ensureChannels(context)
        val withSound = soundEnabled(context)
        val channel = channelId(kind, withSound)
        val iconBitmap = downloadImage(iconUrl)
        val pictureBitmap = downloadImage(pictureUrl)
        val intent = Intent(context, VisualShellActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val requestCode = uniqueKey.hashCode() and 0x7fffffff
        val pendingIntent = PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setColor(android.graphics.Color.rgb(213, 255, 0))
            .setCategory(if (kind == "news") NotificationCompat.CATEGORY_RECOMMENDATION else NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (iconBitmap != null) builder.setLargeIcon(iconBitmap)
        if (pictureBitmap != null) {
            builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(pictureBitmap).setSummaryText(message))
        }
        runCatching {
            NotificationManagerCompat.from(context).notify(requestCode, builder.build())
            saveHistory(context, AlertHistoryItem(kind, title, message, System.currentTimeMillis()))
        }
    }

    private fun downloadImage(address: String): Bitmap? {
        if (!address.startsWith("https://")) return null
        val connection = runCatching { URL(address).openConnection() as HttpURLConnection }.getOrNull() ?: return null
        return try {
            connection.connectTimeout = 3500
            connection.readTimeout = 4500
            connection.instanceFollowRedirects = true
            if (connection.responseCode !in 200..299) return null
            BitmapFactory.decodeStream(connection.inputStream)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun saveHistory(context: Context, item: AlertHistoryItem) {
        val array = runCatching { JSONArray(prefs(context).getString(HISTORY_KEY, "[]")) }
            .getOrDefault(JSONArray())
        val next = JSONArray()
        next.put(JSONObject()
            .put("kind", item.kind)
            .put("title", item.title)
            .put("message", item.message)
            .put("createdAt", item.createdAt))
        for (index in 0 until array.length()) {
            if (next.length() >= 79) break
            next.put(array.optJSONObject(index) ?: continue)
        }
        prefs(context).edit().putString(HISTORY_KEY, next.toString()).apply()
    }

    fun history(context: Context): List<AlertHistoryItem> {
        val array = runCatching { JSONArray(prefs(context).getString(HISTORY_KEY, "[]")) }
            .getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            AlertHistoryItem(item.optString("kind"), item.optString("title"), item.optString("message"), item.optLong("createdAt"))
        }
    }

    fun sendTest(context: Context, kind: String = "goal") {
        val label = when (kind) {
            "goal" -> "⚽ الهدف"
            "assist" -> "🎯 التمريرة الحاسمة"
            "var" -> "📺 مراجعة VAR"
            "cancel" -> "🚫 إلغاء الهدف"
            "red_card" -> "🟥 البطاقة الحمراء"
            "yellow_card" -> "🟨 البطاقة الصفراء"
            "penalty" -> "⚽ ركلة الجزاء"
            "kickoff" -> "🚩 بداية المباراة"
            "halftime" -> "⏸️ الاستراحة"
            "fulltime" -> "🏁 نهاية المباراة"
            "substitution" -> "🔄 التبديل"
            "injury" -> "🩺 الإصابة"
            else -> "📰 الخبر العاجل"
        }
        send(
            context, kind, "اختبار صوت $label",
            "هذا مؤثر صوتي تجريبي مخصص لهذا النوع من تنبيهات KORAX.",
            "", "", "korax-test-$kind-${System.currentTimeMillis()}"
        )
    }
}

@Composable
internal fun KoraxNotificationsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val toggles = remember {
        mutableStateMapOf<String, Boolean>().apply {
            alertSettings.forEach { put(it.key, KoraxSmartAlerts.isEnabled(context, it.key)) }
        }
    }
    var soundEnabled by remember { mutableStateOf(KoraxSmartAlerts.soundEnabled(context)) }
    var permissionGranted by remember { mutableStateOf(KoraxSmartAlerts.hasPermission(context)) }
    var history by remember { mutableStateOf(KoraxSmartAlerts.history(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }

    LaunchedEffect(Unit) {
        KoraxSmartAlerts.ensureChannels(context)
        history = KoraxSmartAlerts.history(context)
        permissionGranted = KoraxSmartAlerts.hasPermission(context)
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF07090D))
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onBack).padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("‹", color = Color(0xFFD5FF00), fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("مركز الإشعارات الذكية", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111923)),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Color(0xFF2B3947)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.fillMaxWidth().padding(15.dp)) {
                Text(
                    if (permissionGranted) "الإشعارات مفعّلة على الجهاز" else "فعّل إشعارات KORAX",
                    color = if (permissionGranted) Color(0xFFD5FF00) else Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    if (permissionGranted) "سيظهر تنبيه لكل حدث حسب اختياراتك وصوت الحدث." else "لا يمكن عرض تنبيهات النظام قبل منح التطبيق الإذن.",
                    color = Color(0xFF9AA5B1), style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else permissionGranted = KoraxSmartAlerts.hasPermission(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD5FF00), contentColor = Color.Black)
                ) {
                    Text(if (permissionGranted) "إعادة طلب الإذن" else "تفعيل إشعارات الهاتف", fontWeight = FontWeight.Bold)
                }
                if (!permissionGranted) {
                    Text(
                        "لو كنت رفضت الإذن نهائيًا، افتح إعدادات إشعارات التطبيق وفعّله يدويًا.",
                        color = Color(0xFF9AA5B1), style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 5.dp).clickable {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                            }
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("أنواع التنبيهات", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Spacer(Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF11161E)),
            shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 5.dp)) {
                alertSettings.forEachIndexed { index, setting ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(setting.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(setting.description, color = Color(0xFF929AA6), fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Switch(
                                checked = toggles[setting.key] ?: true,
                                onCheckedChange = { checked ->
                                    toggles[setting.key] = checked
                                    KoraxSmartAlerts.setEnabled(context, setting.key, checked)
                                }
                            )
                            Text(
                                "اختبار",
                                color = if (permissionGranted) Color(0xFFD5FF00) else Color(0xFF718092),
                                fontSize = 10.sp,
                                modifier = Modifier.clickable(enabled = permissionGranted) {
                                    KoraxSmartAlerts.sendTest(context, setting.key)
                                    history = KoraxSmartAlerts.history(context)
                                }.padding(4.dp)
                            )
                        }
                    }
                    if (index < alertSettings.lastIndex) Divider(color = Color(0xFF27303B))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF11161E)),
            shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("المؤثرات الصوتية", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("صوت مستقل للهدف والتمريرة وVAR والإلغاء والبطاقات والأخبار", color = Color(0xFF929AA6), fontSize = 11.sp)
                    }
                    Switch(checked = soundEnabled, onCheckedChange = {
                        soundEnabled = it
                        KoraxSmartAlerts.setSoundEnabled(context, it)
                    })
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { KoraxSmartAlerts.sendTest(context); history = KoraxSmartAlerts.history(context) },
                    enabled = permissionGranted,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27303B), contentColor = Color.White)
                ) { Text("اختبار صوت إشعار الهدف") }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("آخر الإشعارات", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Spacer(Modifier.height(8.dp))
        if (history.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF11161E)),
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
            ) {
                Text("لا توجد إشعارات محفوظة بعد. ستظهر هنا تنبيهات الأهداف والأخبار والأحداث.",
                    color = Color(0xFF929AA6), modifier = Modifier.padding(16.dp))
            }
        } else {
            history.take(12).forEach { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF11161E)),
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp)
                ) {
                    Column(Modifier.padding(13.dp)) {
                        Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(3.dp))
                        Text(item.message, color = Color(0xFF9AA5B1), fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(
                            java.text.SimpleDateFormat("dd/MM  HH:mm", Locale.getDefault()).format(java.util.Date(item.createdAt)),
                            color = Color(0xFF718092), fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(26.dp))
        Text(
            "آلية التحديث: أثناء فتح التطبيق يتم فحص المباريات دوريًا. عند إغلاقه يستخدم Android مهمة خلفية دورية وقد يتأخر وصول التنبيه حسب النظام والبطارية.",
            color = Color(0xFF718092), fontSize = 10.sp, modifier = Modifier.padding(bottom = 18.dp)
        )
    }
}
