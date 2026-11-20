# Moodlog

A small, private mood tracker for Linux, with companion apps for iPhone and Android. Log how
you feel in one click, see how things go over weeks and months, mark events such as
a medication change, keep a dream journal, and bring a clear PDF report to your
doctor. No account and no cloud: everything stays in plain files on your devices.

In English and Italian. A short welcome tour explains everything the first time
you open it.

![The Log tab: the month calendar and the selected day](docs/screenshots/log.png)

*Screenshots of the real app with made-up sample data.*

## Contents

- [What you can do](#what-you-can-do)
  - [Log your mood](#log-your-mood)
  - [See how things go](#see-how-things-go)
  - [Mark what matters](#mark-what-matters)
  - [Keep a dream journal](#keep-a-dream-journal)
  - [Bring a report to your doctor](#bring-a-report-to-your-doctor)
  - [A gentle reminder, and a kind word](#a-gentle-reminder-and-a-kind-word)
  - [Use it on your iPhone too](#use-it-on-your-iphone-too)
  - [Or on your Android phone](#or-on-your-android-phone)
  - [Make it yours](#make-it-yours)
- [Install](#install)
- [Your data](#your-data)
- [For developers](#for-developers)
- [A word from the developer](#a-word-from-the-developer)
- [Licence](#licence)

## What you can do

### Log your mood

Pick one of five faces, from Awful to Great. That's the whole job. Energy, anxiety
(1–5) and a note are optional. A day can hold several entries, each with its own
time, and you can fill in any past day from the calendar. Each day takes the
colour of its mood; a day with several shows one small tile per entry.

The **Notes** tab lists every note, newest first, with search.

### See how things go

The **Charts** tab works per month, per year or for all time: average mood, days
logged, most common mood, your streak, mood day by day, how often each mood,
average by weekday and by time of day, energy and anxiety, and your mood next to
sleep and steps from your iPhone.

![The Charts tab in dark mode](docs/screenshots/charts-dark.png)

### Mark what matters

Log events such as a medication change or something big that happened. They show
as a flag in the calendar and a dashed line in the charts, and each one shows your
average mood in the two weeks before and after it.

![The Events tab](docs/screenshots/events.png)

### Keep a dream journal

The **Dreams** tab is a dream journal. Each dream belongs to the morning you woke
up: write what you remember, pick how it felt and tick tags such as nightmare,
vivid, recurring or lucid ("Didn't remember" counts too). Nights with a dream show
a moon in the calendar, and once there is enough data Moodlog compares your mood
on days after a disturbing dream with other days. Dreams are private: the doctor
report leaves them out unless you ask.

![The Dreams tab](docs/screenshots/dreams.png)

### Bring a report to your doctor

**Export → Report for your doctor (.pdf)** makes a printable A4 report for the
period you choose: key numbers, your mood over time with your events marked, energy
and anxiety, sleep, your mood around each event, and a day-by-day table. Notes and
dreams are left out unless you tick them.

<img src="docs/screenshots/report.png" alt="First page of the doctor report" width="600">

The phone apps make the same report (the document button in **Charts**), to share,
print or save straight from the phone.

The **Export** menu also saves everything as a spreadsheet (.xlsx, with colours and
charts) or a plain CSV file, which open in LibreOffice Calc, Excel or Google Sheets.

### A gentle reminder, and a kind word

If you like, Moodlog reminds you once a day at the time you choose, only on days you
haven't logged a mood yet: a quiet notification on the PC (☰ → **Reminder and kind
word**) or the phone (**Settings → Reminder**). No streaks, no guilt.

After a few very hard days close together, the Log shows a short, kind note with a
helpline (Telefono Amico in Italian, Find A Helpline in English). It never tells
anyone, and you can hide it or turn it off.

### Use it on your iPhone too

**Moodlog for iPhone** logs moods, energy, anxiety, notes, events and dreams on
your phone, and syncs them with Moodlog on your PC over your home Wi-Fi, even if
you logged while away. It can also send sleep, steps and resting heart rate from
Apple Health. Every [release](https://github.com/Akii-G/moodlog/releases) includes
it as `Moodlog-iPhone-<version>.ipa`; see [`ios/README.md`](ios/README.md) to install
it with SideStore or AltStore.

<img src="docs/screenshots/iphone-log.png" alt="Moodlog for iPhone" width="300">

To connect it, open **Phone sync** on the PC, switch the receiver on, and enter the
address and token shown there in the iPhone app's Settings.

On iOS 26 or newer, Moodlog for iPhone can also read the medications you log in the
Health app (**Settings → Medication**): each day shows the doses you took or skipped,
the report for your doctor gets a Medication section, and your PC receives them with
the health data. Moodlog only reads them and never changes anything in Health.

On both phones, **Charts** also shows your sleep night by night next to your mood,
and **Settings → Backup** keeps a copy of everything in iCloud Drive or Google Drive
(or any folder you pick), so the phone app works on its own, without a PC. A backup
from one phone restores on the other.

### Or on your Android phone

**Moodlog for Android** does the same: moods, energy, anxiety, notes, events and
dreams, synced with your PC over Wi-Fi, encrypted. It can send sleep, steps and
resting heart rate from Health Connect, which most watches and fitness apps share
with. Every [release](https://github.com/Akii-G/moodlog/releases) includes it as
`Moodlog-Android-<version>.apk`: open it on your phone to install it (see
[`android/README.md`](android/README.md)).

<img src="docs/screenshots/android-log.png" alt="Moodlog for Android" width="300">

Connect it the same way: **Phone sync** on the PC, then the address and token in the
app's Settings.

<details>
<summary>Prefer a Shortcut for Apple Health instead of the app?</summary>

A ready-made shortcut sends the last 7 days of steps, resting heart rate and sleep:
**[Get "Moodlog Sync" for Shortcuts](https://www.icloud.com/shortcuts/db962227cb6547b4b480a1060afb0b9f)**

1. In Moodlog, open **Phone sync** and switch the receiver on. Keep that window
   open for the address and token.
2. Open the link on your iPhone and tap **Get Shortcut**. For **Address** enter the
   address shown in Phone sync (for example `http://your-pc.local:8765/health`); for
   **Header**, add `Authorization` with the value `Bearer ` followed by your token.
3. Run it once and allow access to Health. If iOS says it can't share that many
   Health items, turn on **Allow Sharing Large Amounts of Data** in Settings → Apps →
   Shortcuts → Advanced.
4. To sync every day: Shortcuts → **Automation** → **+** → **Time of Day**, pick a time
   when you usually use your phone, choose **Run Immediately** and select the shortcut.

The iPhone and the PC must be on the same Wi-Fi and the PC must be on. Missed days
are filled in on the next run.
</details>

### Make it yours

The ☰ menu switches between two styles, **Default** (GNOME) and **Tahoe**
(macOS-like, with frosted cards and pill buttons), both in light and dark. It also
has the language (English, Italian, or your system's), what's new after each update,
and the welcome tour.

![The Tahoe style, light](docs/screenshots/tahoe-light.png)

![The Tahoe style, dark](docs/screenshots/tahoe-dark.png)

The tour dims the window except for the part it explains, and walks through
everything in a few calm steps. The phone apps have their own, which switches to
each tab as it goes.

![The welcome tour](docs/screenshots/tour.png)

## Install

Download `moodlog_<version>_all.deb` from the
[latest release](https://github.com/Akii-G/moodlog/releases/latest), then:

    sudo apt install ./moodlog_2.8.0_all.deb

Remove it with `sudo apt remove moodlog`; your data is kept. For the phone apps, see
[`ios/README.md`](ios/README.md) and [`android/README.md`](android/README.md).

## Your data

Everything is in plain CSV files in `~/.local/share/moodlog/`, or in a folder you
choose: `moods.csv`, `events.csv`, `dreams.csv` and `health.csv`. Nothing leaves your
devices, there is no account, and the phone only talks to your own PC, encrypted.
The [privacy policy](PRIVACY.md) ([in italiano](PRIVACY.it.md)) says exactly what is
stored and sent; in the app it's under ☰ → Privacy.

- **Back up and restore**: the folder button in the title bar makes one .zip with
  all your data and restores it (your current data is saved first).
- **Two computers**: *Data folder and sync…* can keep your data in a folder that
  Syncthing or Nextcloud syncs. Moodlog merges both computers' data and any conflict
  copies, so nothing is lost.
- **Gridgets**: if you use the [Gridgets](https://github.com/rebatnaath/gridgets)
  GNOME extension, Moodlog links with its mood widget both ways (switch it off at
  the bottom of the Log tab).

<details>
<summary>File formats and the phone sync receiver</summary>

- `moods.csv`: `date,time,mood,energy,anxiety,note,id,updated,deleted`, one row per
  entry; mood is 1 (awful) to 5 (great), energy and anxiety 1–5 or empty.
- `events.csv`: `date,kind,title,note,id,updated,deleted`.
- `dreams.csv`: `date,feeling,tags,text,id,updated,deleted` (date = the morning you
  woke up).
- `health.csv`: `date,metric,value`.
- Changes carry an id and a time, so the newest change wins and deletions sync too.

The receiver is `moodlog --sync-server`, run as the systemd user service
`moodlog-sync.service` on port 8765, and restarts itself after an update.

- **Moodlog for iPhone (1.4+) is encrypted end to end:** every request and reply is
  sealed with AES-256-GCM using a key derived from your token (HKDF-SHA256), on
  `POST /api/e/ping`, `/api/e/sync` and `/api/e/health`. The token itself is never
  sent, so someone on the same Wi-Fi sees only scrambled data.
- **The Shortcut can't encrypt:** `POST /health` takes the token in an
  `Authorization` header and the data in clear (text lines `metric;date;value` and
  `sleep;start;end;stage`, JSON `{"data": "<those lines>"}`, JSON
  `{"date": ..., "steps": ..., ...}`, or Health Auto Export's REST format, untested).
  Use it only on a network you trust. Phone sync shows whether the last connection
  was encrypted, and **Change token…** makes a new token at any time.

From a terminal: `moodlog --export FILE.xlsx` (or `.csv`) and
`moodlog --report FILE.pdf --from 2026-09-01 --to 2026-11-30` (add `--notes`,
`--dreams` or `--dream-text`).
</details>

## For developers

Moodlog for the PC is one Python file, `src/moodlog` (GTK 4 and libadwaita); the
iPhone app is SwiftUI, in `ios/`, and the Android app is Kotlin and Jetpack Compose,
in `android/`. GitHub Actions builds both phone apps.

    sudo apt install python3-gi python3-gi-cairo gir1.2-gtk-4.0 gir1.2-adw-1 python3-openpyxl
    ./src/moodlog          # run from source
    ./build-deb.sh         # build the .deb

The scripts in `test/` check storage, merging, sync, events, dreams, the Gridgets
link and the translations (`python3 test/test_store.py` and so on); the `ui*.py` and
`interact.py` scripts drive the real window and need a display (or `xvfb-run`).
`test/test_android_sync.py` syncs the Android app's code with the real PC receiver.

**Translations** are gettext `.po` files in `po/` (PC), `.lproj` folders in
`ios/Moodlog/` (iPhone) and `.strings` files in `android/app/src/main/assets/i18n/`
(Android, the same format as the iPhone's). To add a language, copy `po/moodlog.pot`
to `po/<code>.po` and fill in the `msgstr` lines (Poedit works), add the code to
`LANGUAGES` in `src/moodlog`, copy `ios/Moodlog/it.lproj` to `<code>.lproj` and
`it.strings` to `<code>.strings`. `tools/extract_strings.py --check`,
`tools/extract_ios_strings.py --check` and `tools/extract_android_strings.py --check`
list anything still missing.

Changes are in [CHANGELOG.md](CHANGELOG.md).

## A word from the developer

Hi, I'm Giosuè. I wanted to explain briefly why I made this app: I fell into
depression about 4 years ago, and I could never remember how my days felt. I also
had a lot of mood swings through the day, and most mood trackers are only for your
phone and only allow one entry. That's mainly why. Now it has become a project I
like working on.

### A word about Claude

Yes, I'm using Claude Opus to code this. I was a developer before, but I lost all
passion for it.

## Licence

MIT
