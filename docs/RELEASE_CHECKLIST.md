# Release Checklist — Perplexion AntiCheat

Every box must be checked before a build is uploaded to a marketplace or
delivered to a direct buyer. Items marked **[BLOCKER]** stop the release.

## 1. Build and tests

- [ ] **[BLOCKER]** `mvn clean package` (tests ON) is green — all unit tests pass
- [ ] Jar produced as `Perplexion-<version>.jar` (artifactId, not a renamed file)
- [ ] **[BLOCKER]** Jar contents audit (`jar tf target/Perplexion-<version>.jar`):
      no test classes, no `.DS_Store`, resources limited to `plugin.yml`,
      `config.yml`, `checks.yml.legacy`, `tiers/*`, `config-profiles/*`

## 2. Version consistency

- [ ] `pom.xml` version == CHANGELOG.md top entry == marketplace listing version
- [ ] `plugin.yml` version interpolates from the pom (`${project.version}`)
- [ ] `config-version` in `config.yml` matches the constant in `VezAntiCheat.java`
      (`currentConfigVersion`), bumped if any config keys changed
- [ ] Doc sweep returns only historical changelog entries:
      `grep -rn "1\.1\.0\|1\.1\.1\|VezAntiCheat\|WatchDog\|VezAC" docs/ README.md`

## 3. Code gates

- [ ] **[BLOCKER]** Java 8 conformance:
      `grep -rEn "List\.of\(|Map\.of\(|Set\.of\(|String\.isBlank|Optional\.isEmpty|\.toList\(\)" src/main/java` is empty
- [ ] **[BLOCKER]** Off-main world access:
      `grep -rn "getTargetBlock|getEntities()|getNearbyEntities" src/main/java`
      hits only `EntityIndex` and provably main-thread call sites
      (currently: `CombatUtil.resolveTarget`'s guarded primary-thread fallback)
- [ ] No new `Thread`/`ExecutorService` outside the Bukkit scheduler

## 4. Default-config safety

- [ ] **[BLOCKER]** `punish.safety-mode: banwave` in `config.yml` (never `instant`)
- [ ] Default profile values match `balanced` (`config.yml` is the balanced baseline)
- [ ] All three `config-profiles/*.yml` parse as YAML and contain
      `punish.safety-mode`, `exempt.ping-scaling`, and `compat.bedrock` keys
      (guarded by `ConfigProfileKeysTest` — green tests imply this)
- [ ] `punish.announcements.appeal-url` is the documented placeholder, and
      BUYER_SETUP_GUIDE tells buyers to change it
- [ ] No debug spam defaults: `diagnostics.perf-sampling-enabled: false`,
      `velocity-engine.debug: false`, `combat-analysis.alerts.debug: false`
- [ ] No hardcoded private info (tokens, webhooks, personal URLs) in src or resources

## 5. Staging evidence **[BLOCKER for first release of a version line]**

- [ ] `docs/STAGING_TEST_CHECKLIST.md` executed on a real 1.8.8 staging server,
      results recorded in `docs/staging-results.md` with sign-off
- [ ] 1.2.0-specific: PrismAutoClick A/B/C shadow-soak completed (dig-packet
      mining reclassification) before autoclicker punishments are trusted
- [ ] `docs/performance-benchmark.md` has at least one recorded run **before any
      performance number appears in the listing** (structural claims only until then)

## 6. Marketplace listing

- [ ] PacketEvents 2.12.x stated as a required separate plugin
- [ ] Supported version stated plainly: Minecraft 1.8.8 Spigot/Paper only
- [ ] License model stated: per-network (link LICENSE_POLICY.md)
- [ ] ToS + refund policy linked or pasted per platform rules
- [ ] Honest-claims check: no "unbypassable", no "0% false positives", no
      unmeasured performance numbers
- [ ] Screenshots current for this version: `/perplexion status`, `/flags` GUI,
      `/perplexion recommendations`, `/perplexion checks`
- [ ] Description proofread; feature list matches this version's CHANGELOG

## 7. Post-upload

- [ ] Download the uploaded jar back and `sha256sum` it against the local build
- [ ] Fresh-server smoke test of the uploaded artifact: drops in, enables, banner
      shows correct version + `safetyMode=BANWAVE`
- [ ] Tag the release in git; archive the exact jar + configs
