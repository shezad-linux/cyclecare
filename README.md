# CycleCare

CycleCare is a local-first menstrual cycle and wellness tracker for Android. This repository includes:

- A Compose MVVM Android scaffold with modular feature boundaries
- Markdown product and engineering docs
- Figma-ready UI specifications and preview assets

## Structure

- `docs/` product, architecture, privacy, monetization, prediction, QA, and UI mapping docs
- `design-preview/` local HTML sources intended for Figma capture/import
- `app/` Android application shell and navigation
- `core/` shared model, domain, data, database, design system, and testing modules
- `feature/` feature-specific UI modules

## Key Product Constraints

- Offline-first and low backend dependency
- Wellness-first positioning with explicit non-medical language
- All features are available by default with no paid tier; the app includes ad-supported surfaces
- Figma is the visual source of truth for screen and token translation

## Local Setup

1. Ensure Android SDK is installed and `local.properties` contains a valid `sdk.dir`.
2. Generate the Gradle wrapper if missing: `gradle wrapper`.
3. Build the project: `./gradlew assembleDebug`
4. Run tests: `./gradlew test`

## Figma Workflow

- Build or refine the HTML preview files in `design-preview/`.
- Capture them into new Figma files via the Figma MCP design generation flow.
- Use exact node URLs with Figma MCP `get_design_context` and `get_screenshot` when translating screens into Compose.
