package com.xconflictionx.weatherwatcher.data

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface ArcgisApiService {

    @GET("sharing/rest/search")
    suspend fun searchPortal(
        @Query("q") query: String,
        @Query("bbox") bbox: String? = null,
        @Query("f") format: String = "json",
        @Query("num") num: Int = 10
    ): ArcgisSearchResponse

    @GET
    suspend fun queryFeatureService(
        @Url url: String,
        @Query("where") where: String = "1=1",
        @Query("geometry") geometry: String? = null,
        @Query("geometryType") geometryType: String? = null,
        @Query("spatialRel") spatialRel: String? = null,
        @Query("inSR") inSR: String? = null,
        @Query("outFields") outFields: String = "*",
        @Query("f") format: String = "json"
    ): ArcgisFeatureResponse

    companion object {
        const val BASE_URL = "https://www.arcgis.com/"
    }
}

@Serializable
data class ArcgisSearchResponse(
    val results: List<ArcgisItem> = emptyList()
)

@Serializable
data class ArcgisItem(
    val id: String,
    val title: String,
    val url: String? = null,
    val type: String? = null,
    val tags: List<String> = emptyList()
)

@Serializable
data class ArcgisFeatureResponse(
    val features: List<ArcgisFeature> = emptyList()
)

@Serializable
data class ArcgisFeature(
    val attributes: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap()
)
