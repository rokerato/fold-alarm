# Fold Alarm

An alarm clock for a book-style foldable. Stand the phone tented on a bedside table
and, when it rings, **both screens light at once**: the cover screen facing you shows
the time with stop and snooze, while the inner screen faces down into the tent and
becomes a warm lamp.

Built and verified on a **Galaxy Z Fold 6 (SM-F956N), Android 16**.

## How the two screens work

There is exactly one public API for two simultaneously-lit panels:
`WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA` ("dual-screen mode") in
Jetpack WindowManager. The ringing activity runs on the inner display and presents
the clock onto the cover display, so the lamp and the controls are the two halves of
one session.

Google documents dual-screen mode for **Pixel Fold** only, and Samsung documents
*rear display* mode — which turns the inner display **off** — so whether this was
possible at all had to be settled on hardware rather than from the docs. It was, by
the probe that is still in this repo (**Diagnostics**, in the app). Findings:

| Check | Result |
| --- | --- |
| `PRESENT_ON_AREA` | `AVAILABLE` |
| `TRANSFER_ACTIVITY_TO_AREA` | `AVAILABLE` |
| Touch on a cover-screen presentation | Works (undocumented) |
| Capability while tented (`HALF_OPENED`) | Still `AVAILABLE` |
| Cover display | 968 x 2376 px |
| Inner display | 1856 x 2160 px @ 2.25x |

**Where dual-screen mode is unavailable the alarm still rings** — stop and snooze are
drawn on whichever display is active, so it can never become undismissable.

## Orientation

A window presented on the cover screen keeps that display's natural **portrait**
orientation however the phone is physically held, and `WindowAreaSessionPresenter`
offers only `setContentView()` — there is no orientation flag to set. So
`RotatableHost` rotates the content *inside* the window, measuring its child with the
axes swapped at 90 and 270 degrees.

Which rotation a tent needs is undocumented, so it was measured. Standing tented, the
device reported gravity `x=-9.6, y=-0.0`; `CoverRotation` derives 270 degrees from
that, which matched the rotation chosen by hand. The alarm therefore orients itself
from the gravity sensor and needs no setting.

## Waking a sleeping phone

- `AlarmManager.setAlarmClock()` — the only scheduling that stays punctual in Doze,
  and it shows the alarm in the status bar
- A high-importance notification with a **full-screen intent** launches the ringing
  screen, since background activity starts are blocked from Android 10
- `showWhenLocked` + `turnScreenOn` so it appears without unlocking
- `BootReceiver` re-applies the alarm after a reboot

Two permissions are required, and the app says so on its front screen if either is
missing: **exact alarms** (otherwise it may fire late or not at all) and
**notifications** (which launch the ringing screen).

## Using it

1. Download `fold-probe.apk` from the GitHub Actions run (Artifacts)
2. Sideload it, set a time, grant the two permissions
3. **Ring now (preview)** fires it immediately — no need to wait until morning
4. Tent the phone to see both screens

Snooze length, lamp brightness and lamp warmth are adjustable. **Diagnostics** opens
the original capability probe.

## Building

CI builds on every push to this branch; the Android SDK and Google Maven are
unreachable from the dev container. Locally, with the SDK installed:

```
./gradlew assembleDebug
```

The APK is debug-signed — sideloading only, not distribution. Release signing needs
your own keystore.
