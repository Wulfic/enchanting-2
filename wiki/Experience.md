# Experience

**From level 30 on, every level costs the same 112 XP points**, vanilla's cost of going from 30 to 31. Below 30 the
curve is vanilla's. There is no level cap.

| Level | Vanilla: points for the next level | Enchanting 2 |
|---|---|---|
| 0–15 | 2L + 7 | same |
| 16–29 | 5L − 38 | same |
| 30 | 112 | 112 |
| 50 | 292 | 112 |
| 99 | 733 | 112 |

Total points to reach a level past 30 = `1395 + 112 × (level − 30)`. Level 50 takes 3,635 points instead of vanilla's
5,345; level 100 takes 9,235 instead of 30,970.

Why: with no 40-level anvil cap and every enchantment on one item, big enchants cost big levels. A flat curve makes
level 60 reachable without making level 30 cheaper.

Details that hold everywhere:
- `/xp add`, `/xp set` and `/xp query` work on the new curve, at any level.
- The level never wraps negative and the XP bar never disappears at absurd levels (vanilla's own overflow is fixed).
- Dying drops XP as in vanilla (at most 100 points).

**Config** (`common.json5` → `experience`): `enabled` (false = vanilla curve everywhere), `flatten_after_level`
(default 30; 0 = flat from level 0), `fixed_points_per_level` (default 112). See [Configuration](Configuration).
