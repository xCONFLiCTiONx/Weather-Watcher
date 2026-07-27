package com.xconflictionx.weatherwatcher.data

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApiService {
    @GET("v1/air-quality")
    suspend fun getPollen(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("daily") pollen: String = "alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen",
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoPollenResponse

    @GET("https://api.open-meteo.com/v1/forecast")
    suspend fun getSunTimes(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("daily") daily: String = "sunrise,sunset",
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoEnvResponse

    @GET("v1/air-quality")
    suspend fun getAqi(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("hourly") hourly: String = "us_aqi,pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone",
        @Query("forecast_days") days: Int = 7,
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoEnvResponse

    companion object {
        const val BASE_URL = "https://air-quality-api.open-meteo.com/"
    }
}
