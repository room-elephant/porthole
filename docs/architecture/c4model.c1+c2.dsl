workspace "Porthole" "A Docker dashboard that finds running containers and the pages they serve" {

    # ─────────────────────────────────────────────────────────────────────────
    # C1 + C2: human-maintained, STABLE.
    # The people, the external systems, the containers and their wiring cannot be
    # derived from the server's code, so they live here and change rarely. The
    # code-coupled C3 layer, the server's components with their packages and the
    # arrows between them, is split into c4model.c3.dsl, which C3ArchTest checks
    # against the Java packages on every build. The same test exports every view
    # below to generated/c4views/<key>.puml and renders its .svg.
    #
    # Relationships are not implied: each level states its own arrows, so a view
    # shows the arrows written for it and no arrow drawn from a lower level.
    # ─────────────────────────────────────────────────────────────────────────

    !impliedRelationships false

    model {
        owner = person "Homelab owner" "Opens the services their containers run; names, groups and links them"

        group "Docker hosts" {
            docker = softwareSystem "Docker Engines" "One or more Docker daemons, each a configured source, reached by socket or TCP" "External"
            services = softwareSystem "Containerised services" "The web pages the containers serve, on published ports or behind a reverse proxy such as Traefik" "External"
        }

        group "Registries and icons" {
            dockerhub = softwareSystem "Docker Hub" "Image tags and manifests" "External"
            ghcr = softwareSystem "GitHub Container Registry" "Image tags and manifests of public packages" "External"
            cdn = softwareSystem "Dashboard Icons on jsDelivr" "The default service icons" "External"
        }

        porthole = softwareSystem "Porthole" "Shows every container of every configured Docker host, with its page, icon and version" {
            browser = container "Browser application" "Tiles, filters, settings dialogs and live refresh" "React 19 / Vite" {
                tags "Browser"
            }
            server = container "Application server" "Watches the Docker sources, checks versions, keeps entries and serves the dashboard and the page; one process" "Java 25 / Spring Boot 4; GraalVM native" {
                # C3 components and the arrows between them, checked against the code
                !include c4model.c3.dsl
            }
            data = container "Data directory" "state.json with every saved entry, and the uploaded icon files" "Local filesystem, PORTHOLE_DATA_DIR" "Database"
            config = container "Configuration" "Docker connections, registry settings and icon mappings" "application.yml, icons.yml" "Database"
        }

        # C1
        owner -> porthole "Browses containers, opens their pages, edits names, icons and links"
        owner -> services "Opens a container's page"
        porthole -> docker "Lists containers, follows events, inspects images"
        porthole -> dockerhub "Reads tags and manifests"
        porthole -> ghcr "Reads tags and manifests"
        porthole -> cdn "Points the browser at default icons"
        docker -> services "Runs"

        # C2
        owner -> browser "Uses the dashboard"
        browser -> server "Reads the dashboard, saves entries, uploads icons" "HTTP / JSON"
        server -> browser "Signals a new dashboard revision" "Server-sent events"
        browser -> cdn "Loads default icons" "HTTPS"
        server -> docker "Lists containers, follows events, inspects images" "Docker Engine API over socket or TCP"
        server -> dockerhub "Reads tags and manifests" "HTTPS, registry v2 API"
        server -> ghcr "Reads tags and manifests" "HTTPS, registry v2 API"
        server -> data "Reads and writes entries and icons" "Filesystem"
        server -> config "Reads at startup" "Filesystem"

        # C3, the server's components to what is outside the server
        browser -> dashboard "Reads the dashboard, writes entries, follows /api/events" "HTTP / JSON and SSE"
        browser -> fleet "Reads source status" "HTTP / JSON"
        browser -> entries "Uploads and reads icons" "HTTP"
        browser -> versions "Starts a version check" "HTTP"
        fleet -> docker "Lists containers, follows events, inspects for version checks" "Docker Engine API"
        versions -> dockerhub "Reads tags and manifests" "HTTPS"
        versions -> ghcr "Reads tags and manifests" "HTTPS"
        entries -> data "Reads and writes state.json and icon files" "Filesystem"
    }

    views {
        properties {
            # Draws each element with its style below, and names the styles in the legend.
            "c4plantuml.tags" "true"
        }

        systemContext porthole "C1-Context" "Who uses Porthole, and which systems it talks to" {
            include *
            include services
            autoLayout lr
        }
        container porthole "C2-Containers" "What runs, where data lives, and how the parts communicate" {
            include *
            autoLayout lr
        }
        component server "C3-Server-Components" "The feature slices and what each one reaches outside the server" {
            include *
            autoLayout lr
        }

        styles {
            element "Person" {
                shape Person
                background #08427b
                color #ffffff
            }
            element "Software System" {
                background #1168bd
                color #ffffff
            }
            element "External" {
                background #999999
                color #ffffff
            }
            element "Container" {
                background #438dd5
                color #ffffff
            }
            element "Database" {
                shape Cylinder
            }
            element "Component" {
                background #85bbf0
                color #000000
            }
            relationship "Relationship" {
                color #707070
                thickness 2
                style solid
            }
        }
    }
}
