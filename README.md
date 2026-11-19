# Moodlog

A small, private mood tracker for Linux, with a companion app for iPhone. Log how
you feel in one click, see how things go over weeks and months, mark events such as
a medication change, keep a dream journal, and bring a clear PDF report to your
doctor. No account and no cloud: everything stays in plain files on your devices.

In English and Italian. A short welcome tour explains everything the first time
you open it.

![The Log tab: the month calendar and the selected day](docs/screenshots/log.png)

*Screenshots of the real app with made-up sample data.*

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

The **Export** menu also saves everything as a spreadsheet (.xlsx, with colours and
charts) or a plain CSV file, which open in LibreOffice Calc, Excel or Google Sheets.

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
everything in a few calm steps. The iPhone app has its own, which switches to each
tab as it goes.

![The welcome tour](docs/screenshots/tour.png)

## Install

Download `moodlog_<version>_all.deb` from the
[latest release](https://github.com/Akii-G/moodlog/releases/latest), then:

    sudo apt install ./moodlog_2.4.0_all.deb

Remove it with `sudo apt remove moodlog`; your data is kept. For the iPhone app, see
[`ios/README.md`](ios/README.md).

## Your data

Everything is in plain CSV files in `~/.local/share/moodlog/`, or in a folder you
choose: `moods.csv`, `events.csv`, `dreams.csv` and `health.csv`. Nothing leaves your
devices, there is no account, and the iPhone only talks to your own PC.

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
`moodlog-sync.service` on port 8765. It only accepts requests that carry your token
and restarts itself after an update. Traffic is plain HTTP, so use it on a network
you trust. It accepts `POST /api/sync` from the iPhone app and `POST /health` with
text lines `metric;date;value` (and `sleep;start;end;stage`), JSON
`{"data": "<those lines>"}`, JSON `{"date": ..., "steps": ..., ...}` or the Health
Auto Export app's REST format (untested).

From a terminal: `moodlog --export FILE.xlsx` (or `.csv`) and
`moodlog --report FILE.pdf --from 2026-09-01 --to 2026-11-30` (add `--notes`,
`--dreams` or `--dream-text`).
</details>

## For developers

Moodlog for the PC is one Python file, `src/moodlog` (GTK 4 and libadwaita); the
iPhone app is SwiftUI, in `ios/`, built by GitHub Actions.

    sudo apt install python3-gi python3-gi-cairo gir1.2-gtk-4.0 gir1.2-adw-1 python3-openpyxl
    ./src/moodlog          # run from source
    ./build-deb.sh         # build the .deb

The scripts in `test/` check storage, merging, sync, events, dreams, the Gridgets
link and the translations (`python3 test/test_store.py` and so on); the `ui*.py` and
`interact.py` scripts drive the real window and need a display (or `xvfb-run`).

**Translations** are gettext `.po` files in `po/` (PC) and `.lproj` folders in
`ios/Moodlog/` (iPhone). To add a language, copy `po/moodlog.pot` to `po/<code>.po`
and fill in the `msgstr` lines (Poedit works), add the code to `LANGUAGES` in
`src/moodlog`, and copy `ios/Moodlog/it.lproj` to `<code>.lproj`.
`tools/extract_strings.py --check` and `tools/extract_ios_strings.py --check` list
anything still missing.

Changes are in [CHANGELOG.md](CHANGELOG.md).

## Roadmap

![Moodlog roadmap](docs/roadmap.png)

Moodlog is for people who track their mood for their health and want to keep that
data private. Next: a plain privacy policy and accessibility, then Flathub for every
Linux distro, then the iPhone and Mac App Stores, then Windows. See
[ROADMAP.md](ROADMAP.md) for the details.

## Licence

MIT
