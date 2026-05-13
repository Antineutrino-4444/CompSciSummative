# MAB Upgrades — Full Reference (v2)

This document replaces v1. The pool is now organized by **archetype** (the
design layer) while each card still carries a **category** tag (for the
resolver). Forty cards total: 20 carried forward, 4 reworked, 20 new.

> **Scope note.** The intercept tier ladder referenced by a few defense cards
> (`Intercept Crews`, `Penetrator Package`) is still TBD. Their wording is
> preserved as-is until the tier system is specified separately.

---

## Changelog from v1

### Removed (4)
- **Early Warning Radar** — created info asymmetry; the design now keeps both sides on equal information.
- **Threat Tracking** — same reason; the trigger was framed as intel-derived.
- **Signal Analysis** — fired on opponent state visibility.
- **Dead Hand Drill** — mostly flavor on losing (the burst couldn't change the outcome). Replaced by `Dead Hand Protocol` which actually rescues the player from death and fires an uninterceptable counter-strike.

### Reworked (2)
- **Tetris Doctrine** — was *"first Tetris per cycle counts as 2 route pips"* (this collided with `Fast Fuse` at a lower rarity). Now: *"each Tetris awards +6 bonus charge."* Pure charge buff, no route compression.
- **Efficient Reactor** — stack cap reduced from 3 → 2. Three copies in a 24-card pool created draft filler.

### Rarity changes (5)
- **Perfect Clear Battery**: ADVANCED → STANDARD (already trigger-gated by skill).
- **Emergency Protocols**: ADVANCED → STANDARD (one-shot conditional reductions fit STANDARD).
- **Heavy Warhead**: STANDARD → ADVANCED (flat +1 line on every launch is build-defining).
- **Rapid Assembly**: STANDARD → ADVANCED (permanent unconditional loop accelerator).
- **Retaliation Doctrine**: ADVANCED → CRITICAL (the snowball-breaker for a losing player).

### New cards (20)
- Combo Reactor: `Streak Stoker`, `Resonance Chamber`, `Cascade Reactor`
- Spin Specialist: `Wrist Drill`, `Spin Network`
- Tetris Stockpiler: `Wellsmith`
- Turtle: `Bunker`, `Cold Steel`, `Reactive Plating`, `Hardened Silos`, `Dead Hand Protocol`
- Rusher: `Light the Fuse`, `Hair Trigger`, `Glass Cannon`, `Manual Override`
- Wildcards: `Stockpile`, `Adrenaline Sequence`, `Quiet Storm`, `Jam`, `EMP`

---

## How drafts work

- **Trigger:** every level gain on either player. One draft per level.
- **Choices:** exactly 3 cards per draft, sampled deterministically from the
  match seed + participant + level.
- **Rarity weights** (per level tier):

  | Level | STANDARD | ADVANCED | CRITICAL |
  |------:|---------:|---------:|---------:|
  |  ≤ 3  |   85%    |   15%    |    0%    |
  |  ≤ 6  |   65%    |   30%    |    5%    |
  |  ≥ 7  |   50%    |   35%    |   15%    |

- **Stacking:** Most cards are non-repeatable (max 1 owned). `Efficient Reactor`
  is repeatable, now capped at 2 stacks.
- **Filtering:** Cards already at max are removed from the draft pool before
  sampling.
- **Pause:** Both boards pause while the draft overlay is open. AI drafts
  resolve silently in the same tick.
- **Per-card sampling weight:** Each card has a base weight of 1.0 within
  its rarity tier. Specific cards may override this for fine-tuned rarity.
  Currently the only card using this is `Dead Hand Protocol` at 0.5×.
- **Active-card overlays:** Two cards (`Manual Override`, `EMP`) trigger their
  own mid-match pause overlay during play. See the
  [Active-card overlays](#active-card-overlays) section below.

---

## Rarities

| Rarity   | Glyph in icon | Notes                                        |
|----------|---------------|----------------------------------------------|
| STANDARD | tier "I"      | Always available; majority of early drafts   |
| ADVANCED | tier "II"     | Stronger; appears more from level 4 onward   |
| CRITICAL | tier "III"    | Build-defining; only from level 7 onward     |

## Categories

| Category       | Theme                                             |
|----------------|---------------------------------------------------|
| CHARGE         | Faster nuke build via better charge per clear     |
| TETRIS_ROUTE   | Improvements to the Tetris launch route           |
| SPIN_ROUTE     | Improvements to the spin launch route             |
| DEFENSE        | Reduce incoming impact / improve intercepts       |
| POWER          | Make your launches deal more damage               |
| TEMPO          | Charge / launch loops based on combat events      |

(INTEL is retired in v2. No cards depend on information asymmetry.)

---

## Archetypes

Five archetypes, each with its own win condition. A drafted run typically
commits to one and dips into a second. Cards that appear in two archetypes are
called out explicitly.

### Combo Reactor — *sustained pressure*
Keep clearing every piece. Combos and B2B chains are your fuel. You fall
behind the moment you stop clearing.

> **Synergy path:** `Streak Stoker` + `Combo Capacitor` + `B2B Amplifier`
> compounds long clean play into runaway charge. `Cascade Reactor` is the
> closer — clears now buy route progress directly, not just charge.

### Spin Specialist — *rotation everything*
T-spins, mini-spins, rotation tricks. Spin clears feed both your charge and
your launch route. You also intercept naturally because you're already spinning
every piece.

> **Synergy path:** `Spin Doctrine` + `Spin Network` + `Spin Launch Crew`
> creates a self-reinforcing loop — launch, keep a pip, and the next cycle's
> first spin is already buffed by the chain. `Counterspin Training` turns every
> defensive intercept into more offense.

### Tetris Stockpiler — *tall wells, big single launches*
Stack 9-wide, hold the well open, chain Tetrises. Slowest to ramp, biggest
single launches.

> **Synergy path:** `Wellsmith` + `Clean Well Logistics` + `Tetris Doctrine`
> rewards *maintaining* the canonical Tetris setup, not just executing it.
> `Fast Fuse` caps the build by reducing the route.

### Turtle — *convert defense to offense*
Take less damage. Convert what damage you do take into charge. Never top out.

> **Synergy path:** `Bunker` + `Shelters` + `Hardened Silos` absorbs early
> aggression. `Counterspin Training` + `Retaliation Doctrine` + `Reactive
> Plating` converts each impact into momentum. `Dead Hand Protocol` is the
> "if I'm going down, you're going down with me" card — designed to create
> one memorable doomsday moment per match.

### Rusher — *race them to the bottom*
Don't accumulate, dump it. Bigger launches, arriving faster.

> **Synergy path:** `Hair Trigger` + `Heavy Warhead` + `Manual Override` is
> the textbook race build — generate faster, dump faster, hit harder, never
> wait. `Glass Cannon` doubles the commitment: now your only winning move is
> to outpace, because trading is fatal.

### Wildcards — *no archetype*
Five cards that don't anchor a build but win the moment they're drawn. Two of
them (`Jam`, `EMP`) are the only direct opponent-interaction cards in the
pool; without them, every match becomes parallel solitaire.

---

## Cards

Each entry below uses this format:

> **Card Name** — RARITY · CATEGORY
> *Archetype:* X · *Stacks:* N · *Tag:* `effect_tag`
> Short line.
>
> Detail paragraph with concrete mechanics.

---

### Combo Reactor (6 cards)

**Efficient Reactor** — STANDARD · CHARGE
*Archetype:* Combo Reactor · *Stacks:* 1–2 (repeatable, capped at 2) · *Tag:* `charge_clear_bonus_1`
Each scoring clear awards +1 bonus charge per stack.

Stacks additively. One copy = +1, two copies = +2 per clear.

---

**Combo Capacitor** — STANDARD · CHARGE
*Archetype:* Combo Reactor · *Stacks:* 1 · *Tag:* `charge_combo_4plus`
Combos of 4 or higher award +2 extra charge per chained clear.

Threshold is the 4th consecutive clear; bonus applies to that clear and every
subsequent clear while the combo holds.

---

**Streak Stoker** — STANDARD · CHARGE — *new*
*Archetype:* Combo Reactor · *Stacks:* 1 · *Tag:* `charge_streak_stoker`
Snowballing bonus per consecutive clear.

Maintain a streak by clearing on consecutive locks. The Nth consecutive clear
(N starting at 1) awards N bonus charge. Streak resets on any lock that does
not clear a line. Hard piece drops without a clear also break the streak.

---

**B2B Amplifier** — ADVANCED · CHARGE
*Archetype:* Combo Reactor + Tetris Stockpiler · *Stacks:* 1 · *Tag:* `charge_b2b_amplifier`
B2B multiplier improves from 1.25 → 1.40.

Back-to-back difficult clears (Tetrises, all spin clears) multiply their
charge by 1.40 instead of the base 1.25 while the B2B streak holds.

---

**Resonance Chamber** — ADVANCED · CHARGE — *new*
*Archetype:* Combo Reactor · *Stacks:* 1 · *Tag:* `charge_combo_resonance`
Hitting combo length 6 grants +30 charge instantly.

Fires the moment the 6th consecutive clear resolves. One-shot per combo run;
the next eligible fire is after the combo breaks and a new one reaches 6.

---

**Cascade Reactor** — CRITICAL · TEMPO — *new*
*Archetype:* Combo Reactor · *Stacks:* 1 · *Tag:* `tempo_cascade_reactor`
While nuke-ready, every 3rd clear in a combo advances your launch route.

Only fires during nuke-ready phase. Counts clears within an unbroken combo.
Every 3rd clear awards 1 route pip: Tetris clears → T pip, any spin clear →
S pip, other clears → no pip from this card (still counted toward the
"every 3rd" counter). If the relevant route is full, the pip is wasted.

---

### Spin Specialist (6 cards)

**Spin Doctrine** — STANDARD · CHARGE
*Archetype:* Spin Specialist · *Stacks:* 1 · *Tag:* `charge_spin_any`
Every spin clear adds +2 bonus charge.

Applies to all spin-detected clears including 0-line spins (see `Wrist Drill`
for the 0-line interaction).

---

**Wrist Drill** — STANDARD · CHARGE — *new*
*Archetype:* Spin Specialist · *Stacks:* 1 · *Tag:* `charge_spin_zero`
Zero-line spin clears award +1 charge.

A zero-line spin is a piece that locks with the spin-detected flag but clears
no lines. Without this card, 0-line spins award no charge. Stacks with `Spin
Doctrine` so a 0-line spin with both cards is +3 charge.

---

**TST Program** — ADVANCED · CHARGE
*Archetype:* Spin Specialist · *Stacks:* 1 · *Tag:* `charge_spin_triple`
Spin triples (3-line spin clears) award +8 extra charge.

Triggers on T-spin triples and any equivalent 3-line spin (e.g. L/J/S/Z
triples if recognized by the spin detector).

---

**Counterspin Training** — ADVANCED · CHARGE
*Archetype:* Spin Specialist + Turtle · *Stacks:* 1 · *Tag:* `charge_intercept`
A full intercept refunds +10 charge.

Triggers only on full intercepts (threat fully neutralized). Partial
intercepts give no refund.

---

**Spin Network** — ADVANCED · CHARGE — *new*
*Archetype:* Spin Specialist · *Stacks:* 1 · *Tag:* `charge_spin_chain`
Spins within a launch cycle chain bonus charge.

Maintains a counter that resets at the start of each launch cycle (whether the
previous cycle ended in launch, intercept, or top-out). Each spin clear in
the cycle awards bonus charge equal to the number of prior spin clears in
that cycle: 1st spin = +0, 2nd = +1, 3rd = +2, 4th = +3, and so on.

---

**Spin Launch Crew** — CRITICAL · SPIN_ROUTE
*Archetype:* Spin Specialist · *Stacks:* 1 · *Tag:* `route_spin_keep1`
A spin launch retains 1 spin route pip.

After firing a launch via the spin route, the spin pip counter resets to 1
instead of 0. The Tetris route resets normally.

---

### Tetris Stockpiler (6 cards)

**Tetris Doctrine** — STANDARD · CHARGE — *reworked*
*Archetype:* Tetris Stockpiler · *Stacks:* 1 · *Tag:* `charge_tetris_flat`
Each Tetris awards +6 bonus charge.

Applies to all 4-line clears regardless of whether they were spin-detected.
Stacks with `Clean Well Logistics` (which adds +3 only for non-spin Tetrises).

> *v1 mechanic was "first Tetris per cycle counts as 2 route pips," which
> overlapped `Fast Fuse`. This rework decouples them.*

---

**Clean Well Logistics** — STANDARD · CHARGE
*Archetype:* Tetris Stockpiler · *Stacks:* 1 · *Tag:* `charge_clean_tetris`
Non-spin Tetrises award +3 extra charge.

Excludes any Tetris where the spin detector flagged the lock. Pairs with
`Tetris Doctrine` so a clean Tetris with both is +9 charge.

---

**Perfect Clear Battery** — STANDARD · CHARGE — *demoted from ADVANCED*
*Archetype:* Tetris Stockpiler · *Stacks:* 1 · *Tag:* `charge_perfect_clear`
Perfect clears award +8 extra charge.

Triggers on any lock that leaves the board fully empty after clears resolve.

---

**Wellsmith** — ADVANCED · CHARGE — *new*
*Archetype:* Tetris Stockpiler · *Stacks:* 1 · *Tag:* `charge_wellsmith`
Maintaining a Tetris-ready well multiplies all charge gain by 1.20.

A "Tetris-ready well" is defined as a single empty column with depth ≥ 4
relative to its neighbors. Implementation note: scan each column for the
topmost occupied row; a column qualifies as a well when its topmost-occupied
row is at least 4 rows below the topmost-occupied row of *both* adjacent
columns (or the one adjacent column if the well is in column 1 or 10). The
bonus applies continuously while the condition holds; it drops the instant
the well is filled or the neighbors fall behind.

---

**B2B Amplifier** — ADVANCED · CHARGE
*See Combo Reactor.* Shared card.

---

**Fast Fuse** — CRITICAL · TETRIS_ROUTE
*Archetype:* Tetris Stockpiler · *Stacks:* 1 · *Tag:* `route_tetris_minus1`
Tetris route target reduced from 4 → 3.

Once nuke-ready, 3 Tetrises now complete the Tetris route instead of 4.

---

### Turtle (11 cards)

**Shelters** — STANDARD · DEFENSE
*Archetype:* Turtle · *Stacks:* 1 · *Tag:* `defense_shelters`
Impact garbage reduced by 1 line (floor 1).

Each incoming impact deals 1 fewer line, minimum of 1.

---

**Bunker** — STANDARD · DEFENSE — *new*
*Archetype:* Turtle · *Stacks:* 1 · *Tag:* `defense_bunker`
First impact of the match has its line count halved.

Reduces by 50%, rounded up (so a 4-line impact becomes 2, a 3-line becomes 2,
a 5-line becomes 3). One-shot per match. Consumed after first impact applies.

---

**Cold Steel** — STANDARD · DEFENSE — *new*
*Archetype:* Turtle · *Stacks:* 1 · *Tag:* `defense_cold_steel`
While stack is low, intercepts refund extra charge.

Condition: your stack's topmost occupied row is at or below row 8 (i.e., the
top 12 rows are empty). While the condition holds, full intercepts refund an
additional +5 charge on top of any other refund cards.

---

**Intercept Crews** — STANDARD · DEFENSE
*Archetype:* Turtle · *Stacks:* 1 · *Tag:* `defense_intercept_str`
Spin intercept tier improved by 1.

> *Effect depends on the intercept tier ladder, which is TBD. See
> [Design notes](#design-notes) for the open spec.*

---

**Emergency Protocols** — STANDARD · DEFENSE — *demoted from ADVANCED*
*Archetype:* Turtle · *Stacks:* 1 · *Tag:* `defense_emergency`
Once per match, a high-stack impact is softened by 2 lines.

Triggers on the first impact you take while your stack's topmost row is at
or above row 13 (upper third of the board). Reduces line count by 2 (floor 1).
Consumed after first trigger.

---

**Reactive Plating** — ADVANCED · DEFENSE — *new*
*Archetype:* Turtle · *Stacks:* 1 · *Tag:* `defense_reactive_plating`
After taking impact, your intercept window doubles for 6 seconds.

The in-flight window during which a spin lands as an intercept is doubled for
6 seconds after each impact you absorb. Designed to give you breathing room
during a chained attack.

---

**Counterspin Training** — ADVANCED · CHARGE
*See Spin Specialist.* Shared card.

---

**Ablative Spin** — CRITICAL · DEFENSE
*Archetype:* Turtle · *Stacks:* 1 · *Tag:* `defense_ablative_spin`
One intercept covers two clustered threats.

If two incoming threats are in flight within 2 ticks of each other, a single
spin intercept resolves both.

---

**Hardened Silos** — CRITICAL · DEFENSE — *new*
*Archetype:* Turtle · *Stacks:* 1 · *Tag:* `defense_hardened_silos`
Once per match, an impact cannot top you out.

If an impact would push your stack above row 20 (top-out), the impact is
capped at row 20: the stack fills up to the top row exactly, with any extra
garbage discarded. One-shot per match. Consumed on first prevented top-out.

---

**Retaliation Doctrine** — CRITICAL · TEMPO — *promoted from ADVANCED*
*Archetype:* Turtle · *Stacks:* 1 · *Tag:* `tempo_retaliation`
Taking impact awards +20 charge.

Each impact you take (after all reductions from Shelters / Bunker / etc.
resolve) grants you +20 charge. Anti-snowball card for the losing side.

---

**Dead Hand Protocol** — CRITICAL · DEFENSE — *new, replaces v1 Dead Hand Drill*
*Archetype:* Turtle · *Stacks:* 1 · *Tag:* `defense_dead_hand` · *Draft weight:* 0.5× (see note)
Once per match: prevent fatal top-out and auto-fire an uninterceptable counter-launch.

Triggers when an incoming impact would push your stack above row 20. The
card's effects fire in this order:
1. The fatal impact is capped at row 20 — your stack fills to the top exactly
   but does not top out. Any extra garbage lines are discarded.
2. A full-power launch is auto-fired at the opponent.
3. The counter-launch enters the normal in-flight phase visually, but the
   intercept window is disabled for this specific launch. The opponent's
   stage banner displays `DEAD HAND STRIKE` in place of the standard
   `SPIN ANY PIECE TO INTERCEPT` prompt.
4. The card is consumed.

**Draft weight note:** this card has a 0.5× sampling weight relative to other
CRITICAL cards. Mechanically, when sampling 3 cards from the eligible
CRITICAL pool at level 7+, each CRITICAL card has a base weight of 1.0 while
Dead Hand Protocol has 0.5. Implementer should expose this as a `weight`
field on the card definition so other cards can be tuned similarly later.

Does not trigger on top-out from your own stacking — only impact-caused
top-out. If you have less than 1 charge or some other resource gate
applies, ignore it; this card explicitly bypasses normal launch requirements
because it's the "doomsday device" fantasy.

---

### Rusher (8 cards)

**Light the Fuse** — STANDARD · POWER — *new*
*Archetype:* Rusher · *Stacks:* 1 · *Tag:* `power_light_fuse`
First launch of the match deals +1 garbage line.

Single-use, consumed after first launch fires. Stacks with other +line cards.

---

**Heavy Warhead** — ADVANCED · POWER — *promoted from STANDARD*
*Archetype:* Rusher · *Stacks:* 1 · *Tag:* `power_heavy_warhead`
Every launch you fire deals +1 garbage line.

Flat bonus on every launch. Stacks with `Dirty Payload` and `Glass Cannon`.

---

**Penetrator Package** — ADVANCED · POWER
*Archetype:* Rusher · *Stacks:* 1 · *Tag:* `power_penetrator`
Opponent intercepts are one tier less effective against your launches.

> *Effect depends on the intercept tier ladder, which is TBD.*

---

**Dirty Payload** — ADVANCED · POWER
*Archetype:* Rusher · *Stacks:* 1 · *Tag:* `power_dirty_payload`
Each impact you cause adds 1 messy garbage line.

The extra line uses random-hole garbage generation (rather than the standard
single-column garbage). Stacks additively with `Heavy Warhead`.

---

**Rapid Assembly** — ADVANCED · TEMPO — *promoted from STANDARD*
*Archetype:* Rusher · *Stacks:* 1 · *Tag:* `tempo_rapid_assembly`
Firing a launch refunds +10 charge.

Triggered the instant the launch fires (not on impact). Goes toward the next
charge build.

---

**Hair Trigger** — ADVANCED · TEMPO — *new*
*Archetype:* Rusher · *Stacks:* 1 · *Tag:* `tempo_hair_trigger`
Charge gain +25%; bleeds 1/sec when you aren't clearing.

All sources of charge gain are multiplied by 1.25. While you have not cleared
a line in the last 1 second, your charge meter bleeds 1 point per second
until you clear. Bleed stops the instant a clear resolves. Cannot reduce
charge below 0.

---

**Glass Cannon** — ADVANCED · POWER — *new*
*Archetype:* Rusher · *Stacks:* 1 · *Tag:* `power_glass_cannon`
Your launches deal +2 lines; impacts on you deal +1 line.

Both effects are permanent and unconditional while owned. You're committing
to the race: trading damage is mathematically bad for you unless you launch
more often.

---

**Manual Override** — CRITICAL · TEMPO — *new*
*Archetype:* Rusher · *Stacks:* 1 · *Tag:* `tempo_manual_override`
At full charge, fire a half-power launch on demand without completing the
route. See [Active-card overlays](#active-card-overlays).

The first time your charge reaches 100 in each launch cycle, both boards
pause and an overlay appears. See the active-card overlays section for the
full UX flow.

---

### Wildcards (5 cards)

**Quiet Storm** — STANDARD · CHARGE — *new*
*Archetype:* Wildcard · *Stacks:* 1 · *Tag:* `charge_quiet_storm`
After 10 seconds without a clear, your next clear gains +20 charge.

Idle timer resets whenever any line clears. Crossing the 10-second mark arms
the bonus, which fires on the next non-zero clear and then disarms until the
next 10-second idle window.

---

**Stockpile** — ADVANCED · CHARGE — *new*
*Archetype:* Wildcard · *Stacks:* 1 · *Tag:* `charge_stockpile`
Charge cap raised from 100 → 130.

100 is still the nuke-ready threshold. The 100–130 reserve is held passively;
it can be spent by other cards (e.g., `EMP`) or simply preserve charge that
would otherwise cap and waste.

---

**Adrenaline Sequence** — ADVANCED · CHARGE — *new*
*Archetype:* Wildcard · *Stacks:* 1 · *Tag:* `charge_adrenaline`
While stack is dangerously high, charge gain is multiplied by 1.5.

Condition: your stack's topmost occupied row is at or above row 14 (upper
30% of the board). Bonus applies continuously while the condition holds.
Designed to give a losing player a comeback lever.

---

**Jam** — ADVANCED · TEMPO — *new*
*Archetype:* Wildcard · *Stacks:* 1 · *Tag:* `tempo_jam`
When opponent reaches nuke-ready, their launch route gets +1 random pip
for that cycle.

When opponent enters nuke-ready, one of their two routes (Tetris or Spin,
chosen by deterministic match-seeded RNG) has its target raised by 1 for
that launch cycle only. So Tetris route might go 4 → 5 or Spin route 2 → 3.
The increase is visible on their tactical strip. Resets when their next
launch fires.

---

**EMP** — CRITICAL · TEMPO — *new*
*Archetype:* Wildcard · *Stacks:* 1 · *Tag:* `tempo_emp`
Once per match, cancel an opponent's launch by spending 40 charge. See
[Active-card overlays](#active-card-overlays).

Triggers once per match on opponent's launch. Both boards pause and an
overlay appears; the player chooses to cancel or skip. See the active-card
overlays section for the full UX flow.

---

## Active-card overlays

Two cards trigger a pause overlay during play: `Manual Override` and `EMP`.
Both follow the same UX pattern — both boards freeze, an overlay appears
with selectable options, and the player navigates with arrow keys and
confirms with Enter. The programmer should treat this as a separate UI
state, similar to the level-up draft overlay but smaller and ephemeral.

**Common interaction**
- Both boards pause (piece gravity, timers, in-flight threats all freeze).
- Overlay appears centered, styled like the draft overlay but with 2 options
  instead of 3.
- LEFT / RIGHT arrow keys move selection between options.
- ENTER confirms the highlighted option and closes the overlay.
- ESC closes the overlay with the "do nothing" option selected.
- Match resumes the moment the overlay closes.

### Manual Override — overlay spec
- **Trigger:** the first moment in each launch cycle when your charge reaches
  100. Subsequent re-fillings within the same cycle don't re-trigger.
- **Options:**
  - `FIRE NOW (half power)` — launch immediately, dealing base impact divided by 2
    (rounded down). Skips both the Tetris and Spin route requirements.
  - `HOLD (continue route)` — close the overlay and continue playing toward a
    full-power launch via the normal route.
- **Default selection:** `HOLD`.
- **No charge cost.** Card is not consumed; it can trigger every cycle.

### EMP — overlay spec
- **Trigger:** opponent fires a launch (the moment their route completes and
  the in-flight phase begins).
- **Options:**
  - `CANCEL (−40 charge)` — opponent's launch is neutralized, no impact resolves.
    You lose 40 charge. Card is consumed.
  - `SKIP` — let the launch proceed normally. Card is not consumed.
- **Default selection:** `SKIP`.
- **Charge gate:** if you have less than 40 charge, the `CANCEL` option is
  shown disabled (greyed out) and is unselectable; only `SKIP` is available.
- **One-shot:** card is consumed only on successful `CANCEL` confirmation.

---

## Summary table (40 cards)

| Card                  | Archetype          | Category      | Rarity   | Stacks | Effect                                              |
|-----------------------|--------------------|---------------|----------|:------:|------------------------------------------------------|
| Efficient Reactor     | Combo Reactor      | CHARGE        | STANDARD |  1–2   | +1 charge per clear (per stack)                      |
| Combo Capacitor       | Combo Reactor      | CHARGE        | STANDARD |   1    | Combo ≥ 4 adds +2 per chained clear                  |
| Streak Stoker         | Combo Reactor      | CHARGE        | STANDARD |   1    | Nth consecutive clear awards N bonus charge          |
| B2B Amplifier         | Combo + Tetris     | CHARGE        | ADVANCED |   1    | B2B multiplier 1.25 → 1.40                           |
| Resonance Chamber     | Combo Reactor      | CHARGE        | ADVANCED |   1    | Combo length 6 → +30 charge instantly                |
| Cascade Reactor       | Combo Reactor      | TEMPO         | CRITICAL |   1    | While ready, every 3rd combo clear → +1 route pip    |
| Spin Doctrine         | Spin Specialist    | CHARGE        | STANDARD |   1    | Any spin adds +2 charge                              |
| Wrist Drill           | Spin Specialist    | CHARGE        | STANDARD |   1    | 0-line spin clears award +1 charge                   |
| TST Program           | Spin Specialist    | CHARGE        | ADVANCED |   1    | Spin triple +8 charge                                |
| Counterspin Training  | Spin + Turtle      | CHARGE        | ADVANCED |   1    | Full intercept refunds +10 charge                    |
| Spin Network          | Spin Specialist    | CHARGE        | ADVANCED |   1    | Each spin in cycle adds +1 charge to next            |
| Spin Launch Crew      | Spin Specialist    | SPIN_ROUTE    | CRITICAL |   1    | Keep 1 spin pip after spin launch                    |
| Tetris Doctrine       | Tetris Stockpiler  | CHARGE        | STANDARD |   1    | +6 charge per Tetris                                 |
| Clean Well Logistics  | Tetris Stockpiler  | CHARGE        | STANDARD |   1    | Non-spin Tetris +3 charge                            |
| Perfect Clear Battery | Tetris Stockpiler  | CHARGE        | STANDARD |   1    | Perfect clear +8 charge                              |
| Wellsmith             | Tetris Stockpiler  | CHARGE        | ADVANCED |   1    | Tetris-ready well → charge gain ×1.20                |
| Fast Fuse             | Tetris Stockpiler  | TETRIS_ROUTE  | CRITICAL |   1    | Tetris route 4 → 3                                   |
| Shelters              | Turtle             | DEFENSE       | STANDARD |   1    | Impact garbage −1 (floor 1)                          |
| Bunker                | Turtle             | DEFENSE       | STANDARD |   1    | First impact halved                                  |
| Cold Steel            | Turtle             | DEFENSE       | STANDARD |   1    | Low stack → intercept refund +5                      |
| Intercept Crews       | Turtle             | DEFENSE       | STANDARD |   1    | Intercept tier +1 (tier system TBD)                  |
| Emergency Protocols   | Turtle             | DEFENSE       | STANDARD |   1    | Once/match high-stack impact −2                      |
| Reactive Plating      | Turtle             | DEFENSE       | ADVANCED |   1    | After impact, 6s doubled intercept window            |
| Ablative Spin         | Turtle             | DEFENSE       | CRITICAL |   1    | One intercept covers two clustered threats           |
| Hardened Silos        | Turtle             | DEFENSE       | CRITICAL |   1    | Once/match impact cannot top you out                 |
| Retaliation Doctrine  | Turtle             | TEMPO         | CRITICAL |   1    | +20 charge per impact taken                          |
| Dead Hand Protocol    | Turtle             | DEFENSE       | CRITICAL |   1    | Once/match prevent fatal impact + uninterceptable counter-launch  |
| Light the Fuse        | Rusher             | POWER         | STANDARD |   1    | First launch +1 line                                 |
| Heavy Warhead         | Rusher             | POWER         | ADVANCED |   1    | All launches +1 line                                 |
| Penetrator Package    | Rusher             | POWER         | ADVANCED |   1    | Opp intercept −1 tier (tier system TBD)              |
| Dirty Payload         | Rusher             | POWER         | ADVANCED |   1    | +1 messy garbage line                                |
| Rapid Assembly        | Rusher             | TEMPO         | ADVANCED |   1    | +10 charge after firing launch                       |
| Hair Trigger          | Rusher             | TEMPO         | ADVANCED |   1    | Charge +25%; bleeds 1/sec when not clearing          |
| Glass Cannon          | Rusher             | POWER         | ADVANCED |   1    | Your launches +2 lines; impacts on you +1 line       |
| Manual Override       | Rusher             | TEMPO         | CRITICAL |   1    | At full charge, overlay → half-power on-demand fire  |
| Quiet Storm           | Wildcard           | CHARGE        | STANDARD |   1    | 10s idle → next clear +20 charge                     |
| Stockpile             | Wildcard           | CHARGE        | ADVANCED |   1    | Charge cap 100 → 130                                 |
| Adrenaline Sequence   | Wildcard           | CHARGE        | ADVANCED |   1    | Stack ≥ row 14 → charge gain ×1.5                    |
| Jam                   | Wildcard           | TEMPO         | ADVANCED |   1    | Opp reaches ready → +1 random route pip that cycle   |
| EMP                   | Wildcard           | TEMPO         | CRITICAL |   1    | Once/match, overlay → cancel opp launch for 40 charge |

---

## Effect tag glossary (developer reference)

These are the strings on each card that `MabUpgradeEffectResolver` reads.
Renaming a tag silently disables the card.

```
# CHARGE (count: 20)
charge_clear_bonus_1       +1 charge per scoring clear (Efficient Reactor)
charge_combo_4plus         combo 4+ adds +2 per chained clear (Combo Capacitor)
charge_streak_stoker       Nth consecutive clear awards N bonus charge (Streak Stoker)
charge_b2b_amplifier       B2B multiplier 1.25 -> 1.40 (B2B Amplifier)
charge_combo_resonance     combo length 6 awards +30 instant charge (Resonance Chamber)
charge_spin_any            any spin +2 charge (Spin Doctrine)
charge_spin_zero           0-line spin +1 charge (Wrist Drill)
charge_spin_triple         spin triple +8 charge (TST Program)
charge_intercept           full intercept refunds +10 charge (Counterspin Training)
charge_spin_chain          spin chain in cycle adds escalating bonus (Spin Network)
charge_tetris_flat         each Tetris +6 charge (Tetris Doctrine, reworked)
charge_clean_tetris        non-spin Tetris +3 charge (Clean Well Logistics)
charge_perfect_clear       perfect clear +8 charge (Perfect Clear Battery)
charge_wellsmith           Tetris-ready well -> charge gain x1.20 (Wellsmith)
charge_quiet_storm         10s idle -> next clear +20 charge (Quiet Storm)
charge_stockpile           charge cap 100 -> 130 (Stockpile)
charge_adrenaline          stack >= row 14 -> charge gain x1.5 (Adrenaline Sequence)

# TEMPO (count: 7)
tempo_cascade_reactor      every 3rd combo clear advances route while ready (Cascade Reactor)
tempo_rapid_assembly       +10 charge after launch (Rapid Assembly)
tempo_hair_trigger         charge +25%, bleed 1/sec when idle (Hair Trigger)
tempo_retaliation          +20 charge after impact (Retaliation Doctrine)
tempo_manual_override      overlay -> half-power on-demand fire at full charge (Manual Override)
tempo_jam                  opp nuke-ready -> +1 random route pip that cycle (Jam)
tempo_emp                  overlay -> cancel opp launch for 40 charge (EMP)

# DEFENSE (count: 10)
defense_shelters           impact garbage -1 line (floor 1) (Shelters)
defense_bunker             first impact halved (Bunker)
defense_cold_steel         low stack -> intercept refund +5 (Cold Steel)
defense_intercept_str      intercept tier +1 (Intercept Crews)
defense_emergency          once/match high-stack impact -2 (Emergency Protocols)
defense_reactive_plating   6s doubled intercept window after impact (Reactive Plating)
defense_ablative_spin      one intercept covers two clustered threats (Ablative Spin)
defense_hardened_silos     once/match impact cannot top you out (Hardened Silos)
defense_dead_hand          prevents fatal top-out + auto-fires uninterceptable counter-launch (Dead Hand Protocol)

# POWER (count: 5)
power_light_fuse           first launch +1 line (Light the Fuse)
power_heavy_warhead        all launches +1 line (Heavy Warhead)
power_penetrator           opp intercept -1 tier vs you (Penetrator Package)
power_dirty_payload        +1 messy garbage line on impact (Dirty Payload)
power_glass_cannon         your launches +2 lines; impacts on you +1 line (Glass Cannon)

# TETRIS_ROUTE (count: 1)
route_tetris_minus1        Tetris route 4 -> 3 (Fast Fuse)

# SPIN_ROUTE (count: 1)
route_spin_keep1           after spin launch, keep 1 spin pip (Spin Launch Crew)
```

Retired tags (do not reuse):
```
intel_early_warning        (Early Warning Radar — removed)
intel_threat_tracking      (Threat Tracking — removed)
intel_signal_analysis      (Signal Analysis — removed)
tempo_dead_hand            (Dead Hand Drill — replaced by defense_dead_hand)
tetris_doctrine            (Tetris Doctrine v1 — replaced by charge_tetris_flat)
```

---

## Design notes

### Pool size
40 cards is on the larger side for a draft system where each level offers 3
cards. Match length determines how many levels a player typically sees
(likely 5–10), so the player will see 15–30 cards across a match against a
pool of 40 with rarity filtering. That's healthy variance — no two matches
play the same archetype the same way.

If you want a tighter pool of ~30 cards, the most defensible cuts (without
losing identity) would be:
- `Quiet Storm` — generic timer card, weakest of the wildcards
- `Stockpile` — abstract enabler, only matters in combination with other cards
- `Adrenaline Sequence` — overlaps thematically with `Last Stand Protocol`
  and `Retaliation Doctrine` for losing-side comeback
- `Wrist Drill` — niche, 0-line spins are rare in typical play
- `Emergency Protocols` — already feels weak even at STANDARD
- `Light the Fuse` — first-launch one-shot, marginal impact

Cutting all 6 takes the pool to 34. I'd start with `Quiet Storm` and
`Stockpile` for a 38-card pool, see how it feels, then decide.

### Intercept tier ladder — deferred
Three cards depend on the intercept tier system (`Intercept Crews`,
`Penetrator Package`, and indirectly `Ablative Spin`'s "covers two
threats" wording). Suggested concrete spec when you're ready to define it:

- **Tier 0** — Full neutralize. Threat dies completely.
- **Tier 1** — Reduction. Threat's impact line count halved (rounded up).
- **Tier 2** — Glancing. Threat's impact line count −1 (floor 1).
- **Tier 3** — No effect. Intercept fails (e.g., wrong piece, mistimed).

Default intercept tier when both sides have no relevant upgrades: Tier 0
(full neutralize, matching current behavior). `Intercept Crews` shifts you
+1 (i.e., upgrades from Tier 1 → Tier 0 if you somehow lost the default; in
practice this just stays at Tier 0 unless something degrades it).
`Penetrator Package` shifts the opponent's intercept −1 tier against your
launches (Tier 0 → Tier 1 = full intercepts now only halve your damage).

This makes both cards readable: *"I get cleaner intercepts"* / *"My launches
punch through partial defense."*

### Archetype distribution
Card count per archetype after all changes:

| Archetype          | STANDARD | ADVANCED | CRITICAL | Total |
|--------------------|---------:|---------:|---------:|------:|
| Combo Reactor      |    3     |    2     |    1     |   6   |
| Spin Specialist    |    2     |    3     |    1     |   6   |
| Tetris Stockpiler  |    3     |    2     |    1     |   6   |
| Turtle             |    5     |    2     |    4     |  11   |
| Rusher             |    1     |    6     |    1     |   8   |
| Wildcards          |    1     |    3     |    1     |   5   |
| **TOTAL**          |  **15**  |  **18**  |   **8**  | **41* |

\* `B2B Amplifier` and `Counterspin Training` are shared cards counted under
their primary archetype only. With shared duplication the unique count is 40.

**Turtle is heavy on purpose** — defense needs more options because matches
vary in pressure level. Five STANDARD-tier turtle cards means an early
defensive build can actually commit. **Rusher is lean on STANDARDs** (only
`Light the Fuse`) because committing to rush at level 1 is a bad call; the
rusher identity comes online at level 4+.

### Synergy density
Each archetype has at least one explicit 3-card synergy path documented above.
That doesn't mean each archetype has only one route — for example, Turtle has
two clear paths (`mitigate everything` via Bunker/Shelters/Hardened Silos vs
`convert hits to charge` via Counterspin/Retaliation/Reactive Plating). The
explicit paths are guidance for an AI drafter and for designer playtest.

### What this doc doesn't cover
- Specific charge constants for base game mechanics (single = X charge, double
  = Y charge, etc.) — those live in `ScoreSystem`, not here.
- AI drafting heuristics. AI auto-picks one of three offered cards; the AI's
  pick weight should weight cards toward whatever archetype it's already
  committed to. Spec that separately.
- Visual treatment of cards in the draft UI. The Battle Shell mockup shows
  three card slots in the draft overlay; flavor copy / icon / tier glyph
  per card is still TBD.
- The mid-game pause overlays for `Manual Override` and `EMP` need their own
  visual treatment. They should feel lighter than the level-up draft overlay
  (2 options, no description-heavy cards, snap decision).

### Open questions I'd want answered next
1. **Active-card overlay frequency.** `Manual Override` triggers every cycle
   you reach 100 charge. In a long match that's potentially 5–10 pauses. Is
   that acceptable rhythm? If not, the card should auto-resolve after a
   short timer (e.g., 1.5s) and only pause if the player taps a hotkey.
2. **AI handling of `Manual Override` and `EMP`.** The AI doesn't see an
   overlay; it just makes the choice instantly via heuristic. Should the AI's
   choice take a simulated reaction time (~0.3–0.6s) to feel fair to the
   human?
3. **Stack-height conditional cards** (`Cold Steel`, `Adrenaline Sequence`,
   `Emergency Protocols`) all measure stack height differently. Worth
   unifying on a single primitive (`topmost_occupied_row`) so the implementer
   doesn't reimplement detection per card.
4. **Card pool cuts.** Worth playtesting at 40 first, but if pre-launch
   feedback says "the draft has too many forgettable options," the cut list
   above is ready.