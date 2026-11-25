# Porthole architecture sources

The sources behind the views in [ARCHITECTURE.md](../ARCHITECTURE.md). Hand-written sources sit here directly;
generated outputs land in [`generated/`](generated/), and are committed so they can be reviewed in a PR.

## Hand-written sources

Structurizr DSL, split by how often it changes:
- [`c4model.c1+c2.dsl`](c4model.c1+c2.dsl) is the **stable** model: the people, external systems and containers,
  every view's definition and the styles. It `!include`s the other file inside the application server container.
- [`c4model.c3.dsl`](c4model.c3.dsl) is the **code-coupled** model: the server's components, one per feature slice,
  and the dependencies between them. Each component carries a `pkg:<pattern>` tag naming the packages it holds. This
  is the file that changes when slices or their dependencies do.

Package diagrams, each checked against the code by `PackagesArchTest`:
- [`packages.puml`](packages.puml): every production package, one box each (`<<..porthole.fleet.docker>>`, with no
  trailing `..`, so no class is in two boxes), grouped in a frame per slice, with every dependency between packages
  except those on `shared`.
- [`fleet-packages.puml`](fleet-packages.puml), [`versions-packages.puml`](versions-packages.puml),
  [`entries-packages.puml`](entries-packages.puml) and [`dashboard-packages.puml`](dashboard-packages.puml): the
  slice's root package and its direct subpackages, and the dependencies between them.
- [`versions-registry-packages.puml`](versions-registry-packages.puml): the same, one level down, for
  `versions.registry`.

Each box's hidden stereotype is the package pattern ArchUnit reads (`<<..porthole.fleet.docker..>>`, a package and
its subpackages; `<<..porthole.fleet>>`, the root alone). A cycle is drawn as one red line with a head at each end,
`[a] <-[#C62828]-> [b]`, which ArchUnit cannot read, backed by `[a] -[hidden]-> [b]` and `[b] -[hidden]-> [a]`,
which it reads as the two dependencies. `versions-registry-packages.puml` has three.

Open `c4model.c1+c2.dsl` in [structurizr.com/dsl](https://structurizr.com/dsl) or the Structurizr CLI for an
interactive view.

[`codecity.sh`](codecity.sh) draws the Java code as a Code City and stores a picture of it. It is run by hand before a
PR that changes Java, since it needs macOS, Chrome and Python 3.10+; see "Code City" in
[ARCHITECTURE.md](../ARCHITECTURE.md).

## What checks them

`C3ArchTest` (`server/src/test/java/com/roomelephant/porthole/`) runs with the unit tests. It fails when:
- a production package matches no component's `pkg:` tag, or a component has no `pkg:` tag;
- a `pkg:` pattern matches no production package;
- the code has a dependency between two components that the DSL does not draw, or the DSL draws an arrow the code
  does not have (a dependency into `shared` is exempt, since every slice may use it; an arrow out of it is not);
- an arrow between components has no description;
- the server components view leaves out a component or an arrow between components;
- the DSL does not parse (the message is Structurizr's own, with the line);
- a generated view had to be written again.

`PackagesArchTest`, in the same folder, checks each package diagram against the code. It fails when:
- a class in the slice depends on one in another box and the diagram has no arrow for it (ArchUnit's
  `adhereToPlantUmlDiagram`);
- the diagram's boxes are not exactly the slice's direct subpackages, plus its root package when that holds classes,
  or, for `packages.puml`, not exactly every production package;
- the diagram draws an arrow that no class dependency backs;
- its SVG in `generated/` had to be rendered again.

Both tests read compiled classes, so a package left in `server/target/classes` by another branch shows up as one
that belongs to no component; `mvn clean` removes it.

`ArchitectureTest` is separate: it says which dependencies are allowed at all, and how a slice's root relates to its
adapters. The DSL draws the dependencies that exist.

## Generated outputs

- [`generated/c4views/`](generated/c4views/): one `.puml` and one `.svg` per view: `C1-Context`, `C2-Containers` and
  `C3-Server-Components`. Written by `C3ArchTest`, which exports the parsed workspace with Structurizr's
  `C4PlantUMLExporter`. A view that no longer exists has its files removed, and the run fails.
- `generated/packages.svg` and `generated/<slice>-packages.svg`: the package diagrams, rendered by `PackagesArchTest` from the hand-written
  sources above.
- [`generated/codecity.jpg`](generated/codecity.jpg): the Code City, a screenshot taken by `codecity.sh`. No test
  writes or checks it; the script's other files stay in the gitignored `.codecity/`.

`GeneratedDiagrams` renders each `.puml` with PlantUML's built-in Smetana layout, so no Graphviz install is needed on
a machine or a CI runner. An SVG embeds the source it was drawn from and is drawn again only when that differs from its
`.puml`, so a run that changes nothing writes nothing. A PlantUML or C4-stdlib version bump does not re-render existing
SVGs; to re-render them all, delete `generated/c4views/*.svg` and run `C3ArchTest`.

## Maintaining

1. Edit the DSL: `c4model.c3.dsl` for a slice or a dependency between slices, `c4model.c1+c2.dsl` for anything else.
2. `make -C server test-one TEST=C3ArchTest`
3. Commit what it rewrote, and run it again: the second run passes and writes nothing.

A new package, or a new dependency between two packages, is the same loop with `PackagesArchTest`: edit
`packages.puml`, and the slice's `<slice>-packages.puml` when the change is inside one slice, by hand; it renders the
SVGs.
