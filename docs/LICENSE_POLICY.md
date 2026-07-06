# License Policy — Perplexion AntiCheat

**Effective date:** 2026-06-10 · Incorporated by reference into `TERMS_OF_SERVICE.md`.

Perplexion AntiCheat is sold under a **per-network license**. This document
defines exactly what that means so there is no ambiguity at purchase time.

## What one license covers

One purchase = one **Network**: the set of Minecraft servers operated under
common ownership and a common player community/brand. Within that Network, the
license covers — at no extra cost and with no server-count limit:

- all production servers (lobby, game modes, proxies' backend servers),
- all **test, staging, and development servers** for that Network,
- moving the plugin between machines/hosts as the Network migrates.

Examples that are **one** license:
- A BungeeCord/Velocity network with a hub and twelve game servers.
- A single standalone SMP plus its private staging copy.
- The same community rebranding or changing hosts.

## What requires a second license

- A second, distinct community or brand — even with the same owner. Two networks
  with separate playerbases are two licenses.
- **Hosting or operating servers for third parties** (server-setup services,
  managed hosting, "we install plugins for you" offerings). Each client network
  needs its own license purchased by or for that client.
- Including the plugin in any product, setup, or bundle delivered to someone
  outside your Network's operating staff (also prohibited by the ToS).

## Transfers

The license is non-transferable **except** with the seller's written consent —
the intended case being the sale of the entire Network to a new owner. Contact
support before the transfer; unauthorized transfers void the license.

## License keys and marketplace validation

- Marketplace purchases (Polymart/BuiltByBit) are validated by the platform's
  own buyer system. For these builds, the optional in-plugin license gate stays
  **disabled** (`license.enabled: false`, the shipped default) — there is no
  phone-home in marketplace builds.
- Direct-store purchases may receive a license key for the optional gate
  (`license.enabled: true`, `license.key`, `license.validation-url`), with a
  configured grace window (`license.grace-hours`, default 24) so a validation
  outage never takes your anticheat down mid-day.
- Keys identify the buyer. Sharing a key, or the jar, outside your Network's
  staff is a ToS violation (Sections 3.2–3.4).

## Leaked copies

Leaked or "cracked" copies are unlicensed regardless of how they were obtained,
receive no support or updates, and may contain tampered code — a real risk for a
plugin that can ban your players. Leaks traced to a buyer's account result in
permanent license revocation under the ToS.

## Questions

If your setup doesn't clearly fit these definitions (e.g. partner networks,
shared lobbies), ask before buying — edge cases are usually resolvable with a
simple written note from the seller.
