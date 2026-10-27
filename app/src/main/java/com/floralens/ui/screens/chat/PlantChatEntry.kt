package com.floralens.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.floralens.R
import com.floralens.ui.components.KitIcon
import com.floralens.ui.theme.Editorial

@Composable
fun PlantChatEntryCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "从这株植物出发，聊聊自然与文化",
) {
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = 72.dp)
            .clickable(role = Role.Button, onClickLabel = "进入花间问答", onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Editorial.sage.copy(alpha = .58f),
        border = BorderStroke(1.dp, Editorial.forest.copy(alpha = .48f)),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = CircleShape, color = Editorial.paper, modifier = Modifier.size(46.dp)) {
                KitIcon(R.drawable.ic_chat_floral, Modifier.padding(9.dp), "花间问答")
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("花间问答", style = MaterialTheme.typography.titleMedium, color = Editorial.forest)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Editorial.captionBrown)
            }
            Spacer(Modifier.width(2.dp))
            KitIcon(R.drawable.wf_pd_icon_arrow_right, Modifier.size(22.dp), "进入")
        }
    }
}
