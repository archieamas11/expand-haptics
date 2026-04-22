# Hapticks

Android app that adds responsive touch feedback across apps and system UI for a more refined, premium feel. Pixel haptics are highly effective and should be utilized more extensively, as several apps and UI interactive elements have yet to incorporate haptic feedback. So i made one

## Privacy & Play Store policy

Hapticks uses the Accessibility API solely to detect `TYPE_VIEW_CLICKED` event types and trigger the device vibrator. It declares `android:canRetrieveWindowContent="false"` so the OS does not grant it access to on-screen content, and it never inspects, stores, or transmits any user data. This intent is disclosed in-app on the onboarding card.

## Out of scope for v1

- Per-app allow / block lists
- Element-type targeting (buttons vs switches vs sliders)
- User-defined custom patterns
- Foreground-service keep-alive for aggressive OEM battery policies
