package com.xconflictionx.weatherwatcher.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- NWS Point Response ---
@Serializable
data class NwsPointResponse(
    val properties: NwsPointProperties
)

@Serializable
data class NwsPointProperties(
    val forecastHourly: String,
    val observationStations: String,
    val forecast: String
)

// --- NWS Stations Response ---
@Serializable
data class NwsStationsResponse(
    val features: List<NwsStationFeature> = emptyList()
)

@Serializable
data class NwsStationFeature(
    val id: String,
    val properties: NwsStationProperties
)

@Serializable
data class NwsStationProperties(
    val stationIdentifier: String,
    val name: String? = null
)

// --- NWS Alert Response ---
@Serializable
data class NwsAlertResponse(
    val features: List<NwsAlertFeature> = emptyList()
)

@Serializable
data class NwsAlertFeature(
    val properties: NwsAlertProperties
)

@Serializable
data class NwsAlertProperties(
    val id: String? = null,
    val event: String? = null,
    val severity: String? = null,
    val description: String? = null,
    val instruction: String? = null,
    val headline: String? = null
)

// --- NWS Forecast Response ---
@Serializable
data class NwsForecastResponse(
    val properties: NwsForecastProperties
)

@Serializable
data class NwsForecastProperties(
    val periods: List<ForecastPeriod> = emptyList()
)

@Serializable
data class ForecastPeriod(
    val number: Int,
    val name: String? = null,
    val startTime: String,
    val endTime: String,
    val temperature: Int? = null,
    val temperatureUnit: String? = null,
    val probabilityOfPrecipitation: NwsValue? = null,
    val dewpoint: NwsMeasurement? = null,
    val relativeHumidity: NwsValue? = null,
    val windSpeed: String? = null,
    val windDirection: String? = null,
    val shortForecast: String? = null,
    val detailedForecast: String? = null
)

@Serializable
data class NwsValue(
    val value: Int? = null
)

// --- NWS Observation Response ---
@Serializable
data class NwsObservationResponse(
    val properties: NwsObservationProperties
)

@Serializable
data class NwsObservationProperties(
    val temperature: NwsMeasurement? = null,
    val relativeHumidity: NwsMeasurement? = null,
    val windSpeed: NwsMeasurement? = null,
    val textDescription: String? = null
)

@Serializable
data class NwsMeasurement(
    val value: Float? = null,
    val unitCode: String? = null
)

// --- NWS Alert Types ---
@Serializable
data class NwsAlertTypesResponse(
    val eventTypes: List<String> = emptyList()
)

// --- Open-Meteo Pollen Models ---
@Serializable
data class OpenMeteoPollenResponse(
    val daily: PollenDaily? = null
)

@Serializable
data class PollenDaily(
    val time: List<String> = emptyList(),
    @SerialName("alder_pollen") val alder: List<Float?> = emptyList(),
    @SerialName("birch_pollen") val birch: List<Float?> = emptyList(),
    @SerialName("grass_pollen") val grass: List<Float?> = emptyList(),
    @SerialName("mugwort_pollen") val mugwort: List<Float?> = emptyList(),
    @SerialName("olive_pollen") val olive: List<Float?> = emptyList(),
    @SerialName("ragweed_pollen") val ragweed: List<Float?> = emptyList()
)

// --- Open-Meteo Environment Models ---
@Serializable
data class OpenMeteoEnvResponse(
    val daily: EnvDaily? = null,
    val hourly: EnvHourly? = null
)

@Serializable
data class EnvDaily(
    val time: List<String> = emptyList(),
    val sunrise: List<String> = emptyList(),
    val sunset: List<String> = emptyList()
)

@Serializable
data class EnvHourly(
    val time: List<String> = emptyList(),
    @SerialName("us_aqi") val aqi: List<Int?> = emptyList(),
    @SerialName("pm10") val pm10: List<Float?> = emptyList(),
    @SerialName("pm2_5") val pm2_5: List<Float?> = emptyList(),
    @SerialName("carbon_monoxide") val co: List<Float?> = emptyList(),
    @SerialName("nitrogen_dioxide") val no2: List<Float?> = emptyList(),
    @SerialName("sulphur_dioxide") val so2: List<Float?> = emptyList(),
    @SerialName("ozone") val ozone: List<Float?> = emptyList(),
    @SerialName("precipitation_probability") val precipitationProbability: List<Int?> = emptyList()
)

@Serializable
data class WeatherEvent(
    val id: String,
    val title: String,
    val severity: String,
    val description: String?,
    val instruction: String?,
    val source: String = "NWS",
    val hash: Int = 0
)

@Serializable
data class WeatherValues(
    val temperature: Float,
    val condition: String,
    val icon: String? = null,
    val lastUpdated: String? = null,
    val humidity: Float? = null,
    val windSpeed: Float? = null,
    val rainProbability: Int? = null,
    val sunrise: String? = null,
    val sunset: String? = null,
    val aqi: Int? = null,
    val aqiForecast: List<AqiDayInfo> = emptyList()
)

@Serializable
data class AqiDayInfo(
    val date: String,
    val maxAqi: Int,
    val label: String,
    val pm2_5: Float? = null,
    val pm10: Float? = null,
    val ozone: Float? = null,
    val no2: Float? = null,
    val co: Float? = null,
    val so2: Float? = null
)

@Serializable
data class PollenData(
    val dailyPollen: List<PollenDayInfo> = emptyList(),
    val isAvailable: Boolean = false
)

@Serializable
data class PollenDayInfo(
    val date: String,
    val grassLevel: String,
    val ragweedLevel: String,
    val birchLevel: String,
    val alderLevel: String? = "N/A",
    val mugwortLevel: String? = "N/A",
    val oliveLevel: String? = "N/A"
)
