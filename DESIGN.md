# ToastLift Design Language Spec

> **Purpose:** This document is the single source of truth for ToastLift's visual
> design. Any agent or human working on the UI MUST follow this spec. When in
> doubt, refer here.

---

## 1. Design Philosophy

ToastLift is a **strength training app** that should feel like a premium,
high-energy product — not a settings panel. Think Cash App's bold minimalism,
Uber's full-bleed canvas, X/Twitter's edge-to-edge flow, and Amex's custom
typography and iconography.

### Core Principles

| Principle | What it means |
|-----------|---------------|
| **Canvas, not cards** | The screen IS the canvas. Don't wrap every piece of content in a card. Use full-bleed sections, dividers, and negative space. |
| **Hero first** | Every screen has ONE hero element — a big number, a progress ring, a gradient banner — that draws the eye immediately. |
| **Motion communicates** | Transitions, springs, and animated content changes guide the user. Never just swap static content. |
| **Custom everything** | No stock Material icons for primary UI. Custom vector icons with unique silhouettes. |
| **Restraint** | 3 nav tabs max. Limited color accents. Generous whitespace. Let content breathe. |

---

## 2. Navigation (3 Tabs)

### Tab Structure

The old 5-tab structure (Today, Generate, Library, History, Profile) is
replaced with 3 tabs:

| Tab | Label | What it contains |
|-----|-------|-----------------|
| **Home** | "Home" | Today's session, program progress, quick generate, coach brief — the dashboard. Merge of old Today + Generate. |
| **Library** | "Explore" | Exercise library, history, stats, bounty cards, strength score — browse and review. Merge of old Library + History. |
| **You** | "You" | Profile, settings, theme, preferences, program management. Merge of old Profile + onboarding. |

### Nav Bar Design

- **No Material `NavigationBar`** — use a custom bottom bar
- Floating pill shape with 24dp bottom margin
- 3 custom icons (no text labels by default; show label on selected tab only)
- Active icon: accent color fill + 4dp top indicator line
- Inactive icon: muted color, no indicator
- Background: surface color with 80% alpha + blur effect
- Height: 56dp + 24dp bottom margin (floating)

### Custom Nav Icons

All three nav icons are custom VectorDrawables:

| Tab | Icon | Description |
|-----|------|-------------|
| Home | `ic_nav_home.xml` | A stylized "lift" — upward arrow inside a rounded square, suggesting progress/lifting |
| Explore | `ic_nav_explore.xml` | A compass-free exploration mark — two overlapping circles (discovery) |
| You | `ic_nav_you.xml` | A geometric person silhouette — hexagon head + shoulders shape |

---

## 3. Color System

### Dark Theme (Primary)

```
Background:     #0A0A0B  (near-black, slight warm tint)
Surface:        #141416  (elevated content)
SurfaceHigh:    #1C1C1F  (cards, sheets)
SurfaceMax:     #242428  (highest elevation)

Primary:        #E8FF47  (electric lime — brand accent, CTAs)
OnPrimary:      #0A0A0B
Secondary:      #3DFFA0  (mint green — success, positive)
Tertiary:       #FFC940  (gold — achievements, highlights)

Error:          #FF3D3D
OnSurface:      #ECECEC
OnSurfaceVariant: #9A9AA0
Outline:        #2A2A2E
```

### Light Theme

```
Background:     #FAFAF8  (warm white)
Surface:        #FFFFFF
SurfaceHigh:    #F5F5F3
SurfaceMax:     #EFEFED

Primary:        #2D7A1E  (forest green)
OnPrimary:      #FFFFFF
Secondary:      #00854E
Tertiary:       #9A6E00

Error:          #D43030
OnSurface:      #1A1A1C
OnSurfaceVariant: #6B6B72
Outline:        #E0E0E2
```

### Accent Colors (Semantic)

| Name | Dark | Light | Usage |
|------|------|-------|-------|
| Ember | #FF3D3D | #D73433 | Destructive, warnings |
| Surge | #3DFFA0 | #008141 | Success, gains, progress |
| Gold | #FFC940 | #9A6E00 | Achievements, PRs, rewards |
| Amethyst | #3D9FFF | #1D76C8 | Info, neutral actions |
| Orange | #FF7A1A | #C14A00 | Energy, active state |

### Gradient Tokens

| Token | Dark | Usage |
|------|------|-------|
| `heroGradient` | #0A0A0B → #1A1A0E | Home screen hero background |
| `sessionGradient` | #1C1C1F → #0A0A0B | Active session background |
| `cardAccent` | accent.color @ 0.12 alpha | Subtle card tint |

---

## 4. Typography

### Font Families

| Family | Font File | Usage |
|--------|-----------|-------|
| Display | Bebas Neue (`bebas_neue_regular.ttf`) | Hero numbers, big stats, screen titles |
| Sans | System default (Inter/Roboto) | Body text, headlines |
| Mono | JetBrains Mono (`jetbrains_mono_regular/medium.ttf`) | Labels, tags, metadata, nav |

### Type Scale

| Role | Family | Size | Weight | Letter Spacing |
|------|--------|------|--------|---------------|
| Hero | Display | 64sp | Normal | 0.02em |
| Display | Display | 40sp | Normal | 0.02em |
| DisplaySmall | Display | 28sp | Normal | 0.02em |
| Headline | Sans | 22sp | Bold | 0 |
| Title | Sans | 16sp | SemiBold | 0 |
| Body | Sans | 14sp | Normal | 0 |
| BodySmall | Sans | 12sp | Normal | 0 |
| Label | Mono | 10sp | Medium | 0.15em |
| LabelSmall | Mono | 9sp | Normal | 0.15em |

### Usage Rules

- **Hero numbers** (e.g., token balance, strength score): Use `Display` family at 48-64sp. These should dominate the screen.
- **Section titles**: `Headline` at 22sp. No ALL-CAPS mono. Just clean bold text.
- **Tags/metadata**: `Label` mono at 10sp with wide tracking.
- **Body**: 14sp, never below 12sp for readable content.

---

## 5. Component Specs

### FeatureCard (Redesigned)

The old `FeatureCard` with its 2dp top accent bar made everything look like
Settings. The new version:

```
Shape:      RoundedCornerShape(16.dp)
Border:     None by default (use elevation/color instead)
Elevation:  0.dp (use surface color contrast for separation)
Padding:    20.dp all sides
```

**Variants:**

| Variant | When to use | Visual |
|---------|-------------|--------|
| `Flat` | Default content sections | Just surface color, no border, no elevation |
| `Tinted` | Highlighted content (accent) | Surface + 8% accent color tint |
| `Hero` | Primary screen element | Gradient background, larger padding (28dp), no border |
| `Outlined` | Interactive/secondary | 1dp outline in surfaceVariant color |

**No top accent bar.** If you need to indicate an accent, use a small
`MiniTag` pill in the top-left corner of the card content.

### MiniTag

```
Shape:      RoundedCornerShape(50) (pill)
Background: accent.color @ 0.15 alpha
Text:       Mono Label 9sp, accent.color
Padding:    4dp vertical, 10dp horizontal
```

### Bottom Bar (Custom, replaces NavigationBar)

```
Shape:      RoundedCornerShape(28.dp)
Background: surface @ 90% alpha (blur if possible)
Margin:     16dp horizontal, 24dp bottom
Height:     60dp
Items:      3 evenly spaced, icon-only (label on selected)
```

### Screen Header (Replaces TopAppBar)

- **Not** `CenterAlignedTopAppBar`
- Use a simple `Text` with `Display` family at 28sp
- Left-aligned, not centered
- 20dp horizontal padding, 12dp top
- No app bar chrome/background — content flows directly
- Optional `IconButton` on right (settings gear, filter, etc.)

### Buttons

| Type | Usage | Style |
|------|-------|-------|
| Primary | Main CTA | Filled, primary color, 52dp height, 16dp radius, Bold 14sp |
| Secondary | Alternative action | Outlined, 1dp outline, 52dp height |
| Ghost | Tertiary/minor | Text only, no container |
| FAB | Floating action | 56dp circle, primary color, shadow |

### Active Session

- **Full-screen takeover** — not a card. When a workout is active, it takes
  over the entire screen (no nav bar, no top bar).
- Large exercise name in Display family
- Set numbers as big tappable circles
- Rest timer as a full-width progress bar at top
- Swipe between exercises (HorizontalPager)

### Lists & Dividers

- Prefer **edge-to-edge lists** with thin dividers over card-wrapped lists
- Divider: 1dp, `outline` color, full width with 20dp start indent
- List item height: 56dp minimum (tappable target)
- No card containers around individual list items

---

## 6. Motion

### Transitions

| From → To | Animation |
|-----------|-----------|
| Tab switch | Fade (180ms) — no slide, just clean crossfade |
| Sheet open | Slide up from bottom (280ms, FastOutSlowIn) |
| Sheet dismiss | Slide down (200ms) |
| Card press | Scale to 0.97 (spring, stiffness 400) |
| Button press | Scale to 0.95 (spring, stiffness 600) |
| Content change | AnimatedContent with fade (220ms) |
| Progress | animateFloatAsState (spring) |

### Always animate:
- Tab selection changes
- Sheet/dialog open/close
- Loading → content transitions
- Number changes (count up animation for stats)

### Never animate:
- Text input
- Scroll position
- Drag gestures

---

## 7. Layout Patterns

### Home Screen

```
┌─────────────────────────┐
│  Display title (left)   │  ← "Today" or date
│                    [⚙]  │  ← Optional action icon
├─────────────────────────┤
│                         │
│   HERO ELEMENT          │  ← Big progress ring or number
│   (e.g., session ready) │     Gradient background, 200dp+ height
│                         │
├─────────────────────────┤
│  Section: Next Workout  │  ← Headline + content, no card wrapper
│  ┌─────────────────────┐│
│  │  Flat list of sets  ││  ← Thin dividers, edge-to-edge
│  └─────────────────────┘│
├─────────────────────────┤
│  Section: Coach         │
│  Tinted card with brief │  ← Tinted variant
├─────────────────────────┤
│  Section: Program       │
│  Progress bar + text    │  ← Flat, inline
└─────────────────────────┘
        [floating nav bar]
```

### Explore Screen

```
┌─────────────────────────┐
│  "Explore"              │
│                   [🔍]  │
├─────────────────────────┤
│  [Stats Row: 3 tiles]   │  ← Streak | Volume | Token balance
│  (edge-to-edge, divided)│
├─────────────────────────┤
│  [Calendar heatmap]     │  ← Custom Canvas-drawn
│  (full width)            │
├─────────────────────────┤
│  Recent workouts list    │  ← Edge-to-edge, dividers
│  (tap to expand)         │
├─────────────────────────┤
│  Bounty cards grid      │  ← 2-col grid, Tinted variant
└─────────────────────────┘
        [floating nav bar]
```

### You Screen

```
┌─────────────────────────┐
│  Avatar + Name          │  ← Hero section, gradient bg
│  "Member since..."      │
├─────────────────────────┤
│  Theme toggle           │  ← List rows with dividers
│  Preferences            │
│  Program management     │
│  Data export            │
│  About                  │
└─────────────────────────┘
        [floating nav bar]
```

---

## 8. Custom Icons

All icons are XML VectorDrawables in `res/drawable/`. Key requirements:

- **24dp viewport** (standard), 2dp stroke width
- Use `currentColor` tinting where possible
- Rounded line caps and joins
- Minimalist geometric style — no detailed illustrations

### Required Icons

| Icon | File | Usage |
|------|------|-------|
| Nav Home | `ic_nav_home.xml` | Bottom nav: Home tab |
| Nav Explore | `ic_nav_explore.xml` | Bottom nav: Explore tab |
| Nav You | `ic_nav_you.xml` | Bottom nav: You tab |
| Lift | `ic_lift.xml` | Workout/lift actions |
| Flame | `ic_flame.xml` | Streaks, intensity |
| Timer | `ic_timer.xml` | Rest timer, duration |
| Target | `ic_target.xml` | Goals, muscle targets |
| Chart | `ic_chart.xml` | Stats, progress |
| Trophy | `ic_trophy.xml` | Achievements, PRs |
| Settings | `ic_settings.xml` | Settings, preferences |

---

## 9. Spacing & Sizing

| Token | Value | Usage |
|-------|-------|-------|
| `screenPadding` | 20dp | Horizontal screen edge padding |
| `sectionGap` | 24dp | Vertical gap between sections |
| `itemGap` | 12dp | Vertical gap between items in a section |
| `cardPadding` | 20dp | Internal card padding |
| `heroPadding` | 28dp | Hero element internal padding |
| `minTouchTarget` | 48dp | Minimum tappable size |
| `bottomBarHeight` | 60dp | Nav bar height |
| `bottomBarMargin` | 24dp | Nav bar bottom margin |

---

## 10. Migration Guide

### For agents working on ToastLift:

1. **Always check this file** before creating or modifying UI components
2. **Replace `CenterAlignedTopAppBar`** with the Screen Header pattern (Section 5)
3. **Replace `NavigationBar`/`NavigationBarItem`** with the custom Bottom Bar (Section 5)
4. **Replace `FeatureCard` top accent bar** — use card variants instead (Section 5)
5. **Replace Material icons** (`Icons.Rounded.*`) with custom VectorDrawables (Section 8)
6. **Merge old tabs**: Today + Generate → Home; Library + History → Explore; Profile → You
7. **Use edge-to-edge lists** with dividers instead of card-wrapped list items
8. **Animate transitions** per the motion spec (Section 6)
9. **Never use ALL-CAPS mono** for screen titles — use Display family, left-aligned
10. **Test both dark and light themes** — the color system (Section 3) defines both

### What to keep from the old design:
- Bebas Neue and JetBrains Mono fonts
- The accent color system (Ember, Surge, Gold, Amethyst, Orange)
- The `GlowAccent` data class pattern (adapt to new tokens)
- Bounty card collectibles concept
- Canvas-drawn charts and graphs
- The `HorizontalPager` for exercise swiping in active sessions
