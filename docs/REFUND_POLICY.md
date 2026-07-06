# Refund Policy — Perplexion AntiCheat

**Effective date:** 2026-06-10
**Applies to:** Perplexion AntiCheat purchased via Polymart, BuiltByBit, or the seller's private store.

Perplexion AntiCheat is a digital good delivered by download. This policy explains when refunds are and are not available. It is incorporated into `TERMS_OF_SERVICE.md` by reference.

---

## 1. Marketplace Rules Take Precedence

Purchases made through Polymart or BuiltByBit are also governed by that platform's own purchase and dispute rules. **Where a marketplace's rules conflict with this policy, the marketplace's rules apply** to purchases made there. Purchases from the seller's private store are governed by this policy directly, subject to any consumer rights that applicable law grants and that cannot be waived.

## 2. General Rule: No Refunds After Download

Because the Software cannot be "returned" once downloaded, **all sales are final after download**, with the single exception described in Section 3.

## 3. The Exception: Verified Non-Functionality on a Supported Setup

A refund will be issued if **all** of the following are true:

1. The Software is non-functional (fails to load, or a core advertised feature does not operate) on the **documented supported configuration**: Minecraft 1.8.8 Spigot/Paper, Java 8, with PacketEvents 2.12.x installed as a separate plugin.
2. You submit a **complete report** (Section 4) to the seller's listed support channel.
3. Support cannot resolve the issue within **14 days** of receiving the complete report.

The 14-day window starts when the report is complete, not when it is first opened.

## 4. What a Complete Report Includes

- Full server logs from a startup that reproduces the problem (`logs/latest.log`, plus any stack traces).
- The output of `/perplexion status` (or a note that the command itself fails, with the resulting console error).
- Exact versions: server software and build (Spigot/Paper 1.8.8 build), Java version, PacketEvents version, and Perplexion version.
- A list of other installed plugins.
- A short description of what you did and what happened instead.

For detection or false-positive issues, also attach the `/perplexion exportdebug <player>` output as described in `FALSE_POSITIVE_REPORT_TEMPLATE.md`.

## 5. Explicitly Not Refundable

The following are **not** defects and do not qualify for a refund:

- **"It caught fewer cheaters than expected."** The Software is designed to reduce cheating; detection of every cheat is not guaranteed (see `TERMS_OF_SERVICE.md`, Section 6).
- **False positives after skipping the staging checklist or editing thresholds.** Punishment outcomes caused by enabling punishments without completing `STAGING_TEST_CHECKLIST.md`, by enabling the `aggressive` profile or `instant` safety mode without staging sign-off, or by custom edits to check thresholds, are the operator's responsibility (see `PROFILE_RECOMMENDATIONS.md` for the recommended rollout).
- **Incompatibility with undocumented versions or plugins.** Only Minecraft 1.8.8 Spigot/Paper with PacketEvents 2.12.x is supported. Other Minecraft versions, forks, hybrid servers, or conflicts with plugins not listed in `COMPATIBILITY.md` are out of scope.
- Change of mind, accidental purchase after download, or purchase of the wrong product.
- Issues already fixed in an available update the buyer has not installed.

## 6. Chargebacks

**Contact support before disputing a payment.** Filing a chargeback or payment dispute without first contacting support and allowing the process in Sections 3–4 to run is a violation of `TERMS_OF_SERVICE.md` (Section 4) and results in immediate revocation of the license, support access, and update access.

## 7. How to Request a Refund

1. Open a request through the seller's listed support channel (see `SUPPORT.md`), or through the marketplace's resource discussion/ticket system where required by that platform.
2. Include the complete report described in Section 4 and your order/transaction reference.
3. Support will confirm receipt, attempt to resolve the issue, and — if Section 3's conditions are met — process the refund through the original payment platform.
