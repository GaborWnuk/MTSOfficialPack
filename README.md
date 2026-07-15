# Immersive Vehicles — Official Content Pack [OCP]

Official Content Pack with planes, cars, tanks, helicopters and trucks for the
[Immersive Vehicles](https://modrinth.com/mod/immersive-vehicles) mod
(formerly known as Minecraft Transport Simulator).

<img src="https://i.imgur.com/eafDLSV.gif" alt="Immersive Vehicles OCP showcase" />

> **The core mod "Immersive Vehicles" is required!**
> Get it on [Modrinth](https://modrinth.com/mod/immersive-vehicles) or
> [CurseForge](https://www.curseforge.com/minecraft/mc-mods/minecraft-transport-simulator).

## About

OCP contains state-of-the-art planes, cars, tanks, helicopters and trucks that are
functionally kept simple to not only show off what is possible with the Immersive
Vehicles mod from a technical standpoint, but also provide a carefully constructed,
immersive and user-friendly flying and driving experience. All vehicles and parts
are craftable in survival.

The fleet includes the Skyhawk, MC-172, Bell 47G, Eclipse 500, Comanche, Vulcanair,
Trimotor, PZL P.11 and PZL.37 Łoś in the air, and the likes of the Mustang, Scout,
GMC truck, fire truck, quad and FT-17 on the ground — plus armament, signs, poles
and decor to build the world around them.

## Supported Minecraft versions

| Minecraft | Loader | Release file |
| --- | --- | --- |
| 1.12.2 | Forge | `MTS Official Pack-1.12.2-*.jar` |
| 1.16.5 | Forge | `MTS Official Pack-1.16.5-*.jar` |
| 1.18.2 | Forge | `MTS Official Pack-1.18.2-*.jar` |
| 1.19.2 | Forge | `MTS Official Pack-1.19.2-*.jar` |
| 1.20.1 | Forge | `MTS Official Pack-1.20.1-*.jar` |
| 1.21.1 | NeoForge | `MTS Official Pack-1.21.1-*.jar` |
| 26.1.2+ | NeoForge | `MTS Official Pack-26.1.2-*.jar` |

Per-version jars are published on this repository's
[Releases](../../releases) page. The official pack is also distributed on
[CurseForge](https://www.curseforge.com/minecraft/mc-mods/transport-simulator-official-vehicle-set)
and [Modrinth](https://modrinth.com/mod/immersive-vehicles-official-content-pack).

## Building from source

Builds are driven by `PackCompiler`:

```
javac PackCompiler.java
java -cp . PackCompiler [mode]
```

| Mode | Output | Toolchain |
| --- | --- | --- |
| *(no argument)* | Forge 1.16.5 jar + 1.12.2 assets jar | JDK 8 (ForgeGradle) |
| `116` | Forge 1.16.5 jar only | JDK 8 (ForgeGradle) |
| `112` | 1.12.2 assets jar only | any JDK |
| `26` / `neo` | NeoForge jar for Minecraft 1.21.1 and 26.1+ | JDK 25+ |

The Forge jar works unchanged on 1.16.5 through 1.20.1, and the NeoForge jar on
1.21.1 and 26.1+ — the per-version release files are copies named for each game
version. Pushing a `V*` tag runs the release workflow, which builds everything
and attaches all per-version jars to a GitHub Release.

## Credits

Pack content by the core Immersive Vehicles team: Don Bruce, Limit, Wolfvanox,
Cactus, Fsendventd, and last, but certainly not least, Mr Mulle.

Questions and bug reports: [MTS Discord](https://discord.gg/KaaSUjm) ·
[issue tracker](https://github.com/DonBruce64/MinecraftTransportSimulator/issues).

License: All rights reserved.

---

*Dedicated to my son **Alex** — six years old and already the best co-pilot
a dad could wish for. May every world you build have something fun to fly.* ✈️ 🚗
*— Gabor*
