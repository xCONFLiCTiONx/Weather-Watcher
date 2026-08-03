package com.xconflictionx.weatherwatcher.ui

import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: WeatherViewModel,
    onNavigateBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val location by viewModel.location.collectAsState()
    val appTheme by viewModel.appTheme.collectAsState()
    val appUnits by viewModel.appUnits.collectAsState()
    val selectedAlerts by viewModel.selectedAlerts.collectAsState()
    val allHazardTypes by viewModel.allHazardTypes.collectAsState()
    val dailyReportEnabled by viewModel.dailyReportEnabled.collectAsState()
    val dailyReportTime by viewModel.dailyReportTime.collectAsState()
    val rainAlertEnabled by viewModel.rainAlertEnabled.collectAsState()
    val trackWeatherEnabled by viewModel.trackWeatherEnabled.collectAsState()
    val regionalSafetyEnabled by viewModel.regionalSafetyEnabled.collectAsState()
    val infrastructureAlertsEnabled by viewModel.infrastructureAlertsEnabled.collectAsState()
    val localFeeds by viewModel.localFeeds.collectAsState()
    val isIgnoringBatteryOptimizations by viewModel.isIgnoringBatteryOptimizations.collectAsState()
    val consoleLogs by viewModel.consoleLogs.collectAsState()
    val serviceStatuses by viewModel.serviceStatuses.collectAsState()

    var selectedHazardInfo by remember { mutableStateOf<String?>(null) }
    var locationInput by remember { mutableStateOf(location) }
    LaunchedEffect(location) { if (location.isNotBlank() && locationInput.isBlank()) locationInput = location }

    var notificationPermissionGranted by remember { mutableStateOf(true) }
    var backgroundLocationGranted by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var consoleExpanded by remember { mutableStateOf(false) }

    fun checkPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            backgroundLocationGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    LaunchedEffect(Unit) {
        checkPermissions()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.updateBatteryOptimizationStatus()
                checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        checkPermissions()
        if (permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            viewModel.detectCurrentLocation()
        }
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        backgroundLocationGranted = isGranted
        if (isGranted) {
            viewModel.detectCurrentLocation()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
            contentPadding = PaddingValues(vertical = 20.dp)
        ) {
            // 1. System Permissions
            val showPermissions = !isIgnoringBatteryOptimizations || !backgroundLocationGranted || !notificationPermissionGranted
            if (showPermissions) {
                item {
                    SettingsSection(title = "System Permissions", icon = Icons.Default.AdminPanelSettings) {
                        PermissionCards(
                            isIgnoringBatteryOptimizations,
                            backgroundLocationGranted,
                            notificationPermissionGranted,
                            onBatteryClick = { viewModel.openBatteryOptimizationSettings(context) },
                            onLocationClick = {
                                if (!backgroundLocationGranted && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                    backgroundLocationLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                }
                            }
                        )
                    }
                }
                item { HorizontalDivider() }
            }

            // 2. Service Status
            item {
                SettingsSection(title = "Service Status", icon = Icons.Default.CloudSync) {
                    ServiceStatusDashboard(
                        statuses = serviceStatuses,
                        infraEnabled = infrastructureAlertsEnabled,
                        regionalEnabled = regionalSafetyEnabled
                    )
                }
            }

            item { HorizontalDivider() }

            // 3. Appearance
            item {
                SettingsSection(title = "Appearance", icon = Icons.Default.Palette) {
                    ThemeSelection(appTheme) { viewModel.updateTheme(it) }
                }
            }

            item { HorizontalDivider() }

            // 4. Units
            item {
                SettingsSection(title = "Measurement Units", icon = Icons.Default.Straighten) {
                    UnitSelection(appUnits) { viewModel.updateUnits(it) }
                }
            }

            item { HorizontalDivider() }

            // 5. Location
            item {
                SettingsSection(title = "Location (US Only)", icon = Icons.Default.Map) {
                    LocationInputRow(
                        locationInput,
                        onValueChange = { locationInput = it },
                        onDetectClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        onSaveClick = { viewModel.saveSettings(locationInput) }
                    )
                }
            }

            item { HorizontalDivider() }

            // 6. Briefing & Notifications
            item {
                SettingsSection(title = "Briefing & Notifications", icon = Icons.Default.EventNote) {
                    NotificationToggles(
                        dailyReportEnabled,
                        dailyReportTime,
                        trackWeatherEnabled,
                        rainAlertEnabled,
                        onDailyReportToggle = { viewModel.updateDailyReportEnabled(it) },
                        onDailyReportTimeClick = { h, m -> 
                            TimePickerDialog(context, { _, h2, m2 -> viewModel.updateDailyReportTime(h2, m2) }, h, m, false).show()
                        },
                        onTrackToggle = { viewModel.updateTrackWeatherEnabled(it) },
                        onRainToggle = { viewModel.updateRainAlertEnabled(it) }
                    )
                }
            }

            item { HorizontalDivider() }

            // 7. Local Alerts
            item {
                SettingsSection(title = "Local Alerts & Safety", icon = Icons.Default.GppGood) {
                    LocalSafetyToggles(
                        regionalSafetyEnabled,
                        infrastructureAlertsEnabled,
                        localFeeds,
                        onRegionalToggle = { viewModel.updateRegionalSafetyEnabled(it) },
                        onInfraToggle = { viewModel.updateInfrastructureAlertsEnabled(it) }
                    )
                }
            }

            item { HorizontalDivider() }

            // 8. Hazard Selection (ULTRA OPTIMIZED)
            item {
                Text(
                    text = "Hazard Selection",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            item {
                HazardSelectionHeader(
                    searchQuery,
                    onSearchChange = { searchQuery = it },
                    onSelectAll = { viewModel.selectAllAlerts() },
                    onClearAll = { viewModel.clearAllAlerts() }
                )
            }

            val filteredHazards = allHazardTypes
                .filter { it.contains(searchQuery, ignoreCase = true) }
                .sorted()
            
            items(filteredHazards.chunked(2)) { rowItems ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    rowItems.forEach { hazardId ->
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isChecked = !selectedAlerts.contains("__NONE__") && (selectedAlerts.isEmpty() || selectedAlerts.contains(hazardId))
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { viewModel.toggleAlert(hazardId) }
                            )
                            Text(
                                text = hazardId, 
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedHazardInfo = hazardId }
                            )
                        }
                    }
                    if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
            
            if (allHazardTypes.isEmpty()) {
                item {
                    Text(
                        text = "Loading hazard types...",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            item { HorizontalDivider() }

            // 9. System Console
            item {
                SettingsSection(title = "System Console", icon = Icons.Default.Terminal) {
                    ConsoleCard(
                        consoleExpanded,
                        consoleLogs,
                        onToggleExpand = { consoleExpanded = !consoleExpanded },
                        onClearLogs = { com.xconflictionx.weatherwatcher.util.ConsoleManager.clear() }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("NWS Service Active", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            
            item { Spacer(modifier = Modifier.height(40.dp)) }
        }

        if (selectedHazardInfo != null) {
            HazardDetailDialog(selectedHazardInfo!!, selectedAlerts, onToggle = { viewModel.toggleAlert(it) }, onDismiss = { selectedHazardInfo = null })
        }
    }
}

@Composable
fun PermissionCards(
    isIgnoringBattery: Boolean,
    bgLocationGranted: Boolean,
    notificationGranted: Boolean,
    onBatteryClick: () -> Unit,
    onLocationClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!isIgnoringBattery) {
            OutlinedCard(
                onClick = onBatteryClick,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                )
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.BatteryAlert, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = "Battery: Optimized (Restricted)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        Text(text = "Phone may sleep app. Tap to change to 'Don't Optimize'.", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (!bgLocationGranted) {
            OutlinedCard(
                onClick = onLocationClick,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                )
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.GpsOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = "Location: While using only", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        Text(text = "Tap to change to 'Allow all the time' for 24/7 monitoring.", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (!notificationGranted) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                )
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Notifications are disabled. Enable in system settings.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun ServiceStatusDashboard(
    statuses: Map<String, Boolean>, 
    infraEnabled: Boolean, 
    regionalEnabled: Boolean
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val services = listOf(
                "NWS Forecasts",
                "NWS Severe Alerts",
                "Regional Safety",
                "Open-Meteo (AQI/Sun)",
                "Community Infrastructure"
            )
            
            services.forEach { service ->
                val isEnabled = when (service) {
                    "Community Infrastructure" -> infraEnabled
                    "Regional Safety" -> regionalEnabled
                    else -> true
                }
                
                val isConnected = statuses[service] ?: true
                
                val statusLabel = when {
                    !isEnabled -> "Disabled"
                    isConnected -> "Connected"
                    else -> "Failed"
                }
                
                val statusColor = when {
                    !isEnabled -> Color.Gray
                    isConnected -> Color(0xFF4CAF50)
                    else -> Color(0xFFF44336)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = service, style = MaterialTheme.typography.bodyMedium)
                    Surface(color = statusColor.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(statusColor, androidx.compose.foundation.shape.CircleShape))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeSelection(current: String, onSelect: (String) -> Unit) {
    val themeOptions = listOf("system" to "System Default", "light" to "Standard (Light)", "dark" to "Dark Mode")
    Column(Modifier.selectableGroup()) {
        themeOptions.forEach { (id, label) ->
            Row(Modifier.fillMaxWidth().height(48.dp).selectable(selected = (current == id), onClick = { onSelect(id) }, role = Role.RadioButton).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = (current == id), onClick = null)
                Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
            }
        }
    }
}

@Composable
fun UnitSelection(current: String, onSelect: (String) -> Unit) {
    val unitOptions = listOf("imperial" to "Fahrenheit (°F)", "metric" to "Celsius (°C)")
    Column(Modifier.selectableGroup()) {
        unitOptions.forEach { (id, label) ->
            Row(Modifier.fillMaxWidth().height(48.dp).selectable(selected = (current == id), onClick = { onSelect(id) }, role = Role.RadioButton).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = (current == id), onClick = null)
                Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
            }
        }
    }
}

@Composable
fun LocationInputRow(input: String, onValueChange: (String) -> Unit, onDetectClick: () -> Unit, onSaveClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = input, onValueChange = onValueChange, label = { Text("City or Zip Code") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp))
            IconButton(onClick = onDetectClick, colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Icon(Icons.Default.LocationOn, contentDescription = "Detect")
            }
        }
        Button(onClick = onSaveClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("Save & Update Weather") }
    }
}

@Composable
fun NotificationToggles(dailyEnabled: Boolean, dailyTime: Pair<Int, Int>, trackEnabled: Boolean, rainEnabled: Boolean, onDailyReportToggle: (Boolean) -> Unit, onDailyReportTimeClick: (Int, Int) -> Unit, onTrackToggle: (Boolean) -> Unit, onRainToggle: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Daily Summary", style = MaterialTheme.typography.bodyLarge)
                Text("Get current weather, AQI, and alerts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = dailyEnabled, onCheckedChange = onDailyReportToggle)
        }
        if (dailyEnabled) {
            val (h, m) = dailyTime
            OutlinedCard(onClick = { onDailyReportTimeClick(h, m) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), shape = RoundedCornerShape(12.dp)) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Reporting Time", style = MaterialTheme.typography.bodyMedium)
                    Text(text = String.format(java.util.Locale.US, "%02d:%02d %s", if (h == 0 || h == 12) 12 else h % 12, m, if (h < 12) "AM" else "PM"), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) { Text("Track", style = MaterialTheme.typography.bodyLarge); Text("Notifications on weather changes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Switch(checked = trackEnabled, onCheckedChange = onTrackToggle)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Rain Alerts", style = MaterialTheme.typography.bodyLarge)
            Switch(checked = rainEnabled, onCheckedChange = onRainToggle)
        }
    }
}

@Composable
fun LocalSafetyToggles(regionalEnabled: Boolean, infraEnabled: Boolean, feeds: List<com.xconflictionx.weatherwatcher.data.ArcgisItem>, onRegionalToggle: (Boolean) -> Unit, onInfraToggle: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) { Text("Regional Safety", style = MaterialTheme.typography.bodyLarge); Text("FEMA & State safety alerts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Switch(checked = regionalEnabled, onCheckedChange = onRegionalToggle)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) { Text("Community Infrastructure", style = MaterialTheme.typography.bodyLarge); Text("Local road and utility updates.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Switch(checked = infraEnabled, onCheckedChange = onInfraToggle)
        }
        if (infraEnabled && feeds.isNotEmpty()) {
            feeds.forEach { feed ->
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = feed.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun HazardSelectionHeader(query: String, onSearchChange: (String) -> Unit, onSelectAll: () -> Unit, onClearAll: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = query, onValueChange = onSearchChange, label = { Text("Search Hazards") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, singleLine = true, shape = RoundedCornerShape(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(
                onClick = onSelectAll,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
            ) { Text("Select All") }
            TextButton(
                onClick = onClearAll,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
            ) { Text("Clear All") }
        }
    }
}

@Composable
fun ConsoleCard(expanded: Boolean, logs: List<com.xconflictionx.weatherwatcher.util.LogEntry>, onToggleExpand: () -> Unit, onClearLogs: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Live Debug Logs", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row {
                    IconButton(onClick = onClearLogs) { Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", modifier = Modifier.size(20.dp)) }
                    IconButton(onClick = onToggleExpand) { Icon(imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "Toggle") }
                }
            }
            if (expanded) {
                if (logs.isEmpty()) {
                    Text("No system errors recorded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), textAlign = TextAlign.Center)
                } else {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        logs.forEach { ConsoleEntryItem(it) }
                    }
                }
            } else {
                Text(text = "${logs.size} logs recorded. Tap expand to view.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ConsoleEntryItem(entry: com.xconflictionx.weatherwatcher.util.LogEntry) {
    var detailExpanded by remember { mutableStateOf(false) }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val timeStr = java.time.Instant.ofEpochMilli(entry.timestamp).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))

    Column(modifier = Modifier.fillMaxWidth().clickable { if (entry.error != null) detailExpanded = !detailExpanded }.padding(4.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.weight(1f)) {
                Text(text = "[$timeStr]", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "${entry.tag}: ${entry.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { val fullText = "[$timeStr] ${entry.tag}: ${entry.message}\n\n${entry.error ?: ""}"; clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(fullText)) }, modifier = Modifier.size(24.dp)) {
                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        if (detailExpanded && entry.error != null) {
            Surface(modifier = Modifier.padding(top = 4.dp, start = 8.dp), color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f), shape = RoundedCornerShape(4.dp)) {
                Text(text = entry.error, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(8.dp), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, maxLines = 10, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun HazardDetailDialog(name: String, selected: Set<String>, onToggle: (String) -> Unit, onDismiss: () -> Unit) {
    val isEnabled = !selected.contains("__NONE__") && (selected.isEmpty() || selected.contains(name))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name, fontWeight = FontWeight.Bold) },
        text = { Text("Detailed information for $name alerts. You will receive real-time notifications when this hazard is active.", style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { 
            Button(
                onClick = { if (!isEnabled) onToggle(name); onDismiss() },
                colors = ButtonDefaults.buttonColors(contentColor = Color.White)
            ) { Text("Enable") } 
        },
        dismissButton = { 
            TextButton(
                onClick = { if (isEnabled) onToggle(name); onDismiss() },
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
            ) { Text("Disable") } 
        }
    )
}

@Composable
fun SettingsSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
        }
        content()
    }
}
