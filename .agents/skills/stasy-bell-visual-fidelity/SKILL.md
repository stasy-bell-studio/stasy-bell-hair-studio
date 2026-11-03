---
name: stasy-bell-visual-fidelity
description: Implement and visually review Stasy Bell Compose and React interfaces against the approved reference and design tokens. Use for Stasy Bell screen design, not unrelated backend work.
---

# Stasy Bell visual fidelity

## Sources and scope

- [Approved visual reference](../../../v2/design/approved/visual-reference.png): inspect the image before choosing composition. From this skill directory, resolve this link from the repository root if the Markdown viewer cannot resolve it.
- [Approved design system](../../../v2/design/approved/design-system-v1.md): exact palette, type, spacing and accessibility parameters.
- Actual paths from this directory are `../../../v2/design/approved/visual-reference.png` and `../../../v2/design/approved/design-system-v1.md`.
- The current user request defines which screens to change. Reference images do not authorize more screens, invented content, payment integrations, or deployment.
- The image controls visual composition; Markdown controls precise tokens. Preserve readable opaque working surfaces when the reference is ambiguous.

## Read the composition before coding

For each target screen identify the dominant surface, header/content boundary, alignment, proportion of imagery, grouping of metrics, row density, navigation, and primary action. Compare these against the actual current screenshot. A palette match alone is not fidelity.

Android: compact forest header, natural low-contrast decorative forms, translucent KPI tiles, light calendar/list surfaces and small outlined navigation icons. Website: warm ivory editorial layout, large serif title balanced with a real photograph, green pill actions, subtle translucent panels and a compact photographic service catalogue.

Never copy mock figures, client portraits/names, prices, profit claims, percentages or business promises from the reference. Use backend results and existing approved website content. Label cash balance as cash difference, not profit. Calculate charts from available actual transactions, including reversals. If a metric is unavailable, show that state rather than invent it.

## Implementation

1. Reuse semantic palette tokens. Keep new colors, shapes and decorative brushes in a shared platform component source. Use the approved 4/8/12/16/24/32 spacing rhythm and the specification's radii, type sizes and weights.
2. Compose: use layered Box/Canvas surfaces for gradients and botanical geometry, thin highlight borders, restrained elevation and translucent KPI surfaces. Keep forms, calendar cells and long rows opaque. Decorative geometry has no accessibility semantics. Preserve system font scaling and 48dp touch targets even where visible icons are 18–20dp.
3. React: build the composition with semantic sections and CSS grid; use the approved serif/sans hierarchy, existing licensed imagery, layered gradients and restrained backdrop filtering. Supply a readable solid-color fallback. Preserve content, routes, addresses, prices and booking behavior.
4. Give panels depth through a clear background/foreground relationship, a fine bright top edge and soft shadow. Avoid uniform flat green rectangles, excessive blur, oversized controls and floating decoration over text.
5. Keep active, loading, empty and error states real. Retain action handlers, validation, confirmations and financial audit history. Do not change API contracts or financial rules for visual convenience.

## Evidence and correction loop

- Implement a coherent group of screens before running expensive checks. First compile/type-check changed code, then perform one grouped Android 15 emulator run through GitHub Actions and Playwright website capture.
- Capture actual application screens using synthetic data. Never use generated pictures, a static replica or the reference itself as implementation evidence.
- For the current review group capture Android Home/Calendar/Finance and website 1440px/375px. Record commit, viewport, font scale, dataset and workflow/artifact link. Compare equivalent states.
- Inspect each image at normal viewing size. Check composition, colors, typography, element scale, row density, glass/depth, proportions, navigation and clipping.
- Maintain a discrepancy table: screen, expected appearance, observed difference, priority, fix, remaining limitation.
- P0: inaccessible or hidden action, misleading money, data loss, broken flow. P1: wrong hierarchy/composition, illegibility, missing reference structure. P2: spacing, radius, alignment, overly flat/deep surface. Correct in that order.
- Make one coherent correction pass and a final grouped verification. Do not trigger full emulators per color adjustment or repeat identical runs. For a stalled test, use logs to locate the blocking operation; an arbitrary rerun does not diagnose it.
- Compilation and unit tests do not establish visual acceptance. Report only inspected screenshots as visually reviewed, name remaining deviations, and stop at the user's review boundary.
