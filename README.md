# Hapticks

Android app that adds customizable haptic feedback to taps across every installed app, built on top of the Android Accessibility API.

## Privacy & Play Store policy

Hapticks uses the Accessibility API solely to detect `TYPE_VIEW_CLICKED` event types and trigger the device vibrator. It declares `android:canRetrieveWindowContent="false"` so the OS does not grant it access to on-screen content, and it never inspects, stores, or transmits any user data. This intent is disclosed in-app on the onboarding card.

## Out of scope for v1

- Per-app allow / block lists
- Element-type targeting (buttons vs switches vs sliders)
- User-defined custom patterns
- Foreground-service keep-alive for aggressive OEM battery policies
