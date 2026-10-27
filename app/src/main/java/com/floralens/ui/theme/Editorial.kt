package com.floralens.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Editorial-specific tokens; Material semantics remain available to legacy screens. */
object Editorial {
    val pageMargin = 16.dp
    val sectionGap = 24.dp
    val cardPadding = 20.dp
    val readingWidth = 680.dp
    val sage = Color(0xFFDCE6D6)
    val botanical = Color(0xFF879F7B)
    val warmBrown = Color(0xFFB59A7A) // decoration, not small body text
    val captionBrown = Color(0xFF896C53)
    val paper = Color(0xFFFFFCF7)
    val linen = Color(0xFFECF0E6)
    val hairline = Color(0xFFD6CDBD)
    val forest = Color(0xFF24452E)
    val ink = Color(0xFF193B27)
    val bodyInk = Color(0xFF32372F)
    val muted = Color(0xFF706B60)
    val photoScrim = Color(0xFF152A1B)
    val brand = TextStyle(fontFamily = Lora, fontSize = 30.sp, lineHeight = 35.sp)
    val sectionTitle = TextStyle(fontFamily = BotanicalSerif, fontWeight = FontWeight.Medium, fontSize = 23.sp, lineHeight = 29.sp)
    val chineseTitle = TextStyle(fontFamily = BotanicalSerif, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 35.sp)
    val scientific = TextStyle(fontFamily = Lora, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 21.sp)
    val eyebrow = TextStyle(fontFamily = Lora, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.sp)
}
