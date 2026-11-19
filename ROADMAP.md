# Moodlog roadmap

![Moodlog roadmap](docs/roadmap.png)

**Goal:** help people who track their mood for their health (for example to show a
doctor or therapist how they have been, or how a treatment change went) with an app
that keeps their data private, on every device they use.

The steps are in order: each one builds on the one before. There are no dates; a step
starts when the previous one is done.

## Done (November 2026)

- Moodlog for the PC, 1.0 → 2.1: moods with notes, energy and anxiety, events, charts,
  the doctor report, CSV/XLSX export, backup and restore, two styles.
- Moodlog for iPhone 1.0: logging on the phone, synced with the PC over home Wi-Fi.
- Sleep, steps and heart rate from Apple Health.
- No account and no cloud: everything is in plain files on your own devices.

## Step 1 – Solid for its users (now)

- **Languages:** Italian first (done in PC 2.2.0 and iPhone 1.1.0), then others. Writing about how you feel should happen
  in your own language.
- **First-run welcome:** explain what to log and why, instead of an empty calendar (done in 2.4.0: the welcome tour).
- **Privacy policy:** a short, plain one. The stores require it, and "your data never
  leaves your devices" is Moodlog's strongest promise.
- **Accessibility:** screen-reader labels and full keyboard use.

## Step 2 – Every Linux distro (next)

- Publish Moodlog as a **Flatpak on Flathub**, so it installs from the software centre
  of any distribution, not only Debian/Ubuntu-based ones.

## Step 3 – iPhone and Mac stores (later)

- **Pick the store name.** "MoodLog - Simple" already exists on the App Store in the
  same category, so Moodlog needs a name that can't be confused with it.
- **Apple developer account** (paid yearly). This also ends the 7-day refresh of
  SideStore/AltStore installs.
- **iCloud sync**, so the app works without a Linux PC at home. It stays private: no
  servers of our own. Sync with the PC stays as an option.
- **iPhone app on the App Store**, through TestFlight first. Health apps get a careful
  review: no medical claims, and a clear reason for each Health permission.
- **Mac app** built from the same SwiftUI code as the iPhone app.
- Builds and uploads keep running on GitHub's Macs, so no Mac is needed.

## Step 4 – Windows (later)

- Decide between packaging the current app for Windows (faster) and a native Windows
  app (feels more at home).
- Publish on the **Microsoft Store**.
- Same data files and sync as the other versions, so people can move between devices.

---

The timeline image is drawn by `tools/roadmap_image.py`; run it again after changing
the steps.
