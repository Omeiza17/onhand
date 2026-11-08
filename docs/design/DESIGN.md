# Onhand — UI design (source of truth for the frontend)

Agreed design for the MVP screens. Build the PWA to match this document.
`reference/*.dc.html` are the original mockups (each file shows the screen in light and dark side by side): read them for exact layout,
spacing and copy, but **do not copy their code** — they use a mockup runtime
(`<x-dc>`, `<sc-for>`, `<sc-if>`, `{{holes}}`, `DCLogic`) that does not exist in
this app. Re-implement in the app's own framework.

If `docs/design/screens/*.png` exist, they are screenshots of the same
mockups — use them to check visual fidelity.

Where this document and `docs/SPEC.md` disagree on *behaviour or data*, the
spec wins. Where they disagree on *look*, this document wins.

## Look

Neutral and utilitarian: off-white ground, white cards, one accent.
No gradients, no emoji, no left-border cards.

### Tokens

Every mockup shows light and dark side by side. Implement both with CSS custom
properties; dark applies under `prefers-color-scheme: dark`.

| Token | Light | Dark | Use |
|---|---|---|---|
| `--bg` | `#F5F6F4` | `#0F1113` | page ground |
| `--surface` | `#FFFFFF` | `#181B1F` | cards, inputs, tab bar, sheet |
| `--ink` | `#15181C` | `#ECEEEF` | primary text |
| `--muted` | `#5B616B` | `#A3A9B1` | secondary text, section headers, inactive tab |
| `--line` | `#E4E6E3` | `#2A2F35` | card and input borders |
| `--line-soft` | `#EEF0ED` | `#22262B` | row dividers inside cards |
| `--accent` | `#0E6B5E` | `#3FB8A4` | primary buttons, active tab, + button, checkbox |
| `--on-accent` | `#FFFFFF` | `#06201C` | text/icons on accent fills |
| `--sel-bg` / `--sel-ink` | `#15181C` / `#FFFFFF` | `#ECEEEF` / `#15181C` | selected chip |
| `--have-bg` / `--have-bd` / `--have-ink` | `#E6F2EF` / `#CFE5E0` / `#0B5148` | `#123530` / `#1E4A43` / `#8EE0D2` | "Have" pill |
| `--low-bg` / `--low-bd` / `--low-ink` | `#FDF0D5` / `#F3DDB0` / `#8A4B08` | `#3A2A10` / `#5A4218` / `#F5C77A` | "Low" pill, "On list" text |
| `--out-bd` / `--out-ink` | `#9AA0A8` / `#4A5059` | `#6B727B` / `#C2C7CD` | "Out" pill — surface fill, 1.5px dashed border |
| `--toast-bg` / `--toast-ink` | `#15181C` / `#FFFFFF` | `#ECEEEF` / `#15181C` | undo toast, put-away card (inverted in dark) |
| `--toast-action` | `#8EE0D2` | `#0B5148` | "Undo" text on the toast |
| `--badge` / `--badge-ink` | `#8A4B08` / `#FFFFFF` | `#F5C77A` / `#2A1C05` | Shopping tab count |
| `--scrim` | `rgba(21,24,28,.45)` | `rgba(0,0,0,.6)` | behind the update sheet |

Status pills must stay distinguishable without colour: Have = filled,
Low = filled warm, Out = dashed outline. Keep that in both themes.

### Type

- Body/UI: **Inter** 400/500/600. Headings and the wordmark: **Space Grotesk** 500/600.
  Self-host or load from Google Fonts — decide and say which.
- Page title 30px/600 Space Grotesk, letter-spacing −0.01em.
- Wordmark "ONHAND" 14px/600 Space Grotesk, letter-spacing 0.04em, muted.
- Section header 13px/600, uppercase, letter-spacing 0.06em, muted.
- Row name 16px/500; row secondary 13px muted. Quantities use tabular numbers.
- Inputs 16px minimum (prevents iOS zoom on focus).

### Shape and spacing

- Screen side padding 16px (headers 20px).
- Cards: radius 16px, 1px `--line` border. Inputs: radius 12px, height 48–52px.
- Pills and round buttons fully rounded.
- **Every tap target ≥ 44×44px.** Rows ≥ 60–68px tall.
- Bottom tab bar 84px tall (includes bottom safe-area; use `env(safe-area-inset-bottom)`).

## Shell

- Bottom tab bar on every main screen: **At home** (house icon) · centre round
  **+** button (56px, accent, goes to Add product) · **Shopping** (list icon)
  with a count badge (items to buy).
- Active tab is accent-coloured; inactive is muted.
- Icons: inline stroke SVG, 2px stroke, round caps (or a small stroke icon set
  such as Lucide — ask before adding a dependency).
- Phone-first. On wide screens, centre the app in a column with
  `max-width: 480px` — no separate desktop layout in MVP.

## Screen 1 — At home (`reference/Main.dc.html`)

- Header: wordmark, title "What's at home", subtitle "{n} items in {m} places".
- Search field (magnifier icon, placeholder "Search rice, eggs, dish soap…"),
  filters live as you type.
- **Filter: All · Food · Household** (Product `kind`) — row of three 40px chips
  directly under search; selected = `--sel-bg` fill.
- List grouped by location (Pantry, Fridge, Freezer, Under the sink, Bathroom…);
  section header "PANTRY · 4". Each location is a surface card with rows
  separated by `--line-soft`. Empty locations are hidden (also after filtering).
- Row: name (+ category below, muted). **The whole name/category area is a link
  to Edit product** (full row height, no visible link styling). Right side
  depends on tracking mode:
  - COUNTED: `[−]  485 g  [+]` — round 44px outline buttons move by the product's
    `step_size`. **The amount itself is a button** (dotted underline) that opens
    the update sheet. Below `min_quantity`, the secondary line adds "· On list"
    in `--low-ink`.
  - COUNTED **at 0**: the amount button shows **"Out"** styled like the Out pill
    (surface fill, 1.5px dashed `--out-bd`, `--out-ink` text, 44px tall, fully
    rounded) instead of the dotted-underline number; − stays disabled, + still
    adds one step, tapping "Out" opens the update sheet on **Now I have**.
    Secondary line adds "· On list". aria-label: "{Name}: out. Set amount".
  - STATUS: one pill button (Have / Low / Out) that cycles HAVE → LOW → OUT → HAVE on tap.

### Update sheet (tap the amount on a counted row)

Bottom sheet over `--scrim` (tap scrim = close), radius 20px top corners, grab handle.
- Title: product name; subtitle "{current} · {location}".
- Two chips: **I used** (default, input empty) / **Now I have** (input prefilled
  with current amount).
- Large decimal input (56px, 2px ink border, `inputmode="decimal"`, `,` or `.`),
  unit as suffix.
- "I used" only: quick chips — 1×step, 2×step, Half, All of it (piece/pack/tin:
  1, 2, Half, All of it).
- Live line: "Result: 485 g → 285 g".
- Weighed units only (g/kg/ml/l): "− / + buttons move by" with step chips
  (g: 50/100/250/500; kg: 0.1/0.25/0.5/1; ml: 100/250/500; l: 0.25/0.5/1).
  Picking one saves the product's `step_size` immediately.
- Buttons: Cancel (outline, 1/3) · **Update** (accent, 2/3). Update closes the
  sheet and shows the normal undo toast.
- Use `role="dialog"`, move focus into the sheet on open, return it on close,
  Escape closes.
- **Every change shows an undo toast**: dark card above the tab bar,
  "{Name}: {old} → {new}", Undo action on the right, auto-hides after ~4s.
  Undo calls the backend's undo endpoint. No confirmation dialogs anywhere.
- No search results: "Nothing called "{q}" at home" + an accent "Add it"
  button that opens Add product with the name prefilled.
- Quantity display: `piece` → just the number; `tin`/`pack` pluralise
  ("1 tin", "3 tins"); metric units as "485 g", "1.5 kg", "1 l".

## Screen 2 — Add product (`reference/AddProduct.dc.html`)

- Header: "Cancel" (left, back to Home) · title "Add product".
- Name field (focused style: 2px ink border). From 2 typed characters, show
  matching existing products in a card below: "{Name} — Already tracked ·
  {location} · {qty}", with a "Restock" action. Last row: "New product: "{typed}"".
- "What is it?" — **Food / Household** chips, two equal halves, default Food.
- "How do you track it?" — two large toggle cards side by side:
  "Count it" (hint "3 tins, 485 g, 1 spare") and "Have / Low / Out"
  (hint "Spices, oil, dish soap"). Selected = 2px ink border.
- Count it → "How much?": `[−]  [ 485 | g ]  [+]` — the middle is a **typed
  decimal input** (`inputmode="decimal"`, accepts `,` or `.`), unit shown as a
  suffix; −/+ nudge by the unit's step. Unit chips below, in two
  labelled rows (the full list is in SPEC.md `default_unit`; the mockup shows
  an older, shorter set):
  - **Weight / volume:** g, kg, ml, l.
  - **Count:** piece, pack, tin, bottle, roll, then a **More** chip that expands
    the rest in place (jar, box, bag, carton, tube, tub, bar, sachet, bunch).
    If the selected unit is one of the hidden ones, show it in the visible row.
- Have / Low / Out → "Right now you…": three equal chips "Have it" / "Low" / "Out".
- "Where does it live?": location chips. **No chip is pre-selected for a new
  product**, except a suggestion: the place of the most recently updated item of
  the selected kind (follows the Food/Household choice until the user taps a
  place). With nothing selected the sticky button reads "Choose where it lives"
  and is disabled.
- Count it only: optional row "Add to shopping list below  [−] 200 g [+]"
  (min_quantity, moves by the unit's default step; 0 shows "Off").
- No step-size control here — it uses the unit default and is changed later
  from the Home update sheet (keeps adding under 10 seconds).
- Sticky bottom button, full width, accent: "Add to {Location}".
- Chip style: 44px tall, white, 1px line border; selected = ink fill, white text.
  Use `aria-pressed` on toggle buttons.

## Screen 3 — Shopping list (`reference/ShoppingList.dc.html`)

- Header: wordmark, title "Shopping list", subtitle "{n} to buy · {m} in the basket".
- Add row: text input "Add something…" + 48px square accent "+" button.
- Sections (hide empty ones):
  1. "Running low at home" — auto-added items (food and household), with where
     they live as the secondary line and a tag on the right: Low (warm fill),
     Out (dashed), or "Below {min}" (warm fill) for counted items under their
     minimum — e.g. "Floor cleaner · Under the sink · last spare opened".
  2. "Added by you" — manual items.
  3. "In the basket" — checked items, name struck through and muted.
- Each row is a `<label>` with a real checkbox (24px, accent-colour), the
  whole row tappable.
- When any *tracked* item is checked, show a `--toast-bg` card above the tab bar:
  "Back home? Mark what you bought as in stock." + contrasting button
  "Put {n} items away" → restocks those products and clears them from the list.

## Screen 4 — Edit product (`reference/EditProduct.dc.html`)

Opened by tapping a product's name on At home. Same building blocks and order
as Add product, prefilled.

- Header: "Cancel" · title "Edit product". Under it, a muted context line:
  "Was "{original name}" · {qty} in {location}".
- Name field, "What is it?" (Food/Household), "Where does it live?" (location chips).
- **Changing location** shows a note under the chips (`--have-bg` fill,
  `--have-ink` text, arrow icon): "Moves 1 from Pantry to Bathroom". Save
  label becomes "Save and move".
- "How do you track it?" toggle cards. Switching Count it → Have/Low/Out shows
  a note: "Will be set to Have (you have 1)" (Out if quantity is 0).
- Count it: unit chips, same two rows and More chip as Add product. If the unit changes, or the mode changes from
  Have/Low/Out to Count it, show a required amount input labelled in
  `--low-ink`: "Unit changed — how much do you have now?" / "How much do you
  have now?". Then step-size chips (weighed units only) and the min-quantity row.
- Bottom of the form, after a divider: outline button with archive-box icon
  "Remove from Onhand", and a muted line "Archived, not deleted. You can undo
  right after." No confirmation dialog — removing returns to Home with the undo toast.
- Sticky save button: accent "Save changes" / "Save and move" when something
  changed; otherwise a muted outlined "No changes" (`aria-disabled`).
- **Every save returns to Home with an undo toast**, including detail-only
  saves ("Saved Brown Basmati rice" + Undo).
- Toggle-card hints show the product's own current value instead of the
  generic examples: Count it → "Now: 3.5 kg"; Have / Low / Out → "Now: Low".

## Accessibility (non-negotiable)

- Real `<button>`, `<a>`, `<input>` + `<label>`. No click handlers on divs.
- Icon-only buttons have `aria-label` (e.g. "Use one Eggs", "Add product").
- Toast uses `role="status"`.
- Text contrast ≥ 4.5:1 in both themes.
