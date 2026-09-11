# Fold Probe

A throwaway capability probe for one question:

> On a Galaxy Z Fold 6, can a third-party app light up **both** the cover screen and
> the inner screen at the same time — alarm UI on the cover, warm lamp glow on the inner?

This app doesn't implement the alarm. It only finds out whether the alarm is buildable,
before any real time gets spent on it.

## Result on a Galaxy Z Fold 6 (SM-F956N, Android 16)

**Dual-screen mode works.** `PRESENT_ON_AREA` reports `AVAILABLE`, both panels light
simultaneously, and touch reaches the cover-screen presentation. This contradicts the
Android docs, which list dual-screen mode for Pixel Fold only — Samsung foldables
support it too, at least on this device.

| Check | Result |
| --- | --- |
| `PRESENT_ON_AREA` | `AVAILABLE` |
| `TRANSFER_ACTIVITY_TO_AREA` | `AVAILABLE` |
| Cover screen touch | Works |
| Cover display | 968 x 2376 px |
| Inner display | 1856 x 2160 px @ 2.25x |

One defect found: the cover screen keeps its natural **portrait** orientation when the
phone is tented, so landscape content renders sideways. A presented window has no
orientation flag to set, so `RotatableHost` rotates the content within the window
instead. The correct rotation for a tented phone is being determined empirically —
the probe exposes a manual rotation cycle and a gravity-based auto mode.

Measuring that turned out to need its own fix. The report can only be copied from the
inner display, so reading it means unfolding the phone first — and every reading
therefore described the *un-tented* state (`FLAT`, with the cover display's rotation
already gone with the closed session). The probe now records the answer instead of
reporting it live: **`✓ looks right`** on the cover screen snapshots rotation, posture,
gravity and both display rotations at that instant, and posture changes are written to
the log, which survives unfolding.

## Why this was in question

There is exactly one public API for two simultaneously-lit panels:
`WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA` ("dual-screen mode") in
Jetpack WindowManager. Google documents it for **Pixel Fold on Android 14+**. Samsung
documents *rear display* mode (which turns the inner display **off**) from One UI 6.0.
Whether Samsung foldables expose dual-screen mode to third-party apps is not stated
either way in public docs — hence this probe.

If the device reports `UNSUPPORTED`, no workaround exists at the app layer.

## What it reports

- **Verdict** — a plain yes / maybe / no on dual-screen mode
- **Window areas** — every area the platform exposes, its size, and the status of both
  `PRESENT_ON_AREA` and `TRANSFER_ACTIVITY_TO_AREA`
- **Fold posture, live** — `FoldingFeature` state, orientation, hinge bounds. Watch this
  change as you fold, so we learn whether a tented posture reads as `HALF_OPENED`
- **Cover screen touch** — whether taps on a window presented to the cover screen
  actually reach the app. The alarm's stop/snooze buttons depend on this, and it is
  *not* documented anywhere I could find

## How to use it

1. Download `fold-probe.apk` from the GitHub Actions run (Artifacts section)
2. Sideload it to the Fold 6 and open it
3. Read the **Verdict** line, then:
   - **Test 1 — light BOTH screens.** If it works: inner display turns amber (the lamp),
     cover screen shows a mock clock with stop/snooze. Tap those buttons — the readout
     confirms whether touch routes.
   - **Test 2 — move to cover screen only.** The rear-display fallback. The inner display
     should go dark and the app appear on the cover screen.
4. **Fold the phone into a tent** while a session is running and see whether it survives
5. Hit **Copy report to clipboard** and send the text back

## Reading the result

| Verdict | What it means |
| --- | --- |
| `AVAILABLE` / `ACTIVE` | The concept works. Build the real alarm. |
| `UNAVAILABLE` | Supported, but blocked in that posture — worth probing further. |
| `UNSUPPORTED` | No third-party route to both screens. The concept needs redesigning around one screen. |

## Building

CI builds it on every push to this branch. To build locally you need the Android SDK:

```
./gradlew assembleDebug
```

The APK is debug-signed, so it is for sideloading only — not distribution.
