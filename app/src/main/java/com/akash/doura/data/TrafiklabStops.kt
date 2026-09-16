package com.akash.doura.data

import com.akash.doura.BuildConfig
import com.akash.doura.TransportType
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.Locale
import java.util.concurrent.TimeUnit

/* ----------------------------------------------------------------------------
 * Trafiklab Stop Lookup
 *
 *   GET https://realtime-api.trafiklab.se/v1/stops/name/{searchValue}?key=...
 *
 * Returns stop_groups: rikshållplatser and meta-stops across ALL of Sweden,
 * which is how SL and UL end up in one list without maintaining two datasets.
 * The returned id is the same id used by Trafiklab Timetables and by ResRobot,
 * so one search result drives both the departure board and the route planner.
 * -------------------------------------------------------------------------- */

interface StopLookupApi {

    @GET("v1/stops/name/{query}")
    suspend fun search(@Path("query") query: String): StopSearchResponse
}

object StopLookupClient {

    private const val BASE_URL = "https://realtime-api.trafiklab.se/"

    val api: StopLookupApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val url = chain.request().url.newBuilder()
                    .addQueryParameter("key", BuildConfig.TRAFIKLAB_KEY)
                    .build()
                chain.proceed(chain.request().newBuilder().url(url).build())
            }
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StopLookupApi::class.java)
    }
}

/* ----------------------------------------------------------------------------
 * Wire format
 * -------------------------------------------------------------------------- */

data class StopSearchResponse(
    @SerializedName("stop_groups") val stopGroups: List<StopGroupDto>?
)

data class StopGroupDto(
    val id: String?,
    val name: String?,
    @SerializedName("area_type") val areaType: String?,
    @SerializedName("average_daily_stop_times") val averageDailyStopTimes: Double?,
    @SerializedName("transport_modes") val transportModes: List<String>?,
    val stops: List<ChildStopDto>?
)

data class ChildStopDto(
    val id: String?,
    val name: String?,
    val lat: Double?,
    val lon: Double?
)

/* ----------------------------------------------------------------------------
 * Domain model
 * -------------------------------------------------------------------------- */

data class StationHit(
    val id: String,
    val name: String,
    val modes: List<TransportType>,
    val isMetaStop: Boolean,
    val traffic: Double,
    val lat: Double?,
    val lon: Double?
)

class StopSearchRepository(private val api: StopLookupApi = StopLookupClient.api) {

    /**
     * Ranked so the stop someone actually meant comes first: exact prefix
     * matches above mid-word matches, and within each group the busiest stop
     * first, because "Kista" should mean the metro station rather than a
     * side-street bus pole that sees four departures a day.
     */
    suspend fun search(query: String): List<StationHit> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()

        val response = api.search(trimmed)
        val lower = trimmed.lowercase(Locale.getDefault())

        return response.stopGroups.orEmpty()
            .mapNotNull { group ->
                val id = group.id ?: return@mapNotNull null
                val name = group.name ?: return@mapNotNull null
                val first = group.stops?.firstOrNull()
                StationHit(
                    id = id,
                    name = name,
                    modes = group.transportModes.orEmpty().mapNotNull(::modeOfName).distinct(),
                    isMetaStop = group.areaType == "META_STOP",
                    traffic = group.averageDailyStopTimes ?: 0.0,
                    lat = first?.lat,
                    lon = first?.lon
                )
            }
            .filter { it.modes.isNotEmpty() }   // stops with no traffic this period
            .sortedWith(
                compareBy<StationHit> {
                    val n = it.name.lowercase(Locale.getDefault())
                    when {
                        n == lower -> 0
                        n.startsWith(lower) -> 1
                        n.split(" ", "-").any { word -> word.startsWith(lower) } -> 2
                        else -> 3
                    }
                }.thenByDescending { it.traffic }
            )
            .take(25)
    }
}

internal fun modeOfName(value: String?): TransportType? = when (value?.uppercase(Locale.US)) {
    "TRAIN" -> TransportType.TRAIN
    "METRO" -> TransportType.METRO
    "BUS" -> TransportType.BUS
    "TRAM" -> TransportType.TRAM
    "BOAT", "FERRY", "SHIP" -> TransportType.FERRY
    else -> null
}
