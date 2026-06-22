@file:Suppress("MagicNumber")

package com.example.alarmwatcher.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        BleDevicesSection(settings, viewModel)
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
    val intervalHelp = "Fréquence de vérification de la luminosité extérieure pour ajuster les lampes Chambre/Cuisine."
    val fadeHelp = "Temps pour qu'une lampe passe en douceur d'un état lumineux à un autre lors d'un ajustement."
    val thresholdHelp =
        "Au-delà de cette luminosité extérieure (mesurée en W/m²), cette pièce considère qu'elle reçoit assez " +
            "de lumière du jour et la lampe s'éteint. Plus une pièce a de petites fenêtres, plus ce seuil doit " +
            "être bas."

    SettingsSectionTitle("Récupération de la lumière du jour — Chambre et Cuisine uniquement")
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
        label = SettingLabel("Seuil de saturation solaire — Chambre", thresholdHelp),
        value = settings.chambreSolarThresholdWm2,
        onValueChange = { viewModel.update { s -> s.copy(chambreSolarThresholdWm2 = it) } },
        sliderRange = SliderRange(range = 50f..1000f, steps = 94),
        valueText = { "${it.toInt()} W/m²" },
    )
    DoubleSliderSetting(
        label = SettingLabel("Seuil de saturation solaire — Cuisine", thresholdHelp),
        value = settings.cuisineSolarThresholdWm2,
        onValueChange = { viewModel.update { s -> s.copy(cuisineSolarThresholdWm2 = it) } },
        sliderRange = SliderRange(range = 50f..1000f, steps = 94),
        valueText = { "${it.toInt()} W/m²" },
    )
}

@Composable
private fun BleDevicesSection(
    settings: AppSettings,
    viewModel: SettingsViewModel,
) {
    var showScanDialog by remember { mutableStateOf(false) }
    val macHelp = "Laisser vide pour utiliser l'adresse configurée à la compilation (local.properties)."

    SettingsSectionTitle("Appareils BLE")
    TextButton(onClick = { showScanDialog = true }) {
        Text("Scanner les ampoules BLE à proximité")
    }
    MacAddressField(
        label = SettingLabel("Adresse MAC — Bureau", macHelp),
        value = settings.bureauMacAddress,
        onValueChange = { viewModel.update { s -> s.copy(bureauMacAddress = it) } },
    )
    MacAddressField(
        label = SettingLabel("Adresse MAC — Chambre", macHelp),
        value = settings.chambreMacAddress,
        onValueChange = { viewModel.update { s -> s.copy(chambreMacAddress = it) } },
    )
    MacAddressField(
        label = SettingLabel("Adresse MAC — Cuisine", macHelp),
        value = settings.cuisineMacAddress,
        onValueChange = { viewModel.update { s -> s.copy(cuisineMacAddress = it) } },
    )

    if (showScanDialog) {
        BleScanDialog(
            onDismiss = { showScanDialog = false },
            onAssign = { zoneLabel, macAddress ->
                viewModel.update { s ->
                    when (zoneLabel) {
                        "Bureau" -> s.copy(bureauMacAddress = macAddress)
                        "Chambre" -> s.copy(chambreMacAddress = macAddress)
                        "Cuisine" -> s.copy(cuisineMacAddress = macAddress)
                        else -> s
                    }
                }
                showScanDialog = false
            },
        )
    }
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
    val retryAttemptsHelp = "Nombre de tentatives avant d'abandonner l'envoi d'une commande BLE à une ampoule."
    val retryDelayHelp = "Temps d'attente entre deux tentatives d'envoi d'une commande BLE."
    val connectTimeoutHelp = "Temps maximal accordé à une ampoule pour répondre à une demande de connexion BLE."
    val opTimeoutHelp = "Temps maximal accordé à une ampoule pour répondre à une commande BLE une fois connectée."

    SettingsSectionTitle("Avancé")
    TimeOfDaySetting(
        label = SettingLabel("Heure de rafraîchissement du coucher de soleil", refreshHelp),
        hour = settings.sunsetRefreshHour,
        minute = settings.sunsetRefreshMinute,
        onHourChange = { h -> viewModel.update { it.copy(sunsetRefreshHour = h) } },
        onMinuteChange = { m -> viewModel.update { it.copy(sunsetRefreshMinute = m) } },
    )

    SettingsSectionTitle("Avancé — robustesse BLE")
    IntSliderSetting(
        label = SettingLabel("Tentatives de connexion BLE (mode soirée)", retryAttemptsHelp),
        value = settings.sunsetSceneRetryAttempts,
        onValueChange = { viewModel.update { s -> s.copy(sunsetSceneRetryAttempts = it) } },
        valueRange = 1..6,
        valueText = { "$it tentative(s)" },
    )
    DoubleSliderSetting(
        label = SettingLabel("Délai entre tentatives", retryDelayHelp),
        value = settings.sunsetSceneRetryDelaySeconds,
        onValueChange = { viewModel.update { s -> s.copy(sunsetSceneRetryDelaySeconds = it) } },
        sliderRange = SliderRange(range = 0.5f..5.0f, steps = 8),
        valueText = { "$it s" },
    )
    IntSliderSetting(
        label = SettingLabel("Timeout de connexion BLE", connectTimeoutHelp),
        value = settings.bleConnectTimeoutSeconds,
        onValueChange = { viewModel.update { s -> s.copy(bleConnectTimeoutSeconds = it) } },
        valueRange = 5..30,
        valueText = { "$it s" },
    )
    IntSliderSetting(
        label = SettingLabel("Timeout d'une commande BLE", opTimeoutHelp),
        value = settings.bleOperationTimeoutSeconds,
        onValueChange = { viewModel.update { s -> s.copy(bleOperationTimeoutSeconds = it) } },
        valueRange = 2..20,
        valueText = { "$it s" },
    )
}
