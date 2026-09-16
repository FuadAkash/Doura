# Doura

An Android journey planner for Swedish public transport, built because no single
app cleanly covered both SL (Stockholm) and UL (Uppsala) for a commute that
crosses between them.

Doura searches every stop in Sweden, plans journeys across operators, shows live
departure boards for any stop, and finds what's running near you.

<!-- Add a screen recording or screenshots here -->

## Why

Commuting between Stockholm and Uppsala means two transit authorities, two
ticketing systems, and two apps that each pretend the other region doesn't
exist. Doura treats the country as one network, because the underlying open data
already does.

## Features

**Journey planning** — search between any two stops with a departure time and
optional transport-type filters. Results show departure and arrival times, total
duration, changes, and the departure platform.

**Journey detail** — a vertical timeline of the whole trip: where to board, every
stop along the way, where to change and how long the connection is, and where to
get off. Intermediate stops collapse behind a summary so a long leg doesn't bury
the instructions.

**Live departure boards** — tap any stop to see what's leaving in the next hour,
with realtime delays, cancellations and platform designations. Paging walks the
board forward hour by hour through the service day.

**Nearby stops** — GPS position, reverse-geocoded to a street name, then every
stop within 2 km sorted by distance with the modes each one serves.

**Nationwide stop search** — debounced search over every stop in Sweden, ranked
so the stop you meant comes first: exact prefix matches above mid-word matches,
and busier stops above quiet ones.

**Ticket hints** — reads the operator on each leg and names which authorities'
tickets the journey touches. Fares aren't in the open data, so this points you at
the right app rather than guessing at prices.

## Built with

- **Kotlin** and **Jetpack Compose** (Material 3)
- **Retrofit** + **Gson** for the API layer
- **Play Services Location** for GPS
- **AndroidX Lifecycle** ViewModels for state
- **Core SplashScreen** for the launch experience

Minimum SDK 24, target SDK 34.

## Data sources

All transport data comes from [Trafiklab](https://www.trafiklab.se), Sweden's
open data portal for public transport, run by Samtrafiken.

| API | Used for |
|---|---|
| Trafiklab Stop Lookup | Nationwide stop search in the From/To picker |
| Trafiklab Realtime Timetables | Departure boards, realtime delays, platform designations |
| ResRobot Route planner | Journey planning, intermediate stops, transfers |
| ResRobot Nearby stops | Stops within a radius of a coordinate |

Because these APIs are national rather than per-operator, SL, UL, Skånetrafiken,
Västtrafik, SJ and the rest all come through the same calls. There's no
per-operator dataset to maintain.

Traffic data from Trafiklab.se, licensed CC-BY.

## Architecture

```
com.akash.doura
├── MainActivity.kt          Home screen, filters, sheets, theme
├── data/                    API clients, DTOs, repositories
│   ├── TrafiklabStops       Stop search
│   ├── TrafiklabRoutes      Journey planning
│   ├── TrafiklabDepartures  Departure boards
│   └── TrafiklabNearby      Nearby stops
├── location/                GPS + reverse geocoding
├── search/                  Station picker, journey list, journey detail
├── nearby/                  Nearest-station tab
├── board/                   Departure board sheet
└── splash/                  Launch screen
```

Each API has its own Retrofit client with the key injected by an OkHttp
interceptor, so keys never appear at call sites. Repositories map wire formats
onto domain models; ViewModels hold screen state; composables stay dumb.

## Running it

You'll need two free API keys from
[developer.trafiklab.se](https://developer.trafiklab.se) — one for **ResRobot
v2.1**, one for **Trafiklab Realtime APIs**. Both have generous free tiers.

Put them in `local.properties` (gitignored):

```properties
RESROBOT_KEY=your_resrobot_key
TRAFIKLAB_KEY=your_realtime_key
```

Then build and run. The keys are exposed to the app through `BuildConfig`.

Testing on an emulator: set a position under Extended Controls → Location, or the
nearby-stops feature has nothing to work with. Kista is 59.4022, 17.9447.

## Known limitations

**Vehicle positions are estimated.** The route planner returns no live vehicle
positions, so the "around here now" marker on the journey timeline is inferred
from the timetable — the last stop whose scheduled time has passed. Real
positions would mean consuming GTFS Regional's protobuf realtime feed.

**Ticket hints name authorities, not fares.** No open API carries fare rules, so
Doura tells you which operators' tickets a journey touches and leaves pricing to
their apps.

**Journey paging re-queries by time.** The route planner caps a response at six
journeys. "Show later journeys" asks again from one minute after the last result
departed, which is the documented approach.

## License

<!-- Add your license here -->

Transport data © Trafiklab / Samtrafiken, licensed under CC-BY.
