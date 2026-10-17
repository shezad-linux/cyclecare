# QA And Acceptance

## Functional Acceptance

- Onboarding saves a profile and immediately unlocks predictions
- Home, calendar, log, insights, and settings work offline
- Reminder preferences persist across process death
- Privacy lock setup and unlock work without backend services
- Export generates a local file and supports share intents

## Design Acceptance

- Compose implementations are matched against Figma node screenshots
- Bottom navigation and card spacing remain consistent across phone sizes
- Free-only flows remain visually consistent across onboarding, home, insights, settings, and export

## Regression Risks To Monitor

- Date math around timezone boundaries
- Irregular cycle confidence messaging
- Insight summary visibility after removing legacy gated branches
- Notification scheduling after reboot or timezone change
