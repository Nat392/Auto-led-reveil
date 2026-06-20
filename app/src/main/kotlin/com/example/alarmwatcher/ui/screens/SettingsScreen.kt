@file:Suppress("MagicNumber")

package com.example.alarmwatcher.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.alarmwatcher.settings.AppSettings
import com.example.alarmwatcher.ui.SettingsViewModel

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val contentModifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)

    Column(modifier = contentModifier) {
        Text(
            text = "Réglages",
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineMedium,
        )

        SunriseSection(settings, viewModel)
        SunsetSection(settings, viewModel)
        NightFadeSection(settings, viewModel)
        DaylightHarvestingSection(settings, viewModel)
        LocationSection(settings, viewModel)
        AdvancedSection(settings, viewModel)
    }
}

@Composable
private fun SunriseSection(
    settings: AppSettings,
    viewModel: SettingsViewModel,
) {
    val preWarnHelp = "Durée pendant laquelle la lumière monte avant l'alarme, comme un lever de soleil."

    SettingsSectionTitle("Aube — rampe lumineuse avant l'alarme")
    IntSliderSetting(
        label = SettingLabel("Durée de la pré-chauffe", preWarnHelp),
        value = settings.preWarnMinutes,
        onValueChange = { viewModel.update { s -> s.copy(preWarnMinutes = it) } },
        valueRange = 5..90,
        valueText = { "$it min avant l'alarme" },
    )
    ColorPickerSetting(
        label = SettingLabel("Couleur d'aube — Bureau"),
        color = settings.bureauSunriseColor,
        onColorChange = { c -> viewModel.update { it.copy(bureauSunriseColor = c) } },
    )
    ColorPickerSetting(
        label = SettingLabel("Couleur d'aube — Chambre"),
        color = settings.chambreSunriseColor,
        onColorChange = { c -> viewModel.update { it.copy(chambreSunriseColor = c) } },
    )
    ColorPickerSetting(
        label = SettingLabel("Couleur d'aube — Cuisine"),
        color = settings.cuisineSunriseColor,
        onColorChange = { c -> viewModel.update { it.copy(cuisineSunriseColor = c) } },
    )
}

@Composable
private fun SunsetSection(
    settings: AppSettings,
    viewModel: SettingsViewModel,
) {
    val offsetHelp = "Minutes avant le coucher du soleil où cette pièce passe en couleurs chaudes (mode soirée)."

    SettingsSectionTitle("Soirée — mode ambre/rouge")
    ColorPickerSetting(
        label = SettingLabel("Couleur de soirée — Bureau"),
        color = settings.bureauSunsetColor,
        onColorChange = { c -> viewModel.update { it.copy(bureauSunsetColor = c) } },
    )
    IntSliderSetting(
        label = SettingLabel("Décalage soirée — Bureau", offsetHelp),
        value = settings.bureauSunsetOffsetMinutes,
        onValueChange = { viewModel.update { s -> s.copy(bureauSunsetOffsetMinutes = it) } },
        valueRange = 0..120,
        valueText = { "$it min avant le coucher du soleil" },
    )
    ColorPickerSetting(
        label = SettingLabel("Couleur de soirée — Chambre"),
        color = settings.chambreSunsetColor,
        onColorChange = { c -> viewModel.update { it.copy(chambreSunsetColor = c) } },
    )
    IntSliderSetting(
        label = SettingLabel("Décalage soirée — Chambre", offsetHelp),
        value = settings.chambreSunsetOffsetMinutes,
        onValueChange = { viewModel.update { s -> s.copy(chambreSunsetOffsetMinutes = it) } },
        valueRange = 0..120,
        valueText = { "$it min avant le coucher du soleil" },
    )
    ColorPickerSetting(
        label = SettingLabel("Couleur de soirée — Cuisine"),
        color = settings.cuisineSunsetColor,
        onColorChange = { c -> viewModel.update { it.copy(cuisineSunsetColor = c) } },
    )
    IntSliderSetting(
        label = SettingLabel("Décalage soirée — Cuisine", offsetHelp),
        value = settings.cuisineSunsetOffsetMinutes,
        onValueChange = { viewModel.update { s -> s.copy(cuisineSunsetOffsetMinutes = it) } },
        valueRange = 0..120,
        valueText = { "$it min avant le coucher du soleil" },
    )
}

@Composable
private fun NightFadeSection(
    settings: AppSettings,
    viewModel: SettingsViewModel,
) {
    val snoozeHelp = "Si vous repoussez l'alarme de moins de cette durée, la rampe de l'aube reste programmée."
    val leadHelp = "Temps avant l'alarme où la lumière baisse doucement le soir pour préparer l'endormissement."
    val windowHelp = "Si l'alarme sonne dans cette plage le matin, le fondu nocturne de la veille est pris en compte."

    SettingsSectionTitle("Fondu nocturne — préparation à l'endormissement")
    IntSliderSetting(
        label = SettingLabel("Tolérance de répétition (snooze)", snoozeHelp),
        value = settings.snoozeToleranceMinutes,
        onValueChange = { viewModel.update { s -> s.copy(snoozeToleranceMinutes = it) } },
        valueRange = 5..60,
        valueText = { "$it min" },
    )
    IntSliderSetting(
        label = SettingLabel("Avance du fondu nocturne", leadHelp),
        value = settings.nightFadeLeadTimeMinutes,
        onValueChange = { viewModel.update { s -> s.copy(nightFadeLeadTimeMinutes = it) } },
        valueRange = 360..720,
        valueText = { leadTimeText(it) },
    )
    TimeOfDaySetting(
        label = SettingLabel("Début de la fenêtre matinale du fondu nocturne", windowHelp),
        hour = minutesToHour(settings.nightFadeMorningStartMinutes),
        minute = minutesToMinute(settings.nightFadeMorningStartMinutes),
        onHourChange = { h -> viewModel.update { it.withMorningStartHour(h) } },
        onMinuteChange = { m -> viewModel.update { it.withMorningStartMinute(m) } },
    )
    TimeOfDaySetting(
        label = SettingLabel("Fin de la fenêtre matinale du fondu nocturne", windowHelp),
        hour = minutesToHour(settings.nightFadeMorningEndMinutes),
        minute = minutesToMinute(settings.nightFadeMorningEndMinutes),
        onHourChange = { h -> viewModel.update { it.withMorningEndHour(h) } },
        onMinuteChange = { m -> viewModel.update { it.withMorningEndMinute(m) } },
    )
}

@Composable
private fun DaylightHarvestingSection(
    settings: AppSettings,
    viewModel: SettingsViewModel,
) {
    val intervalHelp = "Fréquence de vérification de la luminosité extérieure pour ajuster les lampes Bureau/Cuisine."
    val fadeHelp = "Temps pour qu'une lampe passe en douceur d'un état lumineux à un autre lors d'un ajustement."
    val thresholdHelp = "Au-delà de cette luminosité, Bureau et Cuisine s'éteignent (jour suffisant)."

    SettingsSectionTitle("Récupération de la lumière du jour")
    IntSliderSetting(
        label = SettingLabel("Intervalle de vérification", intervalHelp),
        value = settings.daylightIntervalMinutes,
        onValueChange = { viewModel.update { s -> s.copy(daylightIntervalMinutes = it) } },
        valueRange = 5..60,
        valueText = { "$it min" },
    )
    IntSliderSetting(
        label = SettingLabel("Durée du fondu d'ajustement", fadeHelp),
        value = settings.daylightFadeDurationMinutes,
        onValueChange = { viewModel.update { s -> s.copy(daylightFadeDurationMinutes = it) } },
        valueRange = 1..30,
        valueText = { "$it min" },
    )
    DoubleSliderSetting(
        label = SettingLabel("Seuil de saturation solaire", thresholdHelp),
        value = settings.solarSaturationThresholdWm2,
        onValueChange = { viewModel.update { s -> s.copy(solarSaturationThresholdWm2 = it) } },
        sliderRange = SliderRange(range = 100f..1000f, steps = 89),
        valueText = { "${it.toInt()} W/m²" },
    )
}

@Composable
private fun LocationSection(
    settings: AppSettings,
    viewModel: SettingsViewModel,
) {
    val locationHelp = "Coordonnées GPS utilisées pour calculer le coucher du soleil et l'ensoleillement de votre zone."

    SettingsSectionTitle("Localisation")
    CoordinateField(
        label = SettingLabel("Latitude", locationHelp),
        value = settings.latitude,
        onValueChange = { viewModel.update { s -> s.copy(latitude = it) } },
    )
    CoordinateField(
        label = SettingLabel("Longitude", locationHelp),
        value = settings.longitude,
        onValueChange = { viewModel.update { s -> s.copy(longitude = it) } },
    )
}

@Composable
private fun AdvancedSection(
    settings: AppSettings,
    viewModel: SettingsViewModel,
) {
    val refreshHelp = "Heure à laquelle l'app recalcule chaque jour l'heure du coucher de soleil du lendemain."

    SettingsSectionTitle("Avancé")
    TimeOfDaySetting(
        label = SettingLabel("Heure de rafraîchissement du coucher de soleil", refreshHelp),
        hour = settings.sunsetRefreshHour,
        minute = settings.sunsetRefreshMinute,
        onHourChange = { h -> viewModel.update { it.copy(sunsetRefreshHour = h) } },
        onMinuteChange = { m -> viewModel.update { it.copy(sunsetRefreshMinute = m) } },
    )
}
