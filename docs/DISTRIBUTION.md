# Distribution & Commercial Ops — Perplexion AntiCheat 1.2.0

The full release-day gate list lives in [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md);
this page is the surrounding commercial workflow.

## Git remote and release

```bash
# One-time (replace with your private or public repo URL)
git remote add origin <your-repo-url>
git push -u origin main
git push origin v1.2.0
```

## Release build

1. `mvn clean package` (tests ON, all green) — artifact: `target/Perplexion-1.2.0.jar`
2. SHA256:

   ```bash
   shasum -a 256 target/Perplexion-1.2.0.jar
   ```

3. Create release:

   ```bash
   gh release create v1.2.0 target/Perplexion-1.2.0.jar \
     --title "Perplexion AntiCheat 1.2.0" \
     --notes-file CHANGELOG.md
   ```

4. Attach the checksum to the release notes. After uploading to a marketplace,
   download the artifact back and verify the checksum matches.

## Release gates (must all pass — details in RELEASE_CHECKLIST.md)

- [ ] `mvn clean package` green with tests on (460+ tests)
- [ ] Jar contents audit clean
- [ ] [STAGING_TEST_CHECKLIST.md](STAGING_TEST_CHECKLIST.md) signed off in
      [staging-results.md](staging-results.md) — including the 1.2.0
      PrismAutoClick shadow-soak
- [ ] [performance-benchmark.md](performance-benchmark.md) has a recorded run
      before any performance numbers appear in a listing
- [ ] Default config ships `safety-mode: banwave`, balanced profile values,
      placeholder appeal URL documented

## Licensing paths

| Path | Config |
|------|--------|
| **Marketplace** (Polymart / BuiltByBit) | `license.enabled: false` — platform validates buyers |
| **Direct sales** | `license.enabled: true`, `license.key`, optional `validation-url` |

Grace period: `license.grace-hours` (default 24h) before checks disable on an
invalid key. License scope is **per-network** — definitions, transfers, and
leak handling in [LICENSE_POLICY.md](LICENSE_POLICY.md). Ship
[TERMS_OF_SERVICE.md](TERMS_OF_SERVICE.md) and [REFUND_POLICY.md](REFUND_POLICY.md)
with every channel.

## Update channel

```yaml
updates:
  check-enabled: true
  manifest-url: 'https://your-cdn.example/perplexion/manifest.json'
```

`/perplexion status` shows current vs latest. No auto-download.

## Support channel

- Discord / ticket URL: add to README and [SUPPORT.md](SUPPORT.md) when live.
- FP flow: exportdebug → advisor → tune/shadow → report
  (see SUPPORT.md and [FALSE_POSITIVE_REPORT_TEMPLATE.md](FALSE_POSITIVE_REPORT_TEMPLATE.md)).

## Marketplace listing

Full template with binding honesty rules: [MARKETPLACE.md](MARKETPLACE.md).
