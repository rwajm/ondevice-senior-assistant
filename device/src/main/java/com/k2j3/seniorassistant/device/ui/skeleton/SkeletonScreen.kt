package com.k2j3.seniorassistant.device.ui.skeleton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Temporary screen body: lists navigation and action buttons only. The UI part replaces it.
data class SkeletonButton(val label: String, val onClick: () -> Unit)

@Composable
fun SkeletonScreen(
    title: String,
    buttons: List<SkeletonButton>,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title)
            buttons.forEach { button ->
                Button(onClick = button.onClick) {
                    Text(text = button.label)
                }
            }
        }
    }
}
