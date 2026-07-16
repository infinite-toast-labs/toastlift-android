# ToastLift Google Play listing

This directory is the versioned source of truth for the default English
(United States) Google Play listing.

## Regenerate screenshots

```bash
make playstore-screenshots-phone
make playstore-screenshots-tablets
```

Each device class intentionally contains exactly two screenshots. Final JPEGs
and alt-text manifests are committed here. Raw AppReveal responses, emulator
logs, and session data remain ignored under `artifacts/playstore/`.

The feature graphic is an AI-generated marketing illustration. Its exact prompt
is versioned in `feature-graphic-prompt.txt`; the final reviewed 1024x500 PNG is
versioned as `feature-graphic.png`.

## Listing URLs

- Product: https://www.toastlabs.dev/toastlift/
- Support: https://www.toastlabs.dev/toastlift/support/
- Privacy: https://www.toastlabs.dev/toastlift/privacy/
- Contact: https://www.toastlabs.dev/toastlift/contact/
