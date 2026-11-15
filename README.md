corewars8086
============

Core Wars for standard 8086 assembly.

## Build & test

Requires JDK 8+ and Maven.

```
mvn verify        # compile + run tests
mvn package       # also builds target/corewars8086-<version>-jar-with-dependencies.jar
```

## Run

```
java -jar bin/corewars8086-<version>.jar            # GUI
java -jar bin/corewars8086-<version>.jar --headless # command line (see cli/Options.java for flags)
```

The `bundle/` directory contains launcher scripts (`cgx*.sh` / `cgx*.bat`),
sample survivors and the color table.

## Layout

| Package | Responsibility |
|---|---|
| `cpu` | 8086 emulation: `Cpu` (opcode dispatch), `Alu`, `ShiftOps`, `StringOps`, decoders |
| `memory` | Real-mode memory model and access restrictions |
| `war` | Warriors, wars and competitions (no GUI dependencies) |
| `cli` | Headless runner and command-line options |
| `gui` | Swing front-end and visual effects |
