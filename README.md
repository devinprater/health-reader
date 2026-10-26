# Health Reader

An Android app that reads Health Connect and writes the records out as sentences, because some of
the charts in Samsung Health cannot be read with a screen reader. Every row is ordinary text, so
TalkBack, SandFly and braille displays read it without any special handling.

## What it shows

Thirty record types, one screen each, over the last 30 days:

- Sleep: every night with its stage breakdown in words, naps listed separately
- Exercise sessions, steps, distance, floors, active and total calories
- Heart rate, resting heart rate, heart rate variability
- Oxygen saturation, respiratory rate, body and skin temperature
- Weight, body fat, height, basal metabolic rate, bone mass, body water, lean mass
- Blood pressure, blood glucose, nutrition, hydration
- Mindfulness, VO2 max, speed, power, wheelchair pushes
- Menstrual cycle: periods and flow

Each screen opens with a sentence of summary, then one line per record.

## Permissions

The app asks Health Connect for read access to all of the above in one screen. Nothing is written
back, and nothing leaves the phone.

Health Connect must have a rationale activity that handles
`android.intent.action.VIEW_PERMISSION_USAGE` with the category
`android.intent.category.HEALTH_PERMISSIONS`. Without it, Health Connect refuses to serve any
record and the permission request returns nothing, without an error the user can see.

## Where the data comes from

Health Connect, which Samsung Health writes to when it is connected. Coverage is only as good as
what the contributing apps write: on this phone, Samsung Health has written sleep and heart rate
but very little step or weight data, and the app says "No weight in the last 30 days" rather than
inventing anything.

## Build

```bash
export JAVA_HOME="C:/Users/Devin Prater/AppData/Local/Java/jdk-17.0.20.1+1"
export ANDROID_HOME="C:/Users/Devin Prater/Android/Sdk"
gradle :app:assembleDebug --no-daemon
```

Gradle 8.13 and AGP 8.9.2, compileSdk 36, minSdk 28.
