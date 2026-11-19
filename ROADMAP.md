# Moodlog roadmap

![Moodlog roadmap](docs/roadmap.png)

**Goal:** help people who track their mood for their health (for example to show a
doctor or therapist how they have been, or how a treatment change went) with an app
that keeps their data private, on every device they use.

The steps are in order: each one builds on the one before. There are no dates; a step
starts when the previous one is done.

## Done (November 2026)

- Moodlog for the PC, 1.0 → 2.6: moods with notes, energy and anxiety, events, charts,
  the doctor report, CSV/XLSX export, backup and restore, two styles.
- Moodlog for iPhone, 1.0 → 1.4: logging on the phone, synced with the PC over home Wi-Fi,
  plus sleep, steps and heart rate from Apple Health.
- Moodlog for Android, 1.0: the same as the iPhone app, with Health Connect.
- English and Italian, a dream journal, and a welcome tour on all three.
- No account and no cloud: everything is in plain files on your own devices, and the
  phone sync is encrypted.
- A plain [privacy policy](PRIVACY.md).

## Step 1 – Solid for its users (now)

- **Accessibility:** screen-reader labels and full keyboard use.
- **More languages**, after English and Italian. Writing about how you feel should
  happen in your own language.

## Step 2 – Every Linux distro (next)

- Publish Moodlog as a **Flatpak on Flathub**, so it installs from the software centre
  of any distribution, not only Debian/Ubuntu-based ones.

## Step 3 – Windows (later)

- A Windows installer, built by GitHub for every release, so no Windows PC is needed
  to make it.
- Publish on the **Microsoft Store**.
- Same data files and sync as the other versions, so people can move between devices.

## Not planned

- **Apple's App Store and Mac App Store.** Moodlog for iPhone stays a free download from
  the [releases](https://github.com/Akii-G/moodlog/releases), installed with SideStore
  or AltStore.
- **Google Play, for now.** Moodlog for Android is a free `.apk` from the releases.

---

The timeline image is drawn by `tools/roadmap_image.py`; run it again after changing
the steps.
