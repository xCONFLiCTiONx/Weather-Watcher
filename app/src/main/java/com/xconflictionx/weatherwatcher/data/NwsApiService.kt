package com.xconflictionx.weatherwatcher.data

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface NwsApiService {

    @GET("points/{lat},{lon}")
    suspend fun getPointData(
        @Path("lat") lat: String,
        @Path("lon") lon: String,
        @Header("User-Agent") userAgent: String = USER_AGENT
    ): NwsPointResponse

    @GET("alerts/active")
    suspend fun getActiveAlerts(
        @Query("point") point: String,
        @Header("User-Agent") userAgent: String = USER_AGENT
    ): NwsAlertResponse

    @GET("alerts/types")
    suspend fun getAlertTypes(
        @Header("User-Agent") userAgent: String = USER_AGENT
    ): NwsAlertTypesResponse

    @GET
    suspend fun getHourlyForecast(
        @Url url: String,
        @Header("User-Agent") userAgent: String = USER_AGENT
    ): NwsForecastResponse

    @GET
    suspend fun getDailyForecast(
        @Url url: String,
        @Header("User-Agent") userAgent: String = USER_AGENT
    ): NwsForecastResponse

    @GET
    suspend fun getStations(
        @Url url: String,
        @Header("User-Agent") userAgent: String = USER_AGENT
    ): NwsStationsResponse

    @GET
    suspend fun getLatestObservations(
        @Url url: String,
        @Header("User-Agent") userAgent: String = USER_AGENT
    ): NwsObservationResponse

    companion object {
        const val BASE_URL = "https://api.weather.gov/"
        const val USER_AGENT = "WeatherWatcher/1.1 (support@xconflictionx.cc)"
    }
}
