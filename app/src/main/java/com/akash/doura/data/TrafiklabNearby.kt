package com.akash.doura.data

import com.akash.doura.BuildConfig
import com.akash.doura.TransportType
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.Locale
import java.util.concurrent.TimeUnit

/* ----------------------------------------------------------------------------
 * API
 *
 * GET https://api.resrobot.se/v2.1/location.nearbystops
 *     ?originCoordLat=59.40&originCoordLong=17.94&r=2000&maxNo=30
 *     &format=json&accessId=YOUR_KEY
 *
 * r is capped at 10000 m, maxNo at 1000. accessId is added by the interceptor
 * below so it never appears in call sites or logs you paste into a bug report.
 * -------------------------------------------------------------------------- */

interface ResRobotApi {

    @GET("v2.1/location.nearbystops")
    suspend fun nearbyStops(
        @Query("originCoordLat") lat: Double,
        @Query("originCoordLong") lon: Double,
        @Query("r") radiusMetres: Int = 2000,
        @Query("maxNo") maxResults: Int = 30,
        @Query("lang") lang: String = "sv",
        @Query("format") format: String = "json"
    ): NearbyStopsResponse
}

object TrafiklabClient {

    private const val BASE_URL = "https://api.resrobot.se/"

    val resRobot: ResRobotApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val url = chain.request().url.newBuilder()
                    .addQueryParameter("accessId", BuildConfig.RESROBOT_KEY)
                    .build()
                chain.proceed(chain.request().newBuilder().url(url).build())
            }
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ResRobotApi::class.java)
    }
}

/* ----------------------------------------------------------------------------
 * Wire format
 * -------------------------------------------------------------------------- */

data class NearbyStopsResponse(
    @SerializedName("stopLocationOrCoordLocation") val results: List<StopLocationWrapper>?
)

data class StopLocationWrapper(
    @SerializedName("StopLocation") val stop: StopLocationDto?
)

data class StopLocationDto(
    val name: String?,
    val extId: String?,          // the id to use in other Trafiklab calls
    val id: String?,             // internal, documented as "do not use"
    val lat: Double?,
    val lon: Double?,
    val dist: Int?,              // metres from the queried coordinates
    val productAtStop: List<ProductDto>?
)

data class ProductDto(val cls: String?)

/* ----------------------------------------------------------------------------
 * Domain model
 * -------------------------------------------------------------------------- */

data class NearbyStop(
    val id: String,
    val name: String,
    val metres: Int,
    val lat: Double,
    val lon: Double,
    val types: List<TransportType>
)

/**
 * ResRobot product class codes, from the "ResRobot data types" page.
 * 1 air, 2 high-speed rail, 4 regional rail, 8 express bus, 16 local train,
 * 32 metro, 64 tram, 128 bus, 256 ferry, 512 taxi.
 */
private fun TransportTypeOf(cls: String?): TransportType? = when (cls) {
    "2", "4", "16" -> TransportType.TRAIN
    "32" -> TransportType.METRO
    "64" -> TransportType.TRAM
    "8", "128" -> TransportType.BUS
    "256" -> TransportType.FERRY
    else -> null                 // air and taxi have no place in this app
}

class NearbyRepository(private val api: ResRobotApi = TrafiklabClient.resRobot) {

    /**
     * Stops within [metres] of a coordinate, nearest first.
     *
     * Two bits of cleanup matter for a usable list:
     * a big interchange returns one entry per physical stop point, so entries
     * sharing a name are merged into the nearest one with their modes pooled;
     * and all-caps names are ResRobot's virtual grouping stations (GÖTEBORG,
     * STOCKHOLM), which aren't places you can stand and wait, so they're out.
     */
    suspend fun stopsWithin(
        lat: Double,
        lon: Double,
        metres: Int = 2000,
        maxResults: Int = 30
    ): List<NearbyStop> {
        val response = api.nearbyStops(
            lat = lat,
            lon = lon,
            radiusMetres = metres.coerceIn(1, 10_000),
            maxResults = maxResults.coerceIn(1, 1000)
        )

        return response.results.orEmpty()
            .mapNotNull { it.stop }
            .filter { dto ->
                val name = dto.name
                name != null && dto.extId != null && dto.lat != null && dto.lon != null &&
                    name != name.uppercase(Locale("sv", "SE"))
            }
            .map { dto ->
                NearbyStop(
                    id = dto.extId!!,
                    name = dto.name!!,
                    metres = dto.dist ?: metres,
                    lat = dto.lat!!,
                    lon = dto.lon!!,
                    types = dto.productAtStop.orEmpty().mapNotNull { TransportTypeOf(it.cls) }.distinct()
                )
            }
            .groupBy { it.name }
            .map { (_, group) ->
                val nearest = group.minBy { it.metres }
                nearest.copy(types = group.flatMap { it.types }.distinct())
            }
            .filter { it.metres <= metres }
            .sortedBy { it.metres }
    }
}
