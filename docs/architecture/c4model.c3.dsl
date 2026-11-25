# ─────────────────────────────────────────────────────────────────────────────
# C3: the application server's components. CODE-COUPLED: this file tracks the Java code.
#
# Not a standalone workspace: c4model.c1+c2.dsl `!include`s it inside the `server`
# container. Kept apart because, unlike the stable C1/C2 layer, every line here is
# checked against the code by C3ArchTest (server/src/test):
#   • each `pkg:<pattern>` tag must match a production package, and every package
#     under com.roomelephant.porthole must match one; patterns are relative to it,
#     `x.**` matches x and everything under it, `x` alone matches x only, an empty
#     pattern (`pkg:`) matches the base package itself, and the longest matching
#     pattern wins;
#   • each `src -> dst` arrow below must be a code dependency between the two
#     components, and every such dependency must be drawn, except arrows into
#     `shared`: every slice may use it, so those are never drawn, while an arrow
#     out of `shared` must be.
# Which arrows are allowed at all is ArchitectureTest's job; this file draws the
# ones the code has. The server's arrows to stores, the browser and external
# systems are in c4model.c1+c2.dsl, which nothing checks.
# ─────────────────────────────────────────────────────────────────────────────

fleet       = component "Fleet"            "Configured sources, one SourceMonitor each polling and following events, snapshots kept in memory; GET /api/sources" "Java; docker-java adapter"          "pkg:fleet.**"
endpoints   = component "Endpoints"        "Traefik and published-port providers, candidates and automatic selection"                                         "Java"                              "pkg:endpoints.**"
versions    = component "Versions"         "Background version checks from snapshots, their cache, and the Docker Hub and GHCR clients; POST /api/version-checks" "Java; registry v2 HTTP clients"   "pkg:versions.**"
entries     = component "Entries"          "Saved names, icons, endpoint choices and links, state.json and uploaded icons; /api/icons"                         "Java; JSON file store"             "pkg:entries.**"
dashboard   = component "Dashboard"        "Projects snapshots, entries and versions into the dashboard, counts revisions; /api/dashboard, /api/events, /api/entries" "Java; Spring MVC, SSE"     "pkg:dashboard.**"
shared      = component "Shared rules"     "Hosts, HTTP URLs, atomic files, image references and the exception handler"                                       "Java"                              "pkg:shared.**"
nativehints = component "Native hints"     "Reflection hints for library types the native-image agent cannot observe"                                         "Spring AOT"                        "pkg:nativehints.**"
application = component "Application"      "The Spring Boot entry point"                                                                                       "Spring Boot"                       "pkg:"

versions  -> fleet    "Listens to snapshots, inspects containers and images through the fleet" "Java calls"
entries   -> fleet    "Checks that a container's source is configured" "Java calls"
dashboard -> fleet    "Reads snapshots, listens to their changes"  "Java calls"
dashboard -> endpoints "Discovers and selects endpoints"           "Java calls"
dashboard -> versions "Reads cached versions, listens to results"  "Java calls"
dashboard -> entries  "Reads and writes entries, listens to writes" "Java calls"
