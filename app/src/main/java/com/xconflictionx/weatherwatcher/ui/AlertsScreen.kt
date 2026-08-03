package com.xconflictionx.weatherwatcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.xconflictionx.weatherwatcher.data.AqiDayInfo
import com.xconflictionx.weatherwatcher.data.ForecastPeriod
import com.xconflictionx.weatherwatcher.data.PollenData
import com.xconflictionx.weatherwatcher.data.PollenDayInfo
import com.xconflictionx.weatherwatcher.data.WeatherEvent
import com.xconflictionx.weatherwatcher.data.*
import com.xconflictionx.weatherwatcher.util.ConsoleManager
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    viewModel: WeatherViewModel,
    onNavigateToSettings: () -> Unit
) {
    val currentWeather by viewModel.currentWeather.collectAsState()
    val activeAlerts by viewModel.activeAlerts.collectAsState()
    val location by viewModel.location.collectAsState()
    val nextRainTime by viewModel.nextRainTime.collectAsState()
    val hourlyForecast by viewModel.hourlyForecast.collectAsState()
    val dailyForecast by viewModel.dailyForecast.collectAsState()
    val pollenData by viewModel.pollenData.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isCurrentlyRaining by viewModel.isCurrentlyRaining.collectAsState()
    val trackWeatherEnabled by viewModel.trackWeatherEnabled.collectAsState()

    var selectedAlert by remember { mutableStateOf<WeatherEvent?>(null) }
    var selectedForecast by remember { mutableStateOf<ForecastPeriod?>(null) }
    var selectedAqi by remember { mutableStateOf<AqiDayInfo?>(null) }
    var selectedAllergen by remember { mutableStateOf<Pair<String, List<PollenDayInfo>>?>(null) }
    var showMainDetails by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Weather Watcher", fontWeight = FontWeight.ExtraBold) },
                    actions = {
                        IconButton(onClick = { viewModel.refreshWeather(isManual = true) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                if (isRefreshing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            errorMessage?.let {
                item {
                    Surface(
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                CurrentWeatherHero(
                    location = location,
                    weatherValues = currentWeather,
                    nextRainTime = nextRainTime,
                    isCurrentlyRaining = isCurrentlyRaining,
                    trackWeatherEnabled = trackWeatherEnabled,
                    onTrackToggle = { viewModel.updateTrackWeatherEnabled(it) },
                    onClick = { showMainDetails = true }
                )
            }

            if (hourlyForecast.isNotEmpty()) {
                item {
                    ForecastSection(
                        title = "Next 24 Hours", 
                        periods = hourlyForecast,
                        onItemClick = { selectedForecast = it }
                    )
                }
            }

            if (dailyForecast.isNotEmpty()) {
                item {
                    ForecastSection(
                        title = "7-Day Outlook", 
                        periods = dailyForecast, 
                        isDaily = true,
                        onItemClick = { selectedForecast = it }
                    )
                }
            }

            if (currentWeather?.aqiForecast?.isNotEmpty() == true) {
                item {
                    AqiOutlookSection(
                        aqiForecast = currentWeather!!.aqiForecast.take(5),
                        onTileClick = { selectedAqi = it }
                    )
                }
            }

            if (pollenData != null && pollenData!!.dailyPollen.isNotEmpty()) {
                item {
                    AllergyStream(
                        pollenData = pollenData!!,
                        onTileClick = { label -> selectedAllergen = label to pollenData!!.dailyPollen }
                    )
                }
            }

            item {
                Text(
                    text = "Active Hazards",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp)
                )
            }

            if (activeAlerts.isEmpty()) {
                item {
                    EmptyAlertsView()
                }
            } else {
                items(activeAlerts) { alert ->
                    AlertItem(
                        alert = alert,
                        onClick = { selectedAlert = alert }
                    )
                }
            }
            
            item { Spacer(modifier = Modifier.height(48.dp)) }
        }

        if (selectedAlert != null) {
            AlertDetailDialog(
                alert = selectedAlert!!,
                onDismiss = { selectedAlert = null }
            )
        }

        if (selectedForecast != null) {
            ForecastDetailDialog(
                period = selectedForecast!!,
                onDismiss = { selectedForecast = null }
            )
        }

        if (selectedAqi != null) {
            AqiDetailDialog(
                info = selectedAqi!!,
                onDismiss = { selectedAqi = null }
            )
        }

        if (selectedAllergen != null) {
            PollenTrendDialog(
                label = selectedAllergen!!.first,
                dailyPollen = selectedAllergen!!.second,
                onDismiss = { selectedAllergen = null }
            )
        }

        if (showMainDetails) {
            MainWeatherDetailDialog(
                location = location,
                weatherValues = currentWeather,
                nextRainTime = nextRainTime,
                isCurrentlyRaining = isCurrentlyRaining,
                dailyForecast = dailyForecast,
                onDismiss = { showMainDetails = false }
            )
        }
    }
}

@Composable
fun MainWeatherDetailDialog(
    location: String,
    weatherValues: WeatherValues?,
    nextRainTime: String?,
    isCurrentlyRaining: Boolean,
    dailyForecast: List<ForecastPeriod>,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Official Weather Briefing",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold
                )
                
                Text(
                    text = location.ifBlank { "Current Conditions" },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 1. Official NWS Detailed Summaries (Today & Tonight)
                val todayPeriod = dailyForecast.getOrNull(0)
                val tonightPeriod = dailyForecast.getOrNull(1)

                if (todayPeriod != null) {
                    BriefingSection(
                        title = todayPeriod.name ?: "Today",
                        summary = todayPeriod.detailedForecast ?: "No summary available.",
                        color = MaterialTheme.colorScheme.primaryContainer
                    )
                }

                if (tonightPeriod != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    BriefingSection(
                        title = tonightPeriod.name ?: "Tonight",
                        summary = tonightPeriod.detailedForecast ?: "No summary available.",
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
                
                // 2. Weather Visuals
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    WeatherIcon(condition = weatherValues?.condition ?: "N/A", size = 80.dp, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(20.dp))
                    Column {
                        Text(
                            text = if (weatherValues?.temperature != null) "${weatherValues.temperature.toInt()}°" else "--°",
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = weatherValues?.condition ?: "N/A",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
                HorizontalDivider(thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(24.dp))

                // 3. Technical Stats Grid
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem(
                            icon = Icons.Default.WaterDrop,
                            label = "Humidity",
                            value = "${weatherValues?.humidity?.toInt() ?: "N/A"}%",
                            modifier = Modifier.weight(1f)
                        )
                        StatItem(
                            icon = Icons.Default.Air,
                            label = "Wind Speed",
                            value = "${weatherValues?.windSpeed?.toInt() ?: "N/A"} mph",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem(
                            icon = Icons.Default.WbSunny,
                            label = "Sunrise",
                            value = weatherValues?.sunrise ?: "N/A",
                            modifier = Modifier.weight(1f)
                        )
                        StatItem(
                            icon = Icons.Default.WbTwilight,
                            label = "Sunset",
                            value = weatherValues?.sunset ?: "N/A",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem(
                            icon = Icons.Default.Cloud,
                            label = "AQI Index",
                            value = weatherValues?.aqi?.toString() ?: "N/A",
                            modifier = Modifier.weight(1f)
                        )
                        val rainProb = nextRainTime?.split("|")?.getOrNull(1) ?: "--"
                        StatItem(
                            icon = Icons.Default.WaterDrop,
                            label = "Rain Chance",
                            value = if (rainProb != "--") "$rainProb%" else "N/A",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 4. Detailed Rain Timing
                if (nextRainTime != null) {
                    val parts = nextRainTime.split("|")
                    val timeRaw = parts[0]
                    val prob = parts.getOrNull(1) ?: "0"
                    
                    val formattedTime = try {
                        val dt = ZonedDateTime.parse(timeRaw)
                        val now = ZonedDateTime.now()
                        val isToday = dt.toLocalDate() == now.toLocalDate()
                        val timeOnly = dt.format(DateTimeFormatter.ofPattern("h:mm a"))
                        if (isToday) "today at $timeOnly" else dt.format(DateTimeFormatter.ofPattern("EEE 'at' h:mm a"))
                    } catch (e: Exception) { "soon" }

                    val action = if (isCurrentlyRaining) "end" else "begin"
                    
                    Spacer(modifier = Modifier.height(28.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Rain expected to $action $formattedTime with a $prob% chance.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = onDismiss, 
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Close Full Report")
                }
            }
        }
    }
}

@Composable
fun BriefingSection(title: String, summary: String, color: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ForecastDetailDialog(period: ForecastPeriod, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (period.name.isNullOrBlank()) {
                        try {
                            ZonedDateTime.parse(period.startTime).format(DateTimeFormatter.ofPattern("h:mm a, MMM d"))
                        } catch (e: Exception) { 
                        ConsoleManager.logError("AlertsScreen", "Forecast time parsing error", e)
                        "Forecast Detail" 
                    }
                    } else period.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                WeatherIcon(condition = period.shortForecast ?: "", size = 72.dp, tint = MaterialTheme.colorScheme.primary)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "${period.temperature}°",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                
                Text(
                    text = period.shortForecast ?: "Unknown",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(24.dp))

                // Professional 2x3 Stats Grid
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem(
                            icon = Icons.Default.WaterDrop,
                            label = "Rain Chance",
                            value = "${period.probabilityOfPrecipitation?.value ?: 0}%",
                            modifier = Modifier.weight(1f)
                        )
                        StatItem(
                            icon = Icons.Default.Air,
                            label = "Wind",
                            value = "${period.windSpeed ?: "N/A"} ${period.windDirection ?: ""}",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem(
                            icon = Icons.Default.Opacity,
                            label = "Humidity",
                            value = "${period.relativeHumidity?.value ?: "N/A"}%",
                            modifier = Modifier.weight(1f)
                        )
                        StatItem(
                            icon = Icons.Default.Thermostat,
                            label = "Dew Point",
                            value = period.dewpoint?.value?.let { 
                                val f = (it * 9/5) + 32
                                "${f.toInt()}°"
                            } ?: "N/A",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                period.detailedForecast?.let {
                    if (it != period.shortForecast) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = it,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = onDismiss, 
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun StatItem(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AqiDetailDialog(info: AqiDayInfo, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Air Quality Breakdown",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = info.date,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                ) {
                    val progress = (info.maxAqi.toFloat() / 300f).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(getAqiColor(info.label), RoundedCornerShape(4.dp))
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "AQI ${info.maxAqi}", fontWeight = FontWeight.Bold)
                    Text(text = info.label, color = getAqiColor(info.label))
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(text = "Specific Pollutants", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                PollutantRow("PM2.5", info.pm2_5, "Fine particles")
                PollutantRow("PM10", info.pm10, "Coarse particles")
                PollutantRow("Ozone", info.ozone, "Ground-level ozone")
                PollutantRow("NO2", info.no2, "Nitrogen dioxide")
                PollutantRow("CO", info.co, "Carbon monoxide")
                PollutantRow("SO2", info.so2, "Sulfur dioxide")

                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun PollutantRow(name: String, value: Float?, label: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(text = name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(text = value?.toString() ?: "N/A", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun PollenTrendDialog(
    label: String, 
    dailyPollen: List<PollenDayInfo>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "$label 7-Day Trend",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(20.dp))

                dailyPollen.forEach { info ->
                    val dayName = try {
                        val date = java.time.LocalDate.parse(info.date)
                        date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US)
                    } catch (e: Exception) { 
                        ConsoleManager.logError("AlertsScreen", "Pollen date parsing error", e)
                        info.date 
                    }

                    val level = when(label) {
                        "Grass" -> info.grassLevel
                        "Ragweed" -> info.ragweedLevel
                        "Birch" -> info.birchLevel
                        "Alder" -> info.alderLevel
                        "Mugwort" -> info.mugwortLevel
                        "Olive" -> info.oliveLevel
                        else -> "N/A"
                    } ?: "N/A"

                    Row(
                        modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = dayName, modifier = Modifier.width(50.dp), fontWeight = FontWeight.Medium)
                        
                        Surface(
                            color = getPollenColor(level).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = level,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                color = getPollenColor(level),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close")
                }
            }
        }
    }
}

fun getAqiColor(label: String): Color {
    return when (label) {
        "Good" -> Color(0xFF4CAF50)
        "Moderate" -> Color(0xFFFFC107)
        "Unhealthy for Sensitive Groups" -> Color(0xFFFF9800)
        "Unhealthy" -> Color(0xFFF44336)
        "Very Unhealthy" -> Color(0xFF9C27B0)
        else -> Color(0xFF7E3120)
    }
}

fun getPollenColor(level: String): Color {
    return when (level) {
        "Low", "None" -> Color(0xFF4CAF50)
        "Moderate" -> Color(0xFFFFC107)
        "High", "Very High" -> Color(0xFFF44336)
        else -> Color.Gray
    }
}

@Composable
fun CurrentWeatherHero(
    location: String,
    weatherValues: WeatherValues?,
    nextRainTime: String?,
    isCurrentlyRaining: Boolean,
    trackWeatherEnabled: Boolean,
    onTrackToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    
    // Solid Blue Card as requested
    val cardColor = MaterialTheme.colorScheme.primary
    
    // Explicit Black/White based on theme for perfect contrast
    val textColor = if (isDark) Color.White else Color.Black
    val subTextColor = textColor.copy(alpha = 0.7f)
    val infoSurfaceColor = textColor.copy(alpha = 0.15f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        onClick = onClick,
        shape = RoundedCornerShape(32.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Animated Weather Background Layer (Matches Card Size Exactly)
            WeatherBackground(
                condition = weatherValues?.condition ?: "N/A",
                modifier = Modifier.matchParentSize()
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = location.ifBlank { "Resolving..." },
                    style = MaterialTheme.typography.headlineSmall,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                WeatherIcon(
                    condition = weatherValues?.condition ?: "N/A",
                    size = 100.dp,
                    tint = textColor
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = if (weatherValues?.temperature != null) "${weatherValues.temperature.toInt()}°" else "--°",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 80.sp
                    ),
                    color = textColor
                )
                Text(
                    text = weatherValues?.condition ?: "N/A",
                    style = MaterialTheme.typography.titleLarge,
                    color = subTextColor,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    modifier = Modifier.padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SunTime(label = "Sunrise", time = weatherValues?.sunrise, icon = Icons.Default.WbTwilight, color = textColor)
                    SunTime(label = "Sunset", time = weatherValues?.sunset, icon = Icons.Default.WbTwilight, color = textColor)
                }

                if (nextRainTime != null) {
                    val parts = nextRainTime.split("|")
                    val timeRaw = parts[0]
                    val prob = parts.getOrNull(1) ?: "0"

                    val formattedTime = try {
                        val dt = ZonedDateTime.parse(timeRaw)
                        val now = ZonedDateTime.now()
                        val isToday = dt.toLocalDate() == now.toLocalDate()
                        val isTomorrow = dt.toLocalDate() == now.plusDays(1).toLocalDate()
                        
                        val timeOnly = dt.format(DateTimeFormatter.ofPattern("h:mm a"))
                        
                        if (isToday) {
                            "today at $timeOnly"
                        } else if (isTomorrow) {
                            "tomorrow at $timeOnly"
                        } else {
                            dt.format(DateTimeFormatter.ofPattern("EEE 'at' h:mm a"))
                        }
                    } catch (e: Exception) {
                        "soon"
                    }

                    val actionText = if (isCurrentlyRaining) "to end" else "to begin"

                    Spacer(modifier = Modifier.height(24.dp))
                    Surface(
                        color = infoSurfaceColor,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WaterDrop,
                                contentDescription = null,
                                tint = if (isCurrentlyRaining) Color(0xFF03A9F4) else textColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Rain $actionText $formattedTime ($prob%)",
                                color = textColor,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                // Track Weather Toggle inside the card
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .background(infoSurfaceColor, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable(enabled = false) { } // Prevent card click through
                ) {
                    Text(
                        text = "Track Weather",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = trackWeatherEnabled,
                        onCheckedChange = { onTrackToggle(it) },
                        thumbContent = {
                            if (trackWeatherEnabled) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        },
                        modifier = Modifier.scale(0.8f),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = textColor,
                            checkedTrackColor = textColor.copy(alpha = 0.5f),
                            uncheckedThumbColor = textColor.copy(alpha = 0.5f),
                            uncheckedTrackColor = textColor.copy(alpha = 0.2f)
                        )
                    )
                }

                weatherValues?.lastUpdated?.let {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Updated: $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = subTextColor.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun SunTime(label: String, time: String?, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = color.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        Text(text = time ?: "--:--", color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(text = label, color = color.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun ForecastSection(
    title: String, 
    periods: List<ForecastPeriod>, 
    isDaily: Boolean = false,
    onItemClick: (ForecastPeriod) -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 12.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(periods) { period ->
                ForecastItem(period, isDaily, onItemClick)
            }
        }
    }
}

@Composable
fun ForecastItem(
    period: ForecastPeriod, 
    isDaily: Boolean = false,
    onItemClick: (ForecastPeriod) -> Unit
) {
    val time = try {
        if (isDaily) {
            period.name?.replace(" ", "\n") ?: "Day"
        } else {
            val dt = ZonedDateTime.parse(period.startTime)
            val now = ZonedDateTime.now()
            if (dt.toLocalDate() != now.toLocalDate()) {
                dt.format(DateTimeFormatter.ofPattern("EEE\nh a"))
            } else {
                dt.format(DateTimeFormatter.ofPattern("h a"))
            }
        }
    } catch (e: Exception) {
        ConsoleManager.logError("AlertsScreen", "Forecast item time parsing error", e)
        "Now"
    }

    Card(
        onClick = { onItemClick(period) },
        modifier = Modifier
            .width(if (isDaily) 120.dp else 100.dp)
            .height(160.dp), // Fixed height for uniform alignment
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = time, 
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
            WeatherIcon(
                condition = period.shortForecast ?: "",
                size = 32.dp,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "${period.temperature}°",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WaterDrop, contentDescription = null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.primary)
                Text(text = "${period.probabilityOfPrecipitation?.value ?: 0}%", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun AllergyStream(
    pollenData: PollenData,
    onTileClick: (String) -> Unit
) {
    Column {
        Text(
            text = "Pollen Outlook",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 12.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val latest = pollenData.dailyPollen.firstOrNull() ?: return@LazyRow
            val tiles = listOf(
                Triple("Grass", latest.grassLevel, Icons.Default.Eco),
                Triple("Ragweed", latest.ragweedLevel, Icons.Default.Grass),
                Triple("Birch", latest.birchLevel, Icons.Default.Forest),
                Triple("Alder", latest.alderLevel ?: "N/A", Icons.Default.Park),
                Triple("Mugwort", latest.mugwortLevel ?: "N/A", Icons.Default.Yard),
                Triple("Olive", latest.oliveLevel ?: "N/A", Icons.Default.FilterVintage)
            ).filter { it.second != "N/A" && it.second != "None" } // Only show active allergens

            items(tiles) { (label, level, icon) ->
                PollenTile(label = label, level = level, icon = icon, onClick = { onTileClick(label) })
            }
        }
    }
}

@Composable
fun PollenTile(
    label: String, 
    level: String, 
    icon: ImageVector,
    onClick: () -> Unit
) {
    val color = when (level) {
        "Low", "None" -> Color(0xFF4CAF50)
        "Moderate" -> Color(0xFFFFC107)
        "High", "Very High" -> Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(120.dp)
            .height(160.dp), // Uniform height across all Sentinel tiles
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = color.copy(alpha = 0.8f), modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = level,
                style = MaterialTheme.typography.titleSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AqiOutlookSection(
    aqiForecast: List<AqiDayInfo>,
    onTileClick: (AqiDayInfo) -> Unit
) {
    Column {
        Text(
            text = "5-Day AQI Outlook",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 12.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(aqiForecast) { info ->
                AqiTile(info, onTileClick)
            }
        }
    }
}

@Composable
fun AqiTile(
    info: AqiDayInfo,
    onTileClick: (AqiDayInfo) -> Unit
) {
    val color = getAqiColor(info.label)

    val dayName = try {
        val date = java.time.LocalDate.parse(info.date)
        if (date == java.time.LocalDate.now()) "Today" 
        else date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US)
    } catch (e: Exception) {
        info.date
    }

    Card(
        onClick = { onTileClick(info) },
        modifier = Modifier
            .width(120.dp)
            .height(160.dp), // Fixed height for uniform alignment
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = dayName, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = color.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = info.maxAqi.toString(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = info.label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = 12.sp
            )
        }
    }
}

@Composable
fun WeatherIcon(
    condition: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    tint: Color? = null
) {
    val iconData = when {
        condition.contains("Thunderstorm", true) -> Icons.Default.Thunderstorm to Color(0xFFFFB300) // Yellow-Orange Lightning
        condition.contains("Rain", true) || condition.contains("Showers", true) -> Icons.Default.Thunderstorm to Color(0xFF03A9F4) // Blue Rain
        condition.contains("Sun", true) || condition.contains("Clear", true) -> Icons.Default.WbSunny to Color(0xFFFFD600) // Golden Sun
        condition.contains("Snow", true) -> Icons.Default.AcUnit to Color(0xFFB3E5FC) // Cyan/White Snow
        condition.contains("Cloudy", true) || condition.contains("Clouds", true) -> Icons.Default.Cloud to Color.White
        else -> Icons.Default.CloudQueue to Color.White
    }
    
    Icon(
        imageVector = iconData.first,
        contentDescription = null,
        modifier = modifier.size(size),
        tint = tint ?: iconData.second
    )
}

@Composable
fun AlertDetailDialog(alert: WeatherEvent, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = alert.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                val severityColor = getSeverityColor(alert.severity)
                Surface(
                    color = severityColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    Text(
                        text = alert.severity,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = severityColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))

                Box(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        val displayDesc = alert.description?.trim()
                        if (!displayDesc.isNullOrBlank()) {
                            Text(
                                text = displayDesc,
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 24.sp
                            )
                        } else {
                            Text(
                                text = "No additional details provided by source.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        
                        alert.instruction?.let {
                            if (it.isNotBlank()) {
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "Action Required:",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 24.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Text("Close Details")
                }
            }
        }
    }
}

@Composable
fun AlertItem(
    alert: WeatherEvent,
    onClick: () -> Unit
) {
    val severityColor = getSeverityColor(alert.severity)
    val isLocal = alert.id.contains("infra", true)

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = severityColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = if (isLocal) Icons.Default.Info else Icons.Default.Warning,
                    contentDescription = null,
                    tint = severityColor,
                    modifier = Modifier.padding(8.dp).size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = alert.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (isLocal) "Local Source" else "NWS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = alert.severity.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = severityColor,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
fun EmptyAlertsView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircleOutline,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No active hazards or local updates",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            textAlign = TextAlign.Center
        )
    }
}

fun getSeverityColor(severity: String): Color {
    return when (severity.lowercase()) {
        "extreme", "severe" -> Color(0xFFD32F2F) // Professional Red
        "moderate" -> Color(0xFFF57C00) // Professional Orange
        "information", "minor", "advisory" -> Color(0xFF1976D2) // Professional Blue
        else -> Color(0xFF757575) // Gray for unknown
    }
}

@Composable
fun getDynamicWeatherColors(condition: String): List<Color> {
    return listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)
}
