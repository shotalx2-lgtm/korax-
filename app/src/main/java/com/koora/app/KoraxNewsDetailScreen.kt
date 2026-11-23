package com.koora.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun KoraxNewsDetailScreen(article: NewsItem, onBack: () -> Unit) {
    val context = LocalContext.current
    val interactions = remember(context) { LocalNewsInteractions(context) }
    var liked by remember(article.url) { mutableStateOf(interactions.liked(article.url)) }
    var saved by remember(article.url) { mutableStateOf(interactions.saved(article.url)) }
    var comments by remember(article.url) { mutableStateOf(interactions.comments(article.url)) }
    var draft by remember(article.url) { mutableStateOf("") }
    var showComments by remember { mutableStateOf(false) }
    val bg = KoraxThemePalette.background
    val card = KoraxThemePalette.card
    val card2 = KoraxThemePalette.card2
    val text = KoraxThemePalette.text
    val muted = KoraxThemePalette.muted
    val accent = KoraxThemePalette.accent
    val border = KoraxThemePalette.border

    Column(Modifier.fillMaxSize().background(bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "رجوع", tint = accent) }
            Column(Modifier.weight(1f)) {
                Text("تفاصيل الخبر", color = text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text("تغطية KORAX الإخبارية", color = muted, fontSize = 9.sp)
            }
            IconButton(onClick = {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, article.title)
                    putExtra(Intent.EXTRA_TEXT, article.title + "\n" + article.url)
                }
                runCatching { context.startActivity(Intent.createChooser(share, "مشاركة الخبر")) }
            }) { Icon(Icons.Filled.Share, "مشاركة", tint = muted) }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)
        ) {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(21.dp),
                colors = CardDefaults.cardColors(containerColor = card),
                border = BorderStroke(1.dp, border)
            ) {
                Column {
                    if (!article.image.isNullOrBlank() &&
                        KoraxSettingsStore.getBoolean(context, "show_images", true) &&
                        !KoraxSettingsStore.getBoolean(context, "data_saver", false)
                    ) {
                        coil.compose.AsyncImage(
                            model = article.image,
                            contentDescription = article.title,
                            modifier = Modifier.fillMaxWidth().height(218.dp),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            Modifier.fillMaxWidth().height(150.dp)
                                .background(Brush.horizontalGradient(listOf(card2, card))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("KORAX", color = accent, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                        }
                    }
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(article.source, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text(formatNewsDate(article.date), color = muted, fontSize = 9.sp)
                        }
                        Spacer(Modifier.height(11.dp))
                        Text(article.title, color = text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 30.sp)
                        Spacer(Modifier.height(13.dp))
                        Divider(color = border)
                        Spacer(Modifier.height(13.dp))
                        Text("ملخص الخبر", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(7.dp))
                        if (article.summary.isNotBlank()) {
                            Text(article.summary, color = text, fontSize = 14.sp, lineHeight = 25.sp)
                        } else {
                            Text("لم يرفق الناشر ملخصًا لهذا الخبر. يمكنك فتح المصدر لقراءة التفاصيل الكاملة.", color = muted, fontSize = 12.sp, lineHeight = 21.sp)
                        }
                        Spacer(Modifier.height(15.dp))
                        Text(
                            "هذا ملخص الخبر الذي أتاحه المصدر عبر موجز الأخبار. لا ننسخ النص الكامل للمقال؛ التفاصيل الكاملة وحقوق النشر تبقى لدى الناشر.",
                            color = muted, fontSize = 10.sp, lineHeight = 17.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(11.dp))
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(17.dp),
                colors = CardDefaults.cardColors(containerColor = card),
                border = BorderStroke(1.dp, border)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ActionButton(
                        icon = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        label = if (liked) "أعجبني" else "إعجاب",
                        tint = if (liked) Color(0xFFFF5D78) else muted,
                        onClick = { liked = interactions.toggleLike(article.url) }
                    )
                    ActionButton(
                        icon = if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        label = if (saved) "تم الحفظ" else "حفظ",
                        tint = if (saved) accent else muted,
                        onClick = {
                            saved = interactions.toggleSaved(article.url)
                            KoraxOfflineStore.setSaved(context, article, saved)
                        }
                    )
                    ActionButton(
                        icon = Icons.Filled.Share,
                        label = "مشاركة",
                        tint = muted,
                        onClick = {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, article.title + "\n" + article.url)
                            }
                            runCatching { context.startActivity(Intent.createChooser(share, "مشاركة الخبر")) }
                        }
                    )
                    ActionButton(
                        icon = Icons.Filled.FavoriteBorder,
                        label = "${comments.size} تعليق",
                        tint = muted,
                        onClick = { showComments = !showComments }
                    )
                }
            }

            if (showComments) {
                Spacer(Modifier.height(12.dp))
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(17.dp),
                    colors = CardDefaults.cardColors(containerColor = card),
                    border = BorderStroke(1.dp, border)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("مناقشة الخبر", color = text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("تعليقاتك محفوظة على هذا الجهاز حاليًا؛ نشر تعليقات عامة بين المستخدمين يحتاج حسابًا وخدمة تعليقات مشتركة.", color = muted, fontSize = 9.sp, lineHeight = 15.sp)
                        Spacer(Modifier.height(10.dp))
                        if (comments.isEmpty()) {
                            Text("لا توجد تعليقات بعد. ابدأ النقاش.", color = muted, fontSize = 11.sp)
                        } else {
                            comments.takeLast(30).forEachIndexed { index, comment ->
                                Text(comment, color = text, fontSize = 11.sp, lineHeight = 18.sp, modifier = Modifier.padding(vertical = 6.dp))
                                if (index < comments.lastIndex) Divider(color = border)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it.take(500) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(13.dp),
                            label = { Text("اكتب تعليقك") },
                            maxLines = 3
                        )
                        Button(
                            onClick = {
                                if (draft.isNotBlank()) {
                                    comments = interactions.addComment(article.url, draft)
                                    draft = ""
                                }
                            },
                            enabled = draft.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                        ) { Text("نشر التعليق على هذا الجهاز", fontWeight = FontWeight.Bold) }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.url))) } },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
            ) {
                Text("قراءة التفاصيل الكاملة لدى ${article.source}  ↗", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 7.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, color = tint, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatNewsDate(raw: String): String {
    if (raw.isBlank()) return "التاريخ غير متاح"
    return runCatching {
        val parsed = java.time.Instant.parse(raw)
        SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault()).format(Date.from(parsed))
    }.getOrDefault(raw)
}
