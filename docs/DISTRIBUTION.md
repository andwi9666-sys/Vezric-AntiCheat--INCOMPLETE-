# Distribution & Commercial Ops

## Git remote and release

```bash
# One-time (replace with your private or public repo URL)
git remote add origin <your-repo-url>
git push -u origin main
git push origin v1.0.0-hardened
git push origin v1.1.0
```

## GitHub Release checklist

1. `mvn clean package` — artifact: `target/VezAntiCheat-1.1.0.jar`
2. SHA256:

   ```bash
   shasum -a 256 target/VezAntiCheat-1.1.0.jar
   ```

3. Create release:

   ```bash
   gh release create v1.1.0 target/VezAntiCheat-1.1.0.jar \
     --title "VezAntiCheat 1.1.0" \
     --notes-file CHANGELOG.md
   ```

4. Attach checksum to release notes.

## Licensing paths

| Path | Config |
|------|--------|
| **Marketplace** (Polymart / BuiltByBit) | `license.enabled: false` — platform handles keys |
| **Direct sales** | `license.enabled: true`, `license.key`, optional `validation-url` |

Grace period: `license.grace-hours` (default 24h) before checks disable on invalid key.

## Update channel

```yaml
updates:
  check-enabled: true
  manifest-url: 'https://your-cdn.example/vezac/manifest.json'
```

`/vez status` shows current vs latest. No auto-download.

## Support channel

- Discord / ticket URL: add to README and `docs/SUPPORT.md` when live.
- FP flow: profile → shadow → `/vez tune` → trace export (see SUPPORT.md).

## Marketplace listing

Full template: [MARKETPLACE.md](MARKETPLACE.md) — feature bullets, screenshots list, comparison table, dual-channel licensing.

## Publish checklist (operator)

- [ ] Git remote configured; `v1.1.0` pushed
- [ ] GitHub Release with jar + SHA256
- [ ] Staging 12/12 aggressive documented in [staging-results.md](staging-results.md)
- [ ] FP soak P0=0 documented
- [ ] [performance-benchmark.md](performance-benchmark.md) filled with real numbers
- [ ] Marketplace listing live with screenshots
- [ ] Direct-sales license endpoint configured (if applicable)
