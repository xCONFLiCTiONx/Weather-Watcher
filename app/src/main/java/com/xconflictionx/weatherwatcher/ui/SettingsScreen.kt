package com.xconflictionx.weatherwatcher.ui

import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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

    var selectedHazardInfo by remember { mutableStateOf<String?>(null) }
    var locationInput by remember { mutableStateOf(location) }
    LaunchedEffect(location) { if (location.isNotBlank() && locationInput.isBlank()) locationInput = location }

    var notificationPermissionGranted by remember { mutableStateOf(true) }
    var backgroundLocationGranted by remember { mutableStateOf(true) }

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

    // Refresh status whenever user returns to the app
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // System Permissions Section (Top Priority)
            SettingsSection(title = "System Permissions", icon = Icons.Default.AdminPanelSettings) {
                // Battery Optimization
                OutlinedCard(
                    onClick = { viewModel.openBatteryOptimizationSettings(context) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (isIgnoringBatteryOptimizations) 
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f) 
                        else 
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isIgnoringBatteryOptimizations) Icons.Default.CheckCircle else Icons.Default.BatteryAlert,
                            contentDescription = null,
                            tint = if (isIgnoringBatteryOptimizations) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isIgnoringBatteryOptimizations) "Battery: Unrestricted" else "Battery: Optimized (Restricted)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isIgnoringBatteryOptimizations) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = if (isIgnoringBatteryOptimizations) "App will always run in background." else "Phone may sleep app. Tap to change to 'Don't Optimize'.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Location Access
                OutlinedCard(
                    onClick = {
                        if (!backgroundLocationGranted && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            backgroundLocationLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (backgroundLocationGranted) 
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f) 
                        else 
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (backgroundLocationGranted) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                            contentDescription = null,
                            tint = if (backgroundLocationGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (backgroundLocationGranted) "Location: All the time" else "Location: While using only",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (backgroundLocationGranted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = if (backgroundLocationGranted) "24/7 monitoring active." else "Tap to change to 'Allow all the time'.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!notificationPermissionGranted && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                "Notifications are disabled. Please enable in system settings.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // Appearance Section
            SettingsSection(title = "Appearance", icon = Icons.Default.Palette) {
                val themeOptions = listOf("system" to "System Default", "light" to "Standard (Light)", "dark" to "Dark Mode")
                Column(Modifier.selectableGroup()) {
                    themeOptions.forEach { (id, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .selectable(
                                    selected = (appTheme == id),
                                    onClick = { viewModel.updateTheme(id) },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (appTheme == id),
                                onClick = null
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // Units Section
            SettingsSection(title = "Measurement Units", icon = Icons.Default.Straighten) {
                val unitOptions = listOf("imperial" to "Fahrenheit (°F)", "metric" to "Celsius (°C)")
                Column(Modifier.selectableGroup()) {
                    unitOptions.forEach { (id, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .selectable(
                                    selected = (appUnits == id),
                                    onClick = { viewModel.updateUnits(id) },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (appUnits == id),
                                onClick = null
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // Location Settings Section
            SettingsSection(title = "Location (US Only)", icon = Icons.Default.Map) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = locationInput,
                        onValueChange = { locationInput = it },
                        label = { Text("City or Zip Code") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    IconButton(
                        onClick = { 
                            permissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = "Detect Location")
                    }
                }
                
                Button(
                    onClick = { viewModel.saveSettings(locationInput) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save & Update Weather")
                }
            }

            HorizontalDivider()

            // Briefing & Notifications Section
            SettingsSection(title = "Briefing & Notifications", icon = Icons.Default.EventNote) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Daily Summary Notification", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "Get current weather, AQI, and alerts at a set time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = dailyReportEnabled,
                        onCheckedChange = { viewModel.updateDailyReportEnabled(it) }
                    )
                }

                if (dailyReportEnabled) {
                    val (hour, minute) = dailyReportTime
                    val timeString = String.format(java.util.Locale.US, "%02d:%02d %s", 
                        if (hour == 0 || hour == 12) 12 else hour % 12,
                        minute,
                        if (hour < 12) "AM" else "PM"
                    )

                    OutlinedCard(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, m -> viewModel.updateDailyReportTime(h, m) },
                                hour,
                                minute,
                                false
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Reporting Time", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = timeString,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Track", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "Keep track of the weather with regular notifications.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = trackWeatherEnabled,
                        onCheckedChange = { viewModel.updateTrackWeatherEnabled(it) }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Rain Alerts", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = rainAlertEnabled,
                        onCheckedChange = { viewModel.updateRainAlertEnabled(it) }
                    )
                }
            }

            HorizontalDivider()

            // Local Alerts & Safety Section
            SettingsSection(title = "Local Alerts & Safety", icon = Icons.Default.GppGood) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Regional Public Safety", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "Life-safety alerts from FEMA & State agencies (amber alerts, evacuations).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = regionalSafetyEnabled,
                        onCheckedChange = { viewModel.updateRegionalSafetyEnabled(it) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Community Infrastructure", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "Local utility and road updates from municipal data feeds.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = infrastructureAlertsEnabled,
                        onCheckedChange = { viewModel.updateInfrastructureAlertsEnabled(it) }
                    )
                }
                
                if (infrastructureAlertsEnabled && localFeeds.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Active Local Sources:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    localFeeds.forEach { feed ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = feed.title,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // Alerts Selection Section
            SettingsSection(title = "Hazard Selection", icon = Icons.Default.NotificationsActive) {
                var searchQuery by remember { mutableStateOf("") }
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search Hazards") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { viewModel.selectAllAlerts() }) {
                        Text("Select All")
                    }
                    TextButton(onClick = { viewModel.clearAllAlerts() }) {
                        Text("Clear All")
                    }
                }

                val filteredHazards = allHazardTypes.filter { it.contains(searchQuery, ignoreCase = true) }.sorted()
                
                filteredHazards.chunked(2).forEach { rowItems ->
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
                    Text(
                        text = "Loading hazard types...",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("NWS Service Active", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }

        if (selectedHazardInfo != null) {
            val hazardName = selectedHazardInfo!!
            val isChecked = !selectedAlerts.contains("__NONE__") && (selectedAlerts.isEmpty() || selectedAlerts.contains(hazardName))
            
            val hazardDefinitions = mapOf(
                "911 Telephone Outage" to "A public safety alert issued when emergency telephone services are interrupted or unavailable in a specific area.",
                "Administrative Message" to "A non-emergency message issued by the National Weather Service or local authorities to relay important administrative information or updates.",
                "Air Quality Alert" to "An Air Quality Alert is an official notice that local air pollution (like ozone or particulate matter) has reached levels that are unhealthy for sensitive groups or the general public.",
                "Air Stagnation Advisory" to "Issued when atmospheric conditions are stable and winds are light, allowing pollutants to build up in the lower atmosphere.",
                "Ashfall Advisory" to "A minor weather alert issued by the National Weather Service when a volcano experiences a small eruption or wind carries suspended ash, expecting less than 1/4 inch of ash buildup.",
                "Ashfall Warning" to "Issued when significant volcanic ash is expected to fall, usually 1/4 inch or more, causing damage to machinery and risk to health.",
                "Avalanche Advisory" to "Provides information on avalanche conditions; indicates that slides are possible but not necessarily imminent.",
                "Avalanche Warning" to "Issued when life-threatening avalanche conditions exist. Backcountry travel is strongly discouraged in the affected areas.",
                "Avalanche Watch" to "Indicates that conditions are favorable for widespread or life-threatening avalanches within the next 24 to 48 hours.",
                "Beach Hazards Statement" to "Issued for threats such as strong rip currents, high surf, or other dangerous conditions at the shore.",
                "Blizzard Warning" to "Issued for a combination of blowing snow and sustained winds or frequent gusts of 35 mph or higher, reducing visibility to less than 1/4 mile for at least 3 hours.",
                "Blowing Dust Advisory" to "Issued when blowing dust is expected to reduce visibility to between 1/4 mile and 1 mile.",
                "Blowing Dust Warning" to "Issued when blowing dust is expected to reduce visibility to 1/4 mile or less, creating extremely hazardous travel conditions.",
                "Blue Alert" to "A public emergency system used to notify the media and the public when a law enforcement officer has been killed or seriously injured in the line of duty, or has gone missing, and the suspect remains at large, posing an imminent threat to the community.",
                "Brisk Wind Advisory" to "Issued for marine areas when winds are expected to be between 22 and 33 knots (25-38 mph).",
                "Child Abduction Emergency" to "Issued when a child has been abducted and is believed to be in imminent danger of serious bodily harm or death.",
                "Civil Danger Warning" to "Issued for a broad range of civil emergencies that pose a threat to life and property, requiring immediate public action.",
                "Civil Emergency Message" to "A message used to outline a significant emergency situation of a civil nature, requiring public awareness or specific actions.",
                "Coastal Flood Advisory" to "Issued when minor tidal flooding is expected, causing some inconvenience but no major damage to property.",
                "Coastal Flood Statement" to "Issued to provide updates or additional information on minor coastal flooding that does not meet advisory criteria.",
                "Coastal Flood Warning" to "Issued when significant coastal flooding is occurring or imminent, posing a threat to life and property.",
                "Coastal Flood Watch" to "Issued when significant coastal flooding is possible within the next 12 to 48 hours.",
                "Cold Weather Advisory" to "Issued when dangerously cold temperatures are expected, posing a risk of hypothermia or frostbite if precautions aren't taken.",
                "Dense Fog Advisory" to "Issued when widespread fog is expected to reduce visibility to 1/4 mile or less.",
                "Dense Smoke Advisory" to "Issued when widespread smoke is expected to reduce visibility to 1/4 mile or less.",
                "Dust Advisory" to "Issued when blowing dust is expected to reduce visibility to between 1/4 mile and 1 mile.",
                "Dust Storm Warning" to "Issued when strong winds lift large amounts of dust or sand, reducing visibility to 1/4 mile or less over a widespread area.",
                "Earthquake Warning" to "Issued when a significant earthquake has been detected and shaking is imminent in the specified area.",
                "Evacuation Immediate" to "An urgent notice from local authorities that life-threatening conditions require immediate evacuation from the specified area.",
                "Extreme Cold Warning" to "Issued when dangerously low temperatures or wind chills are expected, which can cause frostbite in minutes.",
                "Extreme Cold Watch" to "Issued when dangerously low temperatures or wind chills are possible in the next 12 to 48 hours.",
                "Extreme Fire Danger" to "Issued when fuel conditions and weather (high heat, low humidity, high winds) create a high potential for rapid and uncontrollable wildfire spread.",
                "Extreme Heat Warning" to "Issued when dangerously high temperatures and humidity are occurring or expected.",
                "Extreme Heat Watch" to "Issued when dangerously high temperatures and humidity are possible in the next 24 to 72 hours.",
                "Extreme Wind Warning" to "Issued when sustained winds of 115 mph or higher are expected within an hour, usually associated with the eye wall of a major hurricane.",
                "Fire Warning" to "Issued when a fire is occurring that poses an immediate threat to life and property in the specified area.",
                "Fire Weather Watch" to "Issued when critical fire weather conditions are possible in the next 12 to 72 hours.",
                "Flash Flood Statement" to "Issued to provide updates or follow-up information for an active Flash Flood Warning.",
                "Flash Flood Warning" to "Issued when a flash flood is imminent or occurring. Seek higher ground immediately.",
                "Flash Flood Watch" to "Issued when conditions are favorable for flash flooding in the next 12 to 48 hours.",
                "Flood Advisory" to "Issued when flooding is expected but is not severe enough for a warning. It causes significant inconvenience.",
                "Flood Statement" to "Provides updates or additional information on an active Flood Warning or Advisory.",
                "Flood Warning" to "Issued when flooding is occurring or imminent. Take action to protect life and property.",
                "Flood Watch" to "Issued when conditions are favorable for flooding in the next 12 to 48 hours.",
                "Freeze Warning" to "Issued when widespread freezing temperatures (32°F or below) are expected while the growing season is active.",
                "Freeze Watch" to "Issued when widespread freezing temperatures are possible in the next 24 to 48 hours.",
                "Freezing Fog Advisory" to "Issued when fog is expected to form a thin layer of ice on surfaces, making travel dangerous.",
                "Freezing Spray Advisory" to "Issued for marine areas when freezing sea spray is expected to cause ice accumulation on vessels.",
                "Frost Advisory" to "Issued when widespread frost is expected, which can damage or kill sensitive vegetation.",
                "Gale Warning" to "Issued for marine areas when sustained winds of 34 to 47 knots (39-54 mph) are expected.",
                "Gale Watch" to "Issued for marine areas when gale-force winds are possible in the next 12 to 48 hours.",
                "Hazardous Materials Warning" to "Issued for a significant spill or release of hazardous materials that poses a threat to public health and safety.",
                "Hazardous Seas Warning" to "Issued for marine areas when waves and swell conditions are extremely dangerous to all vessels.",
                "Hazardous Seas Watch" to "Issued when extremely dangerous sea conditions are possible in the next 12 to 48 hours.",
                "Hazardous Weather Outlook" to "A daily report issued to inform the public of potentially significant weather events expected over the next 7 days.",
                "Heat Advisory" to "Issued when high temperatures and humidity are expected to create a risk of heat-related illness.",
                "Heavy Freezing Spray Warning" to "Issued for marine areas when rapid ice accumulation from sea spray is expected on vessels.",
                "Heavy Freezing Spray Watch" to "Issued when heavy freezing spray conditions are possible in the next 12 to 48 hours.",
                "High Surf Advisory" to "Issued when breaking wave heights at the shore are expected to be high enough to pose a danger to swimmers and property.",
                "High Surf Warning" to "Issued when dangerously high breaking waves are occurring or imminent at the shore.",
                "High Wind Warning" to "Issued when sustained winds of 40 mph+ or gusts of 58 mph+ are expected for at least an hour.",
                "High Wind Watch" to "Issued when high wind conditions are possible in the next 12 to 48 hours.",
                "Hurricane Force Wind Warning" to "Issued for marine areas when sustained winds of 64 knots (74 mph) or higher are expected.",
                "Hurricane Force Wind Watch" to "Issued when hurricane-force winds are possible in the next 12 to 48 hours.",
                "Hurricane Warning" to "Issued when sustained winds of 74 mph or higher are expected in the area within 36 hours.",
                "Hurricane Watch" to "Issued when sustained winds of 74 mph or higher are possible in the area within 48 hours.",
                "Hydrologic Outlook" to "Issued to provide information on the potential for significant flooding or long-term water resource issues.",
                "Ice Storm Warning" to "Issued when significant accumulations of freezing rain are expected, typically 1/4 inch or more.",
                "Lake Effect Snow Warning" to "Issued when heavy snow caused by cold air moving over warm lake water is expected to produce significant accumulation.",
                "Lake Wind Advisory" to "Issued when winds over a lake are expected to reach speeds that are dangerous for small boats.",
                "Lakeshore Flood Advisory" to "Issued when minor flooding along a lake shore is expected due to high water levels or strong winds.",
                "Lakeshore Flood Statement" to "Provides updates or additional information on minor lakeshore flooding.",
                "Lakeshore Flood Warning" to "Issued when significant flooding along a lake shore is occurring or imminent.",
                "Lakeshore Flood Watch" to "Issued when significant lakeshore flooding is possible in the next 12 to 48 hours.",
                "Law Enforcement Warning" to "Issued for situations involving law enforcement that require immediate public action or awareness for safety.",
                "Local Area Emergency" to "A general emergency alert for a specific local area, requiring immediate public attention.",
                "Low Water Advisory" to "Issued for marine areas when water levels are significantly below normal, creating a hazard for navigation.",
                "Marine Weather Statement" to "Issued to provide information on significant weather events occurring over marine waters.",
                "Nuclear Power Plant Warning" to "Issued for a significant accident or emergency at a nuclear power facility that poses a threat to the public.",
                "Radiological Hazard Warning" to "Issued for a significant release of radioactive materials that poses a threat to public health.",
                "Red Flag Warning" to "Issued when critical fire conditions—high winds, low humidity, and dry fuels—are occurring or imminent.",
                "Rip Current Statement" to "Issued to provide information on the risk of strong rip currents at the beach.",
                "Severe Thunderstorm Warning" to "Issued when a storm with winds of 58 mph+ or 1-inch hail is occurring or imminent.",
                "Severe Thunderstorm Watch" to "Issued when conditions are favorable for the development of severe thunderstorms.",
                "Severe Weather Statement" to "Provides updates or follow-up information for active severe weather warnings.",
                "Shelter In Place Warning" to "Issued when life-threatening conditions (like a chemical leak) require the public to stay indoors and seal their surroundings.",
                "Short Term Forecast" to "A localized forecast for the next few hours, highlighting specific weather trends.",
                "Small Craft Advisory" to "Issued for marine areas when wind or wave conditions are dangerous for small vessels.",
                "Snow Squall Warning" to "Issued for brief but intense bursts of heavy snow and strong winds causing whiteout conditions and dangerous travel.",
                "Special Marine Warning" to "Issued for short-duration hazardous weather (waterspouts, gale winds) over water.",
                "Special Weather Statement" to "Used to provide information about hazardous weather that doesn't yet meet strict Warning/Advisory criteria.",
                "Storm Surge Warning" to "Issued when life-threatening inundation from rising water moving inland from the shoreline is expected within 36 hours.",
                "Storm Surge Watch" to "Issued when life-threatening inundation from rising water is possible within 48 hours.",
                "Storm Warning" to "Issued for marine areas when sustained winds of 48 to 63 knots (55-73 mph) are expected.",
                "Storm Watch" to "Issued for marine areas when storm-force winds are possible in the next 12 to 48 hours.",
                "Test" to "A message issued to test the alert system and ensure it is functioning correctly.",
                "Tornado Warning" to "Issued when a tornado has been sighted or indicated by radar. Take shelter immediately.",
                "Tornado Watch" to "Issued when conditions are favorable for tornadoes to develop.",
                "Tropical Cyclone Local Statement" to "Provides detailed information on the expected local impacts of a tropical cyclone.",
                "Tropical Storm Warning" to "Issued when sustained winds of 39 to 73 mph are expected within 36 hours.",
                "Tropical Storm Watch" to "Issued when sustained winds of 39 to 73 mph are possible within 48 hours.",
                "Tsunami Advisory" to "Issued when a tsunami with the potential to generate strong currents or dangerous waves is expected.",
                "Tsunami Warning" to "Issued when a potentially destructive tsunami has been generated and is approaching the coast.",
                "Tsunami Watch" to "Issued to alert areas that a tsunami may have been generated and could impact them later.",
                "Typhoon Warning" to "Issued in the Western Pacific when sustained winds of 74 mph or higher are expected within 24 hours.",
                "Typhoon Watch" to "Issued when typhoon conditions are possible within 48 hours.",
                "Volcano Warning" to "Issued when a volcanic eruption is occurring or imminent, posing a threat to life and property.",
                "Wind Advisory" to "Issued when sustained winds of 31-39 mph or gusts of 46-57 mph are expected for at least an hour.",
                "Winter Storm Warning" to "Issued when significant and hazardous winter weather (heavy snow, ice, or both) is occurring or imminent.",
                "Winter Storm Watch" to "Issued when significant winter weather is possible in the next 12 to 48 hours.",
                "Winter Weather Advisory" to "Issued when a combination of snow, sleet, or freezing rain is expected to cause significant inconveniences."
            )
            
            AlertDialog(
                onDismissRequest = { selectedHazardInfo = null },
                title = { Text(hazardName, fontWeight = FontWeight.Bold) },
                text = {
                    val description = hazardDefinitions.entries.firstOrNull { (k, _) -> 
                        hazardName.equals(k, ignoreCase = true) || hazardName.contains(k, ignoreCase = true)
                    }?.value ?: when {
                        hazardName.contains("Warning", ignoreCase = true) -> 
                            "An official NWS 'Warning' means a hazardous weather event is occurring, imminent, or highly likely. It is a formal call to take immediate action to protect life and property."
                        hazardName.contains("Watch", ignoreCase = true) -> 
                            "An official NWS 'Watch' means that the risk of a hazardous weather event has increased significantly, but its exact timing or location is still uncertain. It is a notice to be prepared."
                        hazardName.contains("Advisory", ignoreCase = true) -> 
                            "An 'Advisory' is for weather conditions that are not life-threatening on their own but could become dangerous if caution is not used. They cause significant inconvenience."
                        else -> "This is an official National Weather Service alert category. Enabling this ensures you receive real-time notifications the moment this specific alert is issued for your area."
                    }
                    Text(description, style = MaterialTheme.typography.bodyMedium)
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (!isChecked) viewModel.toggleAlert(hazardName)
                            selectedHazardInfo = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Enable")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            if (isChecked) viewModel.toggleAlert(hazardName)
                            selectedHazardInfo = null
                        }
                    ) {
                        Text("Disable", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    }
}

@Composable
fun SettingsSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold
            )
        }
        content()
    }
}
