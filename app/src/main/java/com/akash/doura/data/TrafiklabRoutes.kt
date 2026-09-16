package com.akash.doura.data

import com.akash.doura.BuildConfig
import com.akash.doura.TransportType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/* ----------------------------------------------------------------------------
 * ResRobot Route planner
 *
 *   GET https://api.resrobot.se/v2.1/trip
 *       ?originId=740000001&destId=740000003
 *       &date=2026-09-15&time=08:30&searchForArrival=0
 *       &numF=6&products=<bitmask>&format=json&accessId=...
 *
 * originId / destId take the same ids as Stop Lookup and Nearby stops, so a
 * StationHit.id drops straight in. numF is capped at 6 results, so "later
 * journeys" re-queries from the last result's departure time.
 * -------------------------------------------------------------------------- */

interface TripApi {

    @GET("v2.1/trip")
    suspend fun trip(
        @Query("originId") originId: String,
        @Query("destId") destId: String,
        @Query("date") date: String?,
        @Query("time") time: String?,
        @Query("products") products: Int?,
        @Query("maxChange") maxChange: Int?,
        @Query("searchForArrival") searchForArrival: Int = 0,
        @Query("numF") numF: Int = 6,
        @Query("passlist") passlist: Int = 1,
        @Query("lang") lang: String = "sv",
        @Query("format") format: String = "json"
    ): TripResponse
}

object RouteClient {

    private const val BASE_URL = "https://api.resrobot.se/"

    val api: TripApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
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
            .create(TripApi::class.java)
    }
}

/* ----------------------------------------------------------------------------
 * Wire format (HAFAS-style, hence the capitalised field names)
 * -------------------------------------------------------------------------- */

data class TripResponse(val Trip: List<TripResultDto>?)

data class TripResultDto(
    val idx: Int?,
    val tripId: String?,
    val LegList: LegListDto?
)

data class LegListDto(val Leg: List<LegDto>?)

data class LegDto(
    val name: String?,
    val category: String?,
    val type: String?,          // JNY for a vehicle, WALK for a footpath
    val direction: String?,
    val dist: Int?,
    val Origin: PointDto?,
    val Destination: PointDto?,
    val Product: List<ProductLegDto>?,
    val Stops: StopsDto?,
    val transportNumber: String?
)

data class StopsDto(val Stop: List<LegStopDto>?)

data class LegStopDto(
    val name: String?,
    val extId: String?,
    val routeIdx: Int?,
    val lat: Double?,
    val lon: Double?,
    val depTime: String?,
    val depDate: String?,
    val arrTime: String?,
    val arrDate: String?
)

data class PointDto(
    val name: String?,
    val extId: String?,
    val time: String?,          // HH:mm:ss
    val date: String?,          // yyyy-MM-dd
    val track: String?,
    val rtTime: String?,
    val rtDate: String?,
    val rtTrack: String?
)

data class ProductLegDto(
    val name: String?,
    val num: String?,
    val line: String?,
    val catOut: String?,
    val catOutL: String?,
    val catCode: String?,
    val cls: String?,
    val operator: String?
)

/* ----------------------------------------------------------------------------
 * Domain model
 * -------------------------------------------------------------------------- */

enum class LegKind { VEHICLE, WALK }

data class LegStop(
    val name: String,
    val time: String,          // HH:mm, arrival where known, else departure
    val millis: Long?
)

data class JourneyLeg(
    val kind: LegKind,
    val line: String?,
    val lineName: String?,     // "Länstrafik - Tåg 41", for the detail header
    val mode: TransportType?,
    val direction: String?,
    val operator: String?,
    val fromId: String?,
    val toId: String?,
    val fromName: String,
    val toName: String,
    val departTime: String,
    val arriveTime: String,
    val departMillis: Long?,
    val arriveMillis: Long?,
    val fromTrack: String?,
    val minutes: Int,
    val stops: List<LegStop>   // includes both endpoints when passlist=1
)

data class Journey(
    val key: String,
    val departTime: String,
    val arriveTime: String,
    val departMillis: Long,
    val durationMinutes: Int,
    val changes: Int,
    val originName: String,
    val destinationName: String,
    val originTrack: String?,
    val delayMinutes: Int,
    val legs: List<JourneyLeg>
) {
    val vehicleLegs: List<JourneyLeg> get() = legs.filter { it.kind == LegKind.VEHICLE }
}

/* ----------------------------------------------------------------------------
 * Repository
 * -------------------------------------------------------------------------- */

/**
 * ResRobot product class codes: 2 high-speed rail, 4 regional rail,
 * 8 express bus, 16 local train, 32 metro, 64 tram, 128 bus, 256 ferry.
 * The API takes the sum of the codes you want.
 */
private fun bitmaskOf(types: Set<TransportType>): Int? {
    if (types.isEmpty()) return null            // omit the parameter = everything
    var mask = 0
    types.forEach { type ->
        mask += when (type) {
            TransportType.TRAIN -> 2 + 4 + 16
            TransportType.METRO -> 32
            TransportType.TRAM -> 64
            TransportType.BUS -> 8 + 128
            TransportType.FERRY -> 256
        }
    }
    return mask
}

class RouteRepository(private val api: TripApi = RouteClient.api) {

    suspend fun journeys(
        originId: String,
        destId: String,
        date: String?,
        time: String?,
        types: Set<TransportType>,
        maxChange: Int? = null
    ): List<Journey> {
        val response = api.trip(
            originId = originId,
            destId = destId,
            date = date,
            time = time,
            products = bitmaskOf(types),
            maxChange = maxChange
        )

        return response.Trip.orEmpty()
            .mapNotNull { it.toJourney() }
            .sortedBy { it.departMillis }
    }
}

private fun TripResultDto.toJourney(): Journey? {
    val legs = LegList?.Leg.orEmpty().mapNotNull { it.toDomain() }
    if (legs.isEmpty()) return null

    val first = LegList?.Leg?.firstOrNull() ?: return null
    val last = LegList?.Leg?.lastOrNull() ?: return null

    val departMillis = millisOf(first.Origin?.date, first.Origin?.time) ?: return null
    val arriveMillis = millisOf(last.Destination?.date, last.Destination?.time) ?: return null

    val scheduledDepart = departMillis
    val realDepart = millisOf(first.Origin?.rtDate, first.Origin?.rtTime) ?: scheduledDepart

    return Journey(
        key = (tripId ?: "$idx") + "@" + departMillis,
        departTime = hhmm(first.Origin?.time),
        arriveTime = hhmm(last.Destination?.time),
        departMillis = departMillis,
        durationMinutes = ((arriveMillis - departMillis) / 60_000L).toInt(),
        changes = (legs.count { it.kind == LegKind.VEHICLE } - 1).coerceAtLeast(0),
        originName = first.Origin?.name ?: "",
        destinationName = last.Destination?.name ?: "",
        originTrack = first.Origin?.rtTrack ?: first.Origin?.track,
        delayMinutes = ((realDepart - scheduledDepart) / 60_000L).toInt(),
        legs = legs
    )
}

private fun LegDto.toDomain(): JourneyLeg? {
    val origin = Origin ?: return null
    val destination = Destination ?: return null

    val departMillis = millisOf(origin.date, origin.time)
    val arriveMillis = millisOf(destination.date, destination.time)
    val minutes = if (departMillis != null && arriveMillis != null) {
        ((arriveMillis - departMillis) / 60_000L).toInt()
    } else 0

    // WALK is a walk to or from a stop; TRSF is a transfer on foot or a wait
    // over 15 minutes. Neither puts the traveller on a vehicle.
    val onFoot = type.equals("WALK", ignoreCase = true) || type.equals("TRSF", ignoreCase = true)
    val product = Product?.firstOrNull()

    val stops = Stops?.Stop.orEmpty().mapNotNull { stop ->
        val stopName = stop.name ?: return@mapNotNull null
        val time = stop.arrTime ?: stop.depTime
        LegStop(
            name = stopName,
            time = hhmm(time),
            millis = millisOf(stop.arrDate ?: stop.depDate, time)
        )
    }

    return JourneyLeg(
        kind = if (onFoot) LegKind.WALK else LegKind.VEHICLE,
        line = product?.line ?: product?.num ?: transportNumber ?: name?.trim(),
        lineName = product?.name ?: name?.trim(),
        mode = if (onFoot) null else modeOfLeg(product, category, name),
        direction = direction,
        operator = product?.operator,
        fromId = origin.extId,
        toId = destination.extId,
        fromName = origin.name ?: "",
        toName = destination.name ?: "",
        departTime = hhmm(origin.time),
        arriveTime = hhmm(destination.time),
        departMillis = departMillis,
        arriveMillis = arriveMillis,
        fromTrack = origin.rtTrack ?: origin.track,
        minutes = minutes,
        stops = stops
    )
}

/* ----------------------------------------------------------------------------
 * Ticket hints
 *
 * Fares are not in the API, so this reads the operator on each vehicle leg and
 * names the authorities whose tickets the journey touches. It tells you WHICH
 * tickets to look at, never what they cost or whether one covers the whole trip.
 * -------------------------------------------------------------------------- */

data class TicketHint(val authority: String, val note: String?)

fun ticketHints(journey: Journey): List<TicketHint> {
    val seen = LinkedHashMap<String, TicketHint>()

    journey.vehicleLegs.forEach { leg ->
        val operator = leg.operator?.trim().orEmpty()
        val words = operator.split(" ", "-", "/").map { it.lowercase(Locale.getDefault()) }

        val hint = when {
            words.contains("sl") || operator.contains("Storstockholm", true) ->
                TicketHint("SL", "Stockholm county")
            words.contains("ul") || operator.contains("Upplands", true) ->
                TicketHint("UL", "Uppsala county")
            operator.isNotBlank() -> TicketHint(operator, null)
            else -> null
        }

        hint?.let { seen.putIfAbsent(it.authority, it) }
    }

    return seen.values.toList()
}

/**
 * Product classes aren't always populated the same way across operators, so
 * fall back through cls, then catCode, then the human-readable category text.
 */
private fun modeOfLeg(product: ProductLegDto?, category: String?, name: String?): TransportType? {
    val code = product?.cls ?: product?.catCode
    when (code) {
        "2", "4", "16" -> return TransportType.TRAIN
        "32" -> return TransportType.METRO
        "64" -> return TransportType.TRAM
        "8", "128" -> return TransportType.BUS
        "256" -> return TransportType.FERRY
    }

    val text = listOfNotNull(product?.catOutL, product?.catOut, category, name)
        .joinToString(" ")
        .lowercase(Locale.getDefault())

    return when {
        text.contains("tunnelbana") || text.contains("metro") -> TransportType.METRO
        text.contains("spårväg") || text.contains("tram") || text.contains("bana") -> TransportType.TRAM
        text.contains("färja") || text.contains("båt") || text.contains("ferry") -> TransportType.FERRY
        text.contains("buss") || text.contains("bus") -> TransportType.BUS
        text.contains("tåg") || text.contains("train") -> TransportType.TRAIN
        else -> null
    }
}

private val DATE_TIME = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

private fun millisOf(date: String?, time: String?): Long? {
    if (date == null || time == null) return null
    return runCatching { DATE_TIME.parse("$date $time")?.time }.getOrNull()
}

private fun hhmm(time: String?): String = time?.take(5) ?: "--:--"

/** Formats for the API's date and time query parameters. */
object TripQuery {
    private val DATE = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val TIME = SimpleDateFormat("HH:mm", Locale.US)

    private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)

    fun date(millis: Long): String = DATE.format(Date(millis))
    fun time(millis: Long): String = TIME.format(Date(millis))
    fun iso(millis: Long): String = ISO.format(Date(millis))
}