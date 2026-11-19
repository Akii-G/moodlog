# Changelog

Moodlog for the PC and Moodlog for iPhone have their own version numbers. Each GitHub release is named after the PC version and includes both apps.

## Moodlog for the PC

### 2.5.1 (2026-11-19)

- A privacy policy: ☰ menu → Privacy shows in short what Moodlog does with your data (nothing leaves your devices except the encrypted phone sync), with a link to the full policy.
- The phone sync receiver no longer writes the address of a request to the system log in full, so a token sent in the address can't end up there.

### 2.5.0 (2026-11-19)

- Encrypted phone sync: Moodlog for iPhone 1.4 encrypts everything it sends with your token, and the token itself never crosses the network. Phone sync shows whether the last connection was encrypted, and has a new Change token button.

### 2.4.0 (2026-11-19)

- A welcome tour for new users: a few friendly, animated steps that point at each part of the window and explain it. It opens by itself on a new install; see it any time from the ☰ menu → Show the tour.

### 2.3.2 (2026-11-19)

- Doctor report: “Also what the dreams were about” can always be ticked, and ticking it includes dreams too. Before, it stayed greyed out until “Include dreams” was ticked, so it was easy to miss.

### 2.3.1 (2026-11-19)

- After an update, the phone sync receiver switches to the new version by itself. Before, it kept running the old one until it was restarted, so new things such as dreams did not sync.

### 2.3.0 (2026-11-19)

- Dream journal: a new Dreams tab. Write down a dream with how it felt and tags such as nightmare or vivid, or note that you didn't remember one. Dreams show as a moon in the calendar, with a summary in the tab and in Charts, and sync with the iPhone.
- The doctor report can include dreams: off by default, then just how many and the nightmare nights, or also what they were about.
- Tabs take less room, so the window fits in half a screen again.

### 2.2.0 (2026-11-19)

- Moodlog speaks Italian. It follows your system language, or choose one in the ☰ menu → Language. Dates, charts, the doctor report and exports are translated too.

### 2.1.0 (2026-11-19)

- A “What's new” window opens once after each update, with the full changelog. Open it again any time from the ☰ menu.

### 2.0.1 (2026-11-19)

- Tahoe style: the selected Energy and Anxiety numbers were invisible (white on white). They are blue again, in light and dark.
- Screenshots of the app are now in the README.

### 2.0.0 (2026-11-19)

- Moodlog for iPhone: a new app that syncs moods, energy, anxiety, notes and events with Moodlog on your PC over your home Wi-Fi.
- Every entry and event now has an ID and a change time: the newest change wins, deletions sync too, and nothing is overwritten when two devices save at once.
- Saving merges changes written meanwhile by the phone or another computer.
- Your existing data is upgraded automatically.

### 1.9.1 (2026-11-19)

- Tahoe style: the top bar, tabs and buttons now change too when a system theme is installed in ~/.config/gtk-4.0.

### 1.9.0 (2026-11-19)

- Two styles in the new ☰ menu: Default (GNOME) and Tahoe (macOS-like, with frosted cards, pill buttons and red/yellow/green window buttons).

### 1.8.0 (2026-11-18)

- Energy and anxiety: two optional 1–5 scales on every entry, with their own chart.
- Report for your doctor: a printable PDF for a chosen period, with charts, events and a day-by-day table. Notes are only included if you ask.
- Backup and restore as one .zip file; restoring saves your current data first.
- Data folder and sync: keep your data in a Syncthing or Nextcloud folder to use Moodlog on two computers, with merging of conflict copies.

### 1.7.1 (2026-11-18)

- Fixed sleep showing 24 hours every night when the shortcut sent dates without a time.

### 1.7.0 (2026-11-18)

- Export to a spreadsheet (.xlsx) with coloured moods, a summary sheet and charts.

### 1.6.0 (2026-11-18)

- Export everything to a CSV file, one row per day.

### 1.5.0 (2026-11-17)

- Events: mark things like a medication change. They show as a flag in the calendar and a line on the charts, with your mood in the two weeks before and after.

### 1.4.0 (2026-11-17)

- Days with several moods show a small grid of tiles in the calendar, one per entry.

### 1.3.0 (2026-11-14)

- Several moods a day, each with its own time.
- New chart: average mood by time of day.

### 1.2.0 (2026-11-14)

- Link with the mood widget of the Gridgets GNOME extension.

### 1.1.0 (2026-11-13)

- Phone sync: an iPhone Shortcut sends sleep, steps and heart rate to Moodlog.
- New chart comparing your mood with your health data.

### 1.0.0 (2026-11-13)

- First version: a mood and a note each day, a month calendar, charts and a notes list, all saved in a plain CSV file.

## Moodlog for iPhone

### 1.4.1 (2026-11-19)

- A privacy policy: Settings → Privacy shows in short what Moodlog does with your data, with a link to the full policy.

### 1.4.0 (2026-11-19)

- Encrypted sync: everything sent to your PC is encrypted with your token (AES-256-GCM), and the token itself never leaves the phone. Needs Moodlog 2.5 or newer on the PC.

### 1.3.0 (2026-11-19)

- A welcome tour for new users: it switches to each tab by itself and explains it, with a spotlight on the calendar and a line pointing at the tab. Watch it again from Settings → Show the tour.

### 1.2.0 (2026-11-19)

- Dream journal: a new Dreams tab, a moon in the calendar on nights with a dream, and the night's dreams in each day. Dreams sync with Moodlog 2.3 on the PC.

### 1.1.0 (2026-11-19)

- Moodlog for iPhone speaks Italian. It follows the iPhone's language, or choose one in Settings → Moodlog → Language.

### 1.0.1 (2026-11-19)

- Days with several moods show a face on each tile of the calendar, like on the PC.

### 1.0.0 (2026-11-19)

- First version: log moods with energy, anxiety and a note, add events, browse the calendar, and sync everything with Moodlog on your PC over your home Wi-Fi.

