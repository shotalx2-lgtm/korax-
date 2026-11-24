package com.koora.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

internal data class KoraxFavorite(
    val id: String,
    val slug: String,
    val name: String,
    val kind: String,
    val imageUrl: String = "",
    val country: String = "",
    val notify: Boolean = true
)

internal object KoraxFavoriteStore {
    private const val PREFS = "korax_user_preferences_v1"
    private const val ITEMS = "favorite_entities"
    private const val SETUP_DONE = "favorite_setup_complete"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): List<KoraxFavorite> {
        val array = runCatching { JSONArray(prefs(context).getString(ITEMS, "[]")) }
            .getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            val name = item.optString("name")
            if (id.isBlank() || name.isBlank()) return@mapNotNull null
            KoraxFavorite(
                id = id,
                slug = item.optString("slug", id),
                name = name,
                kind = item.optString("kind", "club"),
                imageUrl = item.optString("imageUrl"),
                country = item.optString("country"),
                notify = item.optBoolean("notify", true)
            )
        }
    }

    fun save(context: Context, favorites: List<KoraxFavorite>) {
        val array = JSONArray()
        favorites.distinctBy { it.kind + ":" + it.id }.take(300).forEach { item ->
            array.put(JSONObject()
                .put("id", item.id)
                .put("slug", item.slug)
                .put("name", item.name)
                .put("kind", item.kind)
                .put("imageUrl", item.imageUrl)
                .put("country", item.country)
                .put("notify", item.notify))
        }
        prefs(context).edit().putString(ITEMS, array.toString()).apply()
    }

    fun toggle(context: Context, favorite: KoraxFavorite) {
        val current = all(context).toMutableList()
        val index = current.indexOfFirst { it.id == favorite.id && it.kind == favorite.kind }
        if (index >= 0) current.removeAt(index) else current.add(favorite)
        save(context, current)
    }

    fun updateNotification(context: Context, favorite: KoraxFavorite, enabled: Boolean) {
        val current = all(context).map {
            if (it.id == favorite.id && it.kind == favorite.kind) it.copy(notify = enabled) else it
        }
        save(context, current)
    }

    fun isFavorite(context: Context, favorite: KoraxFavorite): Boolean =
        all(context).any { it.id == favorite.id && it.kind == favorite.kind }

    fun setupComplete(context: Context): Boolean =
        prefs(context).getBoolean(SETUP_DONE, false)

    fun setSetupComplete(context: Context, complete: Boolean) {
        prefs(context).edit().putBoolean(SETUP_DONE, complete).apply()
    }

    fun followed(context: Context): List<KoraxFavorite> = all(context).filter { it.notify }
}

internal object KoraxSettingsStore {
    private const val PREFS = "korax_general_settings_v1"
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getBoolean(context: Context, key: String, default: Boolean) =
        prefs(context).getBoolean(key, default)

    fun setBoolean(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }

    fun theme(context: Context): String = prefs(context).getString("theme", "dark") ?: "dark"

    fun setTheme(context: Context, value: String) {
        prefs(context).edit().putString("theme", value).apply()
        KoraxThemePalette.applyLightMode(value == "light")
    }

    fun requestPermissionAsked(context: Context): Boolean =
        prefs(context).getBoolean("notification_permission_prompted", false)

    fun markPermissionAsked(context: Context) {
        prefs(context).edit().putBoolean("notification_permission_prompted", true).apply()
    }
}

internal object KoraxThemePalette {
    var light by mutableStateOf(false)
        private set

    fun applyLightMode(value: Boolean) {
        light = value
    }

    val background: Color get() = if (light) Color.White else Color(0xFF030609)
    val card: Color get() = if (light) Color.White else Color(0xFF0B1016)
    val card2: Color get() = if (light) Color(0xFFE8ECF1) else Color(0xFF131C26)
    val text: Color get() = if (light) Color(0xFF151A21) else Color(0xFFF6F8FB)
    val muted: Color get() = if (light) Color(0xFF66717E) else Color(0xFF8B98A8)
    val border: Color get() = if (light) Color(0xFFD8DEE6) else Color(0xFF202C38)
    val accent: Color get() = if (light) Color(0xFF6B7F00) else Color(0xFFD5FF00)
}

internal class LocalNewsInteractions(context: Context) {
    private val prefs = context.getSharedPreferences("korax_news_interactions_v2", Context.MODE_PRIVATE)
    private fun key(url: String) = "news_" + url.hashCode().toUInt().toString(16)

    fun liked(url: String) = prefs.getBoolean(key(url) + "_liked", false)
    fun saved(url: String) = prefs.getBoolean(key(url) + "_saved", false)
    fun toggleLike(url: String): Boolean {
        val next = !liked(url)
        prefs.edit().putBoolean(key(url) + "_liked", next).apply()
        return next
    }
    fun toggleSaved(url: String): Boolean {
        val next = !saved(url)
        prefs.edit().putBoolean(key(url) + "_saved", next).apply()
        return next
    }
    fun comments(url: String): List<String> = runCatching {
        val array = JSONArray(prefs.getString(key(url) + "_comments", "[]"))
        (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())
    fun addComment(url: String, text: String): List<String> {
        val next = (comments(url) + text.trim()).takeLast(100)
        val array = JSONArray()
        next.forEach(array::put)
        prefs.edit().putString(key(url) + "_comments", array.toString()).apply()
        return next
    }
}
