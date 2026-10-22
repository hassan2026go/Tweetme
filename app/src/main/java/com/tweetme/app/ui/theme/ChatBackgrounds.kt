package com.tweetme.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

/** خلفية جاهزة للمحادثة. brush = null تعني خلفية الثيم. لإضافة خلفية جديدة أضف سطراً في القائمة. */
data class ChatBg(val id: String, val brush: Brush?)

val chatBackgrounds: List<ChatBg> = listOf(
    ChatBg("default", null),
    ChatBg("navy", SolidColor(Color(0xFF0B1426))),
    ChatBg("ink", SolidColor(Color(0xFF02040A))),
    ChatBg("g1", Brush.linearGradient(listOf(Color(0xFF071226), Color(0xFF0B2A5B)))),
    ChatBg("g2", Brush.radialGradient(listOf(Color(0xFF12346B), Color(0xFF050912)))),
    ChatBg("g3", Brush.linearGradient(listOf(Color(0xFF000000), Color(0xFF0A1F44), Color(0xFF123D86)))),
    ChatBg("sky", SolidColor(Color(0xFFE8F0FF))),
    ChatBg("white", SolidColor(Color(0xFFFFFFFF)))
)

/** معرّف خلفية الصورة المختارة من المعرض. */
const val BG_IMAGE_ID = "image"
