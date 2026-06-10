# Compatibility

## Required

| Plugin | Role |
|--------|------|
| **PacketEvents** | Netty packet interception (hard depend) |

## Tested soft dependencies

| Plugin | Behavior |
|--------|----------|
| **Vulcan** | Auto-coexistence via `integrations.vulcan` — defers conflicting hooks, optional transaction disable |
| **ViaVersion** | Supported; use lag gates + lenient profile for high effective ping |
| **Geyser-Spigot** | Supported with lenient profile; Bedrock clients have higher jitter |
| **ProtocolLib** | Softdepend; PacketEvents is primary — do not double-inject |
| **ProtocolSupport** | Listed softdepend; test on your fork before production |

## Not recommended

- Running two full anticheats with overlapping movement/combat hooks on the same priority without coexistence config.
- Bukkit `/reload` — use `/vez reload`.

## Version matrix

| VezAntiCheat | Minecraft | Notes |
|--------------|-----------|-------|
| 1.1.0 | 1.8.8 | Primary target |
| 1.0.0-hardened | 1.8.8 | Hardening baseline tag |

Higher Minecraft versions are not supported in this release line.
