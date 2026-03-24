import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import Color from "colorjs.io";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "../..");
const uiDir = path.join(repoRoot, "app/src/main/java/dev/toastlabs/toastlift/ui");
const themePath = path.join(uiDir, "Theme.kt");
const appPath = path.join(uiDir, "ToastLiftApp.kt");
const reportsDir = path.join(__dirname, "reports");

const themeSource = fs.readFileSync(themePath, "utf8");
const appSource = fs.readFileSync(appPath, "utf8");
const uiFiles = fs.readdirSync(uiDir)
  .filter((file) => file.endsWith(".kt"))
  .map((file) => path.join(uiDir, file));

function ensureDir(dirPath) {
  fs.mkdirSync(dirPath, { recursive: true });
}

function lineNumberFor(source, index) {
  return source.slice(0, index).split("\n").length;
}

function formatRepoPath(absolutePath) {
  return path.relative(repoRoot, absolutePath).replaceAll(path.sep, "/");
}

function parseComposeColorLiteral(literal) {
  const hex = literal.replace(/^0x/i, "").toUpperCase();
  if (hex.length !== 8) {
    throw new Error(`Expected 8-digit ARGB color, received ${literal}`);
  }
  const alpha = Number.parseInt(hex.slice(0, 2), 16) / 255;
  const red = Number.parseInt(hex.slice(2, 4), 16);
  const green = Number.parseInt(hex.slice(4, 6), 16);
  const blue = Number.parseInt(hex.slice(6, 8), 16);
  return { red, green, blue, alpha, literal: `#${hex.slice(2)}`, argb: `#${hex}` };
}

function withAlpha(color, alpha) {
  return { ...color, alpha };
}

function compositeOver(foreground, background) {
  const outAlpha = foreground.alpha + background.alpha * (1 - foreground.alpha);
  if (outAlpha <= 0) {
    return { red: 0, green: 0, blue: 0, alpha: 0, literal: "#000000", argb: "#00000000" };
  }
  const red = ((foreground.red * foreground.alpha) + (background.red * background.alpha * (1 - foreground.alpha))) / outAlpha;
  const green = ((foreground.green * foreground.alpha) + (background.green * background.alpha * (1 - foreground.alpha))) / outAlpha;
  const blue = ((foreground.blue * foreground.alpha) + (background.blue * background.alpha * (1 - foreground.alpha))) / outAlpha;
  const rounded = {
    red: Math.round(red),
    green: Math.round(green),
    blue: Math.round(blue),
    alpha: outAlpha,
  };
  const alphaHex = Math.round(outAlpha * 255).toString(16).padStart(2, "0").toUpperCase();
  const rgbHex = [rounded.red, rounded.green, rounded.blue]
    .map((value) => value.toString(16).padStart(2, "0").toUpperCase())
    .join("");
  return {
    ...rounded,
    literal: `#${rgbHex}`,
    argb: `#${alphaHex}${rgbHex}`,
  };
}

function toCssColor(color) {
  if (color.alpha >= 0.999) {
    return `rgb(${color.red} ${color.green} ${color.blue})`;
  }
  return `rgb(${color.red} ${color.green} ${color.blue} / ${color.alpha.toFixed(4)})`;
}

function contrastRatio(foreground, background) {
  const fg = new Color(toCssColor(foreground));
  const bg = new Color(toCssColor(background));
  return Number(fg.contrast(bg, "WCAG21").toFixed(2));
}

function extractBalancedCall(source, marker) {
  const markerIndex = source.indexOf(marker);
  if (markerIndex === -1) {
    throw new Error(`Could not find ${marker}`);
  }
  const openIndex = source.indexOf("(", markerIndex);
  let depth = 0;
  for (let index = openIndex; index < source.length; index += 1) {
    const char = source[index];
    if (char === "(") {
      depth += 1;
    } else if (char === ")") {
      depth -= 1;
      if (depth === 0) {
        return source.slice(openIndex + 1, index);
      }
    }
  }
  throw new Error(`Unbalanced parentheses for ${marker}`);
}

function extractScheme(name) {
  const marker = `private val ${name} =`;
  const body = extractBalancedCall(themeSource, marker);
  const scheme = {};
  const colorRegex = /(\w+)\s*=\s*Color\((0x[0-9A-Fa-f]{8})\)/g;
  for (const match of body.matchAll(colorRegex)) {
    scheme[match[1]] = parseComposeColorLiteral(match[2]);
  }
  return scheme;
}

function extractAccent(name) {
  const marker = `private val ${name} = GlowAccent`;
  const body = extractBalancedCall(appSource, marker);
  const fields = {};
  const colorRegex = /(\w+)\s*=\s*Color\((0x[0-9A-Fa-f]{8})\)/g;
  for (const match of body.matchAll(colorRegex)) {
    fields[match[1]] = parseComposeColorLiteral(match[2]);
  }
  if (!fields.textOnAccent) {
    fields.textOnAccent = parseComposeColorLiteral("0xFF0E0E0E");
  }
  return fields;
}

function extractUiLiteralInventory() {
  const literals = [];
  const literalRegex = /Color\((0x[0-9A-Fa-f]{8})\)/g;
  for (const absolutePath of uiFiles) {
    const source = fs.readFileSync(absolutePath, "utf8");
    for (const match of source.matchAll(literalRegex)) {
      literals.push({
        literal: parseComposeColorLiteral(match[1]).argb,
        path: formatRepoPath(absolutePath),
        line: lineNumberFor(source, match.index),
      });
    }
  }
  return literals;
}

function luminance(color) {
  return new Color(toCssColor(color)).luminance;
}

function readableTextColor(background, scheme) {
  return luminance(background) < 0.34 ? scheme.onSurface : parseComposeColorLiteral("0xFF10131A");
}

function toneChipColors(tint, scheme, isDark, defaultOverlayAlpha) {
  const overlayAlpha = tint.alpha < 0.999 ? tint.alpha : defaultOverlayAlpha;
  const container = compositeOver(withAlpha({ ...tint, alpha: 1 }, overlayAlpha), scheme.surface);
  return {
    container,
    content: readableTextColor(container, scheme),
    border: compositeOver(
      withAlpha({ ...tint, alpha: 1 }, isDark ? 0.38 : 0.22),
      scheme.surface,
    ),
  };
}

const schemes = {
  dark: extractScheme("ToastLiftDarkColors"),
  light: extractScheme("ToastLiftLightColors"),
};

const accents = {
  dark: {
    ember: extractAccent("DarkEmberAccent"),
    surge: extractAccent("DarkSurgeAccent"),
    gold: extractAccent("DarkGoldAccent"),
    amethyst: extractAccent("DarkAmethystAccent"),
    orange: extractAccent("DarkOrangeAccent"),
  },
  light: {
    ember: extractAccent("LightEmberAccent"),
    surge: extractAccent("LightSurgeAccent"),
    gold: extractAccent("LightGoldAccent"),
    amethyst: extractAccent("LightAmethystAccent"),
    orange: extractAccent("LightOrangeAccent"),
  },
};

const pairMap = [
  ["onPrimary", "primary"],
  ["onPrimaryContainer", "primaryContainer"],
  ["onSecondary", "secondary"],
  ["onSecondaryContainer", "secondaryContainer"],
  ["onTertiary", "tertiary"],
  ["onTertiaryContainer", "tertiaryContainer"],
  ["onBackground", "background"],
  ["onSurface", "surface"],
  ["onSurfaceVariant", "surfaceVariant"],
  ["onError", "error"],
  ["onErrorContainer", "errorContainer"],
];

function buildAudits() {
  const audits = [];

  for (const [mode, scheme] of Object.entries(schemes)) {
    for (const [foregroundRole, backgroundRole] of pairMap) {
      if (!scheme[foregroundRole] || !scheme[backgroundRole]) {
        continue;
      }
      audits.push({
        kind: "theme-role",
        mode,
        name: `${foregroundRole} on ${backgroundRole}`,
        foreground: scheme[foregroundRole],
        background: scheme[backgroundRole],
        minContrast: 4.5,
      });
    }

    const isDark = mode === "dark";
    const modeAccents = accents[mode];
    for (const [accentName, accent] of Object.entries(modeAccents)) {
      audits.push({
        kind: "accent-fill",
        mode,
        name: `${accentName} textOnAccent on fill`,
        foreground: accent.textOnAccent,
        background: accent.color,
        minContrast: 4.5,
      });
      audits.push({
        kind: "accent-emphasis",
        mode,
        name: `${accentName} accent on surface`,
        foreground: accent.color,
        background: scheme.surface,
        minContrast: 4.5,
      });
      const miniTag = toneChipColors(accent.color, scheme, isDark, isDark ? 0.22 : 0.16);
      audits.push({
        kind: "component",
        mode,
        name: `MiniTag accent (${accentName})`,
        foreground: miniTag.content,
        background: miniTag.container,
        minContrast: 4.5,
      });
    }

    const neutralMiniTag = toneChipColors(scheme.surfaceVariant, scheme, isDark, isDark ? 0.22 : 0.16);
    audits.push({
      kind: "component",
      mode,
      name: "MiniTag neutral",
      foreground: neutralMiniTag.content,
      background: neutralMiniTag.container,
      minContrast: 4.5,
    });

    const unselectedChip = toneChipColors(
      scheme.surfaceVariant,
      scheme,
      isDark,
      isDark ? 0.92 : 1,
    );
    const selectedChip = toneChipColors(
      scheme.primaryContainer,
      scheme,
      isDark,
      isDark ? 0.82 : 1,
    );
    audits.push({
      kind: "component",
      mode,
      name: "FilterChip unselected",
      foreground: unselectedChip.content,
      background: unselectedChip.container,
      minContrast: 4.5,
    });
    audits.push({
      kind: "component",
      mode,
      name: "FilterChip selected",
      foreground: selectedChip.content,
      background: selectedChip.container,
      minContrast: 4.5,
    });
  }

  return audits.map((audit) => {
    const contrast = contrastRatio(audit.foreground, audit.background);
    return {
      ...audit,
      contrast,
      pass: contrast >= audit.minContrast,
      foregroundHex: audit.foreground.argb,
      backgroundHex: audit.background.argb,
    };
  });
}

const inventory = extractUiLiteralInventory();
const audits = buildAudits();
const failures = audits.filter((audit) => !audit.pass);
const groupedInventory = Object.entries(
  inventory.reduce((accumulator, entry) => {
    accumulator[entry.literal] ??= [];
    accumulator[entry.literal].push(`${entry.path}:${entry.line}`);
    return accumulator;
  }, {}),
).sort((left, right) => right[1].length - left[1].length);

const report = {
  generatedAtUtc: new Date().toISOString(),
  totals: {
    audits: audits.length,
    failures: failures.length,
    uiLiteralCount: groupedInventory.length,
  },
  failures,
  audits,
  uiLiteralInventory: groupedInventory.map(([literal, locations]) => ({ literal, locations })),
};

ensureDir(reportsDir);
fs.writeFileSync(
  path.join(reportsDir, "color-audit-report.json"),
  JSON.stringify(report, null, 2),
);

const markdown = [
  "# ToastLift Color Audit",
  "",
  `Generated: ${report.generatedAtUtc}`,
  "",
  `Audits: ${report.totals.audits}`,
  `Failures: ${report.totals.failures}`,
  `Unique UI literals: ${report.totals.uiLiteralCount}`,
  "",
  "## Failures",
  "",
  ...(failures.length === 0
    ? ["All audited theme, accent, tag, and chip recipes pass the configured WCAG 2.1 contrast threshold."]
    : failures.map((failure) =>
        `- [${failure.mode}] ${failure.name}: ${failure.contrast}:1 (min ${failure.minContrast}:1), fg ${failure.foregroundHex}, bg ${failure.backgroundHex}`,
      )),
  "",
  "## Raw UI Literal Inventory",
  "",
  ...groupedInventory.map(([literal, locations]) => `- ${literal}: ${locations.join(", ")}`),
  "",
];

fs.writeFileSync(path.join(reportsDir, "color-audit-report.md"), markdown.join("\n"));

if (failures.length > 0) {
  console.error(`Color audit failed with ${failures.length} contrast issue(s).`);
  process.exitCode = 1;
} else {
  console.log("Color audit passed with no contrast failures.");
}
