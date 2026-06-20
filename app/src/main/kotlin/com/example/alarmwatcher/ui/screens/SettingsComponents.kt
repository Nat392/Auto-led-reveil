@file:Suppress("MagicNumber")

package com.example.alarmwatcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.alarmwatcher.settings.RgbColor

/**
 * Titre et explication vulgarisée ("?") d'un réglage.
 */
data class SettingLabel(val title: String, val help: String? = null)

/**
 * Titre de section dans l'écran Réglages.
 */
@Composable
fun SettingsSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

/**
 * Bouton "?" affichant une explication vulgarisée dans une boîte de dialogue.
 */
@Composable
fun HelpDialogButton(
    explanation: String,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }

    TextButton(onClick = { showDialog = true }, modifier = modifier) {
        Text("?")
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("Compris") }
            },
            text = { Text(explanation) },
        )
    }
}

@Composable
private fun SettingHeaderRow(
    label: SettingLabel,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Text(text = label.title, style = MaterialTheme.typography.bodyLarge)
        if (label.help != null) {
            HelpDialogButton(explanation = label.help)
        }
    }
}

/**
 * Réglage numérique entier représenté par un slider, avec libellé de valeur personnalisable.
 */
@Composable
fun IntSliderSetting(
    label: SettingLabel,
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange,
    valueText: (Int) -> String = { "$it" },
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        SettingHeaderRow(label = label)
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = (valueRange.last - valueRange.first - 1).coerceAtLeast(0),
        )
        Text(text = valueText(value), style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Plage et nombre de crans d'un [DoubleSliderSetting].
 */
data class SliderRange(val range: ClosedFloatingPointRange<Float>, val steps: Int)

/**
 * Réglage numérique décimal représenté par un slider, avec libellé de valeur personnalisable.
 */
@Composable
fun DoubleSliderSetting(
    label: SettingLabel,
    value: Double,
    onValueChange: (Double) -> Unit,
    sliderRange: SliderRange,
    valueText: (Double) -> String,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        SettingHeaderRow(label = label)
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toDouble()) },
            valueRange = sliderRange.range,
            steps = sliderRange.steps,
        )
        Text(text = valueText(value), style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Sélecteur de couleur RGB (curseurs Rouge/Vert/Bleu) avec aperçu de la couleur résultante.
 */
@Composable
fun ColorPickerSetting(
    label: SettingLabel,
    color: RgbColor,
    onColorChange: (RgbColor) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        SettingHeaderRow(label = label)
        val swatchColor = Color(color.red, color.green, color.blue)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(swatchColor))
            Spacer(modifier = Modifier.width(12.dp))
            ColorPreviewText(color)
        }
        ColorChannelSlider("Rouge", color.red) { onColorChange(color.copy(red = it)) }
        ColorChannelSlider("Vert", color.green) { onColorChange(color.copy(green = it)) }
        ColorChannelSlider("Bleu", color.blue) { onColorChange(color.copy(blue = it)) }
    }
}

@Composable
private fun ColorPreviewText(color: RgbColor) {
    val text = "RGB(${color.red}, ${color.green}, ${color.blue})"
    Text(text = text, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ColorChannelSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text = label, modifier = Modifier.width(56.dp), style = MaterialTheme.typography.bodySmall)
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..255f,
            steps = 254,
            modifier = Modifier.weight(1f),
        )
        Text(text = "$value", modifier = Modifier.width(40.dp), style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Champ de saisie d'une coordonnée géographique (latitude ou longitude).
 */
@Composable
fun CoordinateField(
    label: SettingLabel,
    value: Double,
    onValueChange: (Double) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        SettingHeaderRow(label = label)
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input
                input.toDoubleOrNull()?.let(onValueChange)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

/**
 * Réglage d'une heure de la journée (heure et minute) via deux curseurs.
 */
@Composable
fun TimeOfDaySetting(
    label: SettingLabel,
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        SettingHeaderRow(label = label)
        Text(
            text = "%02d:%02d".format(hour, minute),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(text = "Heure", style = MaterialTheme.typography.bodySmall)
        Slider(value = hour.toFloat(), onValueChange = { onHourChange(it.toInt()) }, valueRange = 0f..23f, steps = 22)
        Text(text = "Minute", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = minute.toFloat(),
            onValueChange = { onMinuteChange(it.toInt()) },
            valueRange = 0f..59f,
            steps = 58,
        )
    }
}
