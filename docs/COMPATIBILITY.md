# Compatibility — Perplexion AntiCheat 1.2.0

## Required

| Plugin | Role |
|--------|------|
| **PacketEvents 2.12.x** | Netty packet interception (hard depend — install as a separate plugin) |

## Tested soft dependencies

| Plugin | Behavior |
|--------|----------|
| **ViaVersion / ViaBackwards / ViaRewind** | Supported on 1.8.8 servers. The 1.2.0 compat layer reads the client protocol version through the ViaVersion API (reflection, no hard dependency) — surfaced in `/perplexion info` and exportdebug for diagnostics. |
| **Geyser-Spigot / Floodgate** | Supported with the 1.2.0 Bedrock layer (`compat.bedrock`): Bedrock players are detected (Floodgate API → UUID convention → client brand) and automatically get aim-check exemption, scaled scaffold buffers, and a reach margin. Honest caveat: there is **no dedicated Bedrock movement simulation** — movement checks assume Java physics, so Geyser-heavy networks should run the `lenient` profile. |
| **Vulcan** | Auto-coexistence via `integrations.vulcan` — defers conflicting hooks, optional transaction disable. Do not run both in punishing mode simultaneously. |
| **ProtocolLib** | Softdepend; PacketEvents is the packet authority — do not double-inject. |
| **ProtocolSupport** | Listed softdepend; test on your fork in staging before production. |

All `compat.*` adjustments only ever exempt or loosen — a misdetected client can
cause a missed flag, never a false one.

## Not recommended

- Two full anticheats both punishing on the same server. Coexistence mode keeps
  hooks stable, but conflicting setbacks/punishments are an operator problem.
- Bukkit `/reload` or PlugMan reloads — packet hooks cannot re-inject. Use
  `/perplexion reload` for config, full restart for the jar.

## Version matrix

| Perplexion | Minecraft server | Notes |
|------------|------------------|-------|
| 1.2.0 | 1.8.8 | Current — rebrand + thread-safety + compat layer |
| 1.1.x (as VezAntiCheat) | 1.8.8 | Previous line; upgrade notes in INSTALL.md §4 |
| 1.0.0-hardened | 1.8.8 | Hardening baseline tag |

Newer Minecraft **server** versions are not supported in this release line.
Newer **clients** connecting through ViaVersion to a 1.8.8 server are supported.
