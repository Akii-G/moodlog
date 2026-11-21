package io.github.akiig.moodlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.akiig.moodlog.Feeling

/** The feeling words as pills that wrap onto several lines; tap to pick or unpick. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeelingChips(selected: Set<String>, onToggle: (String) -> Unit) {
    FlowRow(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (key in Feeling.keys) {
            val on = key in selected
            Text(Feeling.label(key),
                Modifier.clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    .border(1.5.dp, if (on) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                    .clickable { onToggle(key) }
                    .semantics { this.selected = on }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}
