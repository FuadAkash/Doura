package com.akash.doura.data

import com.akash.doura.BuildConfig
import com.akash.doura.TransportType
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/* ----------------------------------------------------------------------------
 * Trafiklab Realtime Timetables
 *
 *   GET https://realtime-api.trafiklab.se/v1/departures/{areaId}?key=...
 *   GET https://realtime-api.trafiklab.se/v1/departures/{areaId}/{time}?key=...
 *
 * time is YYYY-MM-DDTHH:mm (no seconds). The window is ALWAYS 60 minutes and
 * cannot be widened, so a full day means calling repeatedly, each time one hour
 * later. Responses are cached server-side for 60 seconds.
 *
 * The area id is the same id ResRobot returns as extId, so NearbyStop.id can be
 * passed straight in.
 *
 * Needs a second key (Trafiklab Realtime APIs, not ResRobot):
 *   local.properties:  TRAFIKLAB_KEY=your_realtime_key
 *   build.gradle.kts:  buildConfigField("String", "TRAFIKLAB_KEY",
 *                          "\"${props.getProperty("TRAFIKLAB_KEY") ?: ""}\"")
 * -------------------------------------------------------------------------- */

interface TimetablesApi {

    @GET("v1/departures/{areaId}")
    suspend fun departures(@Path("areaId") areaId: String): DeparturesResponse

    @GET("v1/departures/{areaId}/{time}")
    suspend fun departuresAt(
        @Path("areaId") areaId: String,
        @Path("time") time: String
    ): DeparturesResponse
}

object TimetablesClient {

    private const val BASE_URL = "https://realtime-api.trafiklab.se/"

    val api: TimetablesApi by lazy {
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
            .create(TimetablesApi::class.java)
    }
}

/* ----------------------------------------------------------------------------
 * Wire format
 * -------------------------------------------------------------------------- */

data class DeparturesResponse(
    val timestamp: String?,
    val query: QueryDto?,
    val stops: List<StopDto>?,
    val departures: List<CallDto>?
)

data class QueryDto(
    @SerializedName("queryTime") val queryTime: String?,
    val query: String?
)

data class StopDto(
    val id: String?,
    val name: String?,
    @SerializedName("transport_modes") val transportModes: List<String>?
)

data class CallDto(
    val scheduled: String?,
    val realtime: String?,
    val delay: Int?,
    val canceled: Boolean?,
    val route: RouteDto?,
    val trip: TripDto?,
    val stop: StopDto?,
    @SerializedName("scheduled_platform") val scheduledPlatform: PlatformDto?,
    @SerializedName("realtime_platform") val realtimePlatform: PlatformDto?,
    @SerializedName("is_realtime") val isRealtime: Boolean?,
    val alerts: List<AlertDto>?
)

data class RouteDto(
    val name: String?,
    val designation: String?,
    @SerializedName("transport_mode") val transportMode: String?,
    val direction: String?,
    val destination: EndpointDto?
)

data class EndpointDto(val id: String?, val name: String?)
data class TripDto(@SerializedName("trip_id") val tripId: String?)
data class PlatformDto(val designation: String?)
data class AlertDto(val title: String?, val text: String?)

/* ----------------------------------------------------------------------------
 * Domain model
 * -------------------------------------------------------------------------- */

data class StopDeparture(
    val key: String,
    val line: String,
    val mode: TransportType,
    val destination: String,
    val scheduledTime: String,      // HH:mm
    val expectedTime: String,       // HH:mm, equals scheduled when no realtime
    val delayMinutes: Int,
    val canceled: Boolean,
    val isRealtime: Boolean,
    val platform: String?,
    val stopName: String?,
    val minutesFromNow: Int?,
    val alert: String?
) {
    val isDelayed: Boolean get() = delayMinutes > 0
}

/* ----------------------------------------------------------------------------
 * Repository
 * -------------------------------------------------------------------------- */

private val API_TIME = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
private val FULL_TIME = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

/** One page of departures plus the timestamp to ask for to get the next hour. */
data class DeparturePage(
    val stopName: String?,
    val modes: List<TransportType>,
    val departures: List<StopDeparture>,
    val nextQueryTime: String?,
    val windowEnd: String?
)

class DepartureRepository(private val api: TimetablesApi = TimetablesClient.api) {

    suspend fun page(areaId: String, time: String? = null): DeparturePage {
        val response = if (time == null) api.departures(areaId) else api.departuresAt(areaId, time)

        val base = response.query?.queryTime?.let { parseFull(it) ?: parseApi(it) } ?: Date()
        val next = Calendar.getInstance().apply {
            this.time = base
            add(Calendar.MINUTE, 60)
        }

        // Stop serving pages once we've walked past the end of the service day.
        val sameDay = Calendar.getInstance().apply { this.time = base }
            .get(Calendar.DAY_OF_YEAR) == next.get(Calendar.DAY_OF_YEAR)

        val departures = response.departures.orEmpty().mapNotNull { it.toDomain() }

        return DeparturePage(
            stopName = response.stops?.firstOrNull()?.name,
            modes = response.stops.orEmpty()
                .flatMap { it.transportModes.orEmpty() }
                .mapNotNull(::modeOf)
                .distinct(),
            departures = departures,
            nextQueryTime = if (sameDay) API_TIME.format(next.time) else null,
            windowEnd = hhmm(next.time)
        )
    }
}

private fun CallDto.toDomain(): StopDeparture? {
    val mode = modeOf(route?.transportMode) ?: return null
    val scheduled = scheduled?.let { parseFull(it) } ?: return null
    val expected = realtime?.let { parseFull(it) } ?: scheduled

    val line = route?.designation?.takeIf { it.isNotBlank() }
        ?: route?.name?.takeIf { it.isNotBlank() }
        ?: "—"

    val destination = route?.direction?.takeIf { it.isNotBlank() }
        ?: route?.destination?.name
        ?: "—"

    val minutesAway = ((expected.time - System.currentTimeMillis()) / 60_000L).toInt()

    return StopDeparture(
        key = (trip?.tripId ?: line) + "@" + (this.scheduled ?: ""),
        line = line,
        mode = mode,
        destination = destination,
        scheduledTime = hhmm(scheduled),
        expectedTime = hhmm(expected),
        delayMinutes = ((delay ?: 0) / 60),
        canceled = canceled == true,
        isRealtime = isRealtime == true,
        platform = (realtimePlatform ?: scheduledPlatform)?.designation,
        stopName = stop?.name,
        minutesFromNow = if (minutesAway in 0..59) minutesAway else null,
        alert = alerts?.firstOrNull()?.title
    )
}

/** transport_mode is one of BUS, METRO, TRAIN, TRAM, TAXI, BOAT. */
private fun modeOf(value: String?): TransportType? = when (value?.uppercase(Locale.US)) {
    "TRAIN" -> TransportType.TRAIN
    "METRO" -> TransportType.METRO
    "BUS" -> TransportType.BUS
    "TRAM" -> TransportType.TRAM
    "BOAT" -> TransportType.FERRY
    else -> null                     // TAXI (närtrafik) has no badge in this app
}

private fun parseFull(value: String): Date? = runCatching { FULL_TIME.parse(value) }.getOrNull()
private fun parseApi(value: String): Date? = runCatching { API_TIME.parse(value) }.getOrNull()
private fun hhmm(date: Date): String = SimpleDateFormat("HH:mm", Locale.US).format(date)
