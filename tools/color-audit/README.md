# ToastLift Color Audit

One-off contrast audit for the Compose UI palette and shared component recipes.

## Usage

```bash
cd tools/color-audit
npm install
npm run audit
```

The script writes:

- `reports/color-audit-report.md`
- `reports/color-audit-report.json`

It inventories raw UI color literals, audits theme `on*` pairs, app accents, `MiniTag`, and `ToastLiftFilterChip` recipes for both dark and light modes.
