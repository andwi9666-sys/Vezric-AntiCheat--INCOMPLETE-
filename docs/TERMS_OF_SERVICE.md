# Terms of Service — Perplexion AntiCheat

**Effective date:** 2026-06-10
**Applies to:** Perplexion AntiCheat 1.2.0 and later, purchased via Polymart, BuiltByBit, or the seller's private store.

By purchasing, downloading, installing, or running Perplexion AntiCheat ("the Software"), you ("the Buyer") agree to these Terms of Service ("Terms"). If you do not agree, do not purchase or use the Software.

---

## 1. Definitions

- **Seller** — the developer and rights holder of Perplexion AntiCheat.
- **Software** — the Perplexion AntiCheat plugin (`Perplexion-1.2.0.jar` and successor builds), its bundled configuration files, and its documentation.
- **Network** — a group of Minecraft servers (lobby, game, test, and staging servers) operated under common ownership and branding, as defined precisely in `LICENSE_POLICY.md`.
- **Marketplace** — the platform through which the purchase was made (Polymart, BuiltByBit, or the seller's private store).
- **Supported configuration** — Minecraft 1.8.8 Spigot/Paper, Java 8, with PacketEvents 2.12.x installed.

## 2. License Grant

2.1. Upon completed purchase, the Seller grants the Buyer a non-exclusive, non-transferable, revocable license to install and run the Software on **one (1) Network**, per the per-network terms defined in `LICENSE_POLICY.md`. That document is incorporated into these Terms by reference.

2.2. The license covers the purchased version and any updates the Seller chooses to provide (see Section 8).

2.3. All rights not expressly granted are reserved by the Seller. The purchase conveys a license to use the Software, not ownership of it.

## 3. Prohibited Acts

The Buyer must not, and must not permit any third party to:

3.1. **Redistribute** the Software, in whole or in part, in original or modified form, whether for free or for payment.

3.2. **Leak** the Software, including uploading it to public or private file shares, "cracked plugin" sites, or plugin archives.

3.3. **Resell** the Software or include it in any paid product, server setup, or bundle sold to others.

3.4. **Share license keys, marketplace accounts, or jar files** with anyone outside the licensed Network's operating staff.

3.5. **Claim ownership or authorship** of the Software or remove/alter its branding, attribution, or license notices.

3.6. **Decompile, disassemble, or reverse engineer** the Software, except to the extent such restriction is prohibited by applicable law.

Violation of this Section terminates the license immediately and forfeits all support and update access, without refund.

## 4. Chargebacks and Payment Fraud

4.1. Initiating a chargeback, payment dispute, or payment reversal for a delivered copy of the Software, without first contacting support as described in `REFUND_POLICY.md`, is treated as payment fraud and a material breach of these Terms.

4.2. Upon a chargeback, the Buyer's license, support access, and update access are **revoked immediately**. The Seller may also report the account to the relevant Marketplace.

## 5. Support Scope and Limits

5.1. Support is provided on a **best-effort basis** via the seller's listed support channel (see `SUPPORT.md`). No response-time guarantee is given.

5.2. Support covers the **documented supported configuration only**: Minecraft 1.8.8 Spigot/Paper with PacketEvents 2.12.x. Issues arising on other Minecraft versions, forks, Java versions, or with undocumented third-party plugins are out of scope, though the Seller may assist at their discretion.

5.3. False-positive reports must include the output of `/perplexion exportdebug` for the affected player, using the format in `FALSE_POSITIVE_REPORT_TEMPLATE.md`. Reports without this information may be closed as incomplete.

5.4. Support access may be revoked for abuse of the support channel or any violation of these Terms.

## 6. No Guarantee of Detection

6.1. The Software is designed to **reduce cheating** on the licensed Network. It does not and cannot detect every cheat, cheat client, or cheating technique, and no such guarantee is made or implied.

6.2. Cheat software evolves continuously. The presence of undetected cheating on the Buyer's Network is not a defect in the Software and is not grounds for a refund (see `REFUND_POLICY.md`).

## 7. False Positives and Operator Responsibility

7.1. No anticheat is free of false positives. The Software ships with safety mechanisms (the `banwave` default safety mode, evidence scoring, delayed punishments, the lag gate, and the `vez.bypass` permission) designed to limit the impact of incorrect detections.

7.2. **The Buyer must complete `STAGING_TEST_CHECKLIST.md` on a staging server before enabling punishments in production.** This is a condition of safe operation, not an optional recommendation.

7.3. The Buyer is solely responsible for punishment decisions issued on their Network, including the choice of profile (`lenient`, `balanced`, `aggressive` — see `PROFILE_RECOMMENDATIONS.md`), safety mode, and any custom threshold edits.

7.4. The Seller is **not liable for player bans or other punishments** issued while `aggressive` profile settings or the `instant` safety mode were enabled by the Buyer without first completing the staging checklist. The `instant` mode is intentionally never a shipped default and requires the Buyer's own staging sign-off.

## 8. Updates

8.1. Updates, fixes, and new features are provided **at the Seller's discretion**. No update schedule, feature roadmap, or indefinite maintenance commitment is promised.

8.2. Update access is tied to a license in good standing and may be withdrawn under Sections 3 and 4.

## 9. Disclaimer of Warranty and Limitation of Liability

9.1. The Software is provided **"as is" and "as available"**, without warranty of any kind, express or implied, including warranties of merchantability, fitness for a particular purpose, and non-infringement, except where such disclaimers are not permitted by applicable law.

9.2. To the maximum extent permitted by applicable law, the Seller's total aggregate liability arising out of or relating to the Software is **limited to the amount the Buyer paid for the license**. The Seller is not liable for indirect, incidental, consequential, or special damages, including lost revenue, lost players, or reputational harm.

9.3. Nothing in these Terms limits liability that cannot be limited under applicable law.

## 10. Refunds

Refunds are governed by `REFUND_POLICY.md`, and by the rules of the Marketplace where the purchase was made, which take precedence where applicable.

## 11. Severability

If any provision of these Terms is found unenforceable, that provision is modified to the minimum extent necessary to make it enforceable, and the remaining provisions remain in full effect.

## 12. Changes to These Terms

The Seller may update these Terms from time to time. The current version is published alongside the Software and on the Marketplace listing. Continued use of the Software after an update constitutes acceptance of the revised Terms. Material changes will be noted in the changelog of the release that introduces them.

## 13. Contact

Questions about these Terms, licensing, or transfers should be directed to the seller's listed support channel (see `SUPPORT.md`).
