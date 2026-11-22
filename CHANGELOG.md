# Changelog

Moodlog for the PC, Moodlog for iPhone and Moodlog for Android have their own version numbers. Each GitHub release is named after the PC version and includes all three apps.

## Moodlog for the PC

### 2.12.1 (2026-11-22)

- Feeling words: the ones you pick now light up in the Tahoe style too.
- Reminder, kind word and the Gridgets link are now in ☰ → Customize your experience, and they apply at once.

### 2.12.0 (2026-11-21)

- Moodlog for Windows 11 and 10: the same app, with phone sync, the daily reminder as a Windows notification, and an installer. Your data is in %LOCALAPPDATA%\Moodlog.

### 2.11.0 (2026-11-21)

- Feeling words: next to a mood you can pick words like Joy, Tiredness or Worry, more precise than five faces. They show on the day, sync with the phones, and the report lists the ones you picked most often.
- Writing prompts: an empty note shows a gentle question, a different one each day, with a button for another. Never required. Both can be turned off in Customize.

### 2.10.0 (2026-11-21)

- Questionnaires for your doctor (☰ menu): PHQ-9 about low mood and GAD-7 about anxiety, with the official words in English and Italian. Moodlog shows only your score, a chart of scores over time, and the report can include them. Two weeks after the last one, a small card on the Log page asks if you'd like to answer again.
- Charts: "Most common" now counts every entry, like the chart of how often you logged each mood and like the phones. Before, it counted each day's average, so a tie between days could show a different mood from the chart.

### 2.9.0 (2026-11-21)

- Private notes and dreams: tick Private on a note and it never goes in the doctor report; a private dream is only counted there, never shown. In Customize you can hide the tick, or make new ones start private.
- Customize your experience (☰ menu, the tour, and once after updating): turn off Dreams or Medication, and choose whether days with several moods show all of them in a grid or only the latest.

### 2.8.1 (2026-11-21)

- Medication: a Medication button on each day opens the list of that day's doses, each with a pill in the colour of its status, the time, the amount and Taken or Skipped.

### 2.8.0 (2026-11-20)

- Medication from Apple Health: with Moodlog for iPhone 1.6, the medications you log in the Health app and the doses you took or skipped arrive with the health data. They show on each day, and the report for your doctor has a Medication section.

### 2.7.0 (2026-11-20)

- A daily reminder (☰ → Reminder and kind word): one quiet desktop notification at the time you choose, only on days you haven't logged a mood yet.
- A kind word on hard days: after a few very hard days close together, the Log shows a short, kind note with a helpline. It never tells anyone, and you can hide it or turn it off.

### 2.6.1 (2026-11-20)

- Moodlog for Android 1.1.0 is out: a new look like the iPhone app, a taller calendar, and it finds this PC more reliably when pop-os.local doesn't work on the phone. Nothing changes on the PC.

### 2.6.0 (2026-11-19)

- Moodlog for Android: a new app for Android phones that syncs moods, energy, anxiety, notes, events and dreams with this PC, encrypted, and sends sleep, steps and resting heart rate from Health Connect. Phone sync has the steps to set it up.

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

### 1.9.0 (2026-11-21)

- Feeling words: next to a mood you can pick words like Joy, Tiredness or Worry. They show on the day, sync with the PC, and the report lists the ones you picked most often.
- Writing prompts: an empty note shows a gentle question, a different one each day; tap Another question for a new one. Both can be turned off in Customize.
- Medication: when Health has nothing new to ask, Choose which medications… now says so, and where to change your choice.

### 1.8.1 (2026-11-21)

- Sync: questionnaires answered on the iPhone now go to the PC. In 1.8.0 they stayed on the phone.

### 1.8.0 (2026-11-21)

- Questionnaires for your doctor (Settings): PHQ-9 about low mood and GAD-7 about anxiety, with the official words. Moodlog shows only your score and a chart of scores over time; the report can include them, and they sync with the PC. A notification two weeks after the last one reminds you, if you want.

### 1.7.0 (2026-11-21)

- Private notes and dreams: a private note never goes in the doctor report, and a private dream is only counted there. They sync with the PC and Android.
- Customize your experience (Settings, the tour, and once after updating): turn off Dreams or Medication, and choose whether days with several moods show all of them in a grid or only the latest.

### 1.6.7 (2026-11-21)

- Backup: the screen for saving or restoring the backup file now always has its Save button.

### 1.6.6 (2026-11-21)

- Backup: Settings warns when Moodlog can't reach the backup file any more (for example after installing a new version) and says why. Doses not taken yet are no longer sent: Health doesn't list them in advance.

### 1.6.5 (2026-11-21)

- Medication: each day lists the doses with a pill in the colour of their status, the time and the amount.

### 1.6.4 (2026-11-20)

- Medication: Moodlog asks Health for the names in more ways, and when Health still doesn't give them, the naming screen is laid out like Health's medication log: grouped by time, then as needed.

### 1.6.3 (2026-11-20)

- Medication: when Health doesn't give the names, you can name each medication once (Settings → Medication → Name your medications). Backup: Moodlog now overwrites the backup file in place, so it can keep it up to date in iCloud Drive and on the iPhone.

### 1.6.2 (2026-11-20)

- Medication: the doses show even when Health doesn't list the medications they belong to (they're numbered then), and Moodlog asks Health for the names in a second way.

### 1.6.1 (2026-11-20)

- Medication: Settings now says what Moodlog can see (medications, doses in the last 30 days) or the exact error from Health, and where to change your choice when Health doesn't ask again.

### 1.6.0 (2026-11-20)

- Medication from Apple Health (iOS 26 or newer): turn it on in Settings, and each day shows the doses you took or skipped. The report for your doctor gets a Medication section, and your PC receives them with the Health data. Moodlog only reads them: it never changes anything in Health.

### 1.5.0 (2026-11-20)

- A daily reminder, only on days you haven't logged a mood yet; tapping it opens a new entry.
- The report for your doctor, now on the phone too: a PDF to share, print or save (the Charts tab).
- Sleep and mood: a new chart with your sleep from Apple Health and the mood after shorter and longer nights.
- Backup to a file in iCloud Drive or anywhere in Files, kept up to date after each change, and restore from it. The same file works on Android.
- A kind word on hard days, with a helpline.
- What's new: this list, shown once after each update and any time from Settings.

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

## Moodlog for Android

### 1.5.0 (2026-11-21)

- Feeling words: next to a mood you can pick words like Joy, Tiredness or Worry. They show on the day, sync with the PC, and the report lists the ones you picked most often.
- Writing prompts: an empty note shows a gentle question, a different one each day; tap Another question for a new one. Both can be turned off in Customize.

### 1.4.0 (2026-11-21)

- Questionnaires for your doctor (Settings): PHQ-9 about low mood and GAD-7 about anxiety, with the official words. Moodlog shows only your score and a chart of scores over time; the report can include them, and they sync with the PC. A notification two weeks after the last one reminds you, if you want.

### 1.3.0 (2026-11-21)

- Private notes and dreams: a private note never goes in the doctor report, and a private dream is only counted there. They sync with the PC and iPhone.
- Customize your experience (Settings, the tour, and once after updating): turn off Dreams, and choose whether days with several moods show all of them in a grid or only the latest.

### 1.2.0 (2026-11-20)

- A daily reminder, only on days you haven't logged a mood yet; tapping it opens a new entry.
- The report for your doctor, now on the phone too: a PDF to share, print or save (the Charts tab).
- Sleep and mood: a new chart with your sleep from Health Connect and the mood after shorter and longer nights.
- Backup to Google Drive or any file you pick, kept up to date after each change, and restore from it. The same file works on the iPhone.
- A kind word on hard days, with a helpline.
- What's new: this list, shown once after each update and any time from Settings.

### 1.1.0 (2026-11-20)

- A new look, like the iPhone app: big titles, white cards on a light grey page, a floating tab bar, iPhone-style switches and choices.
- The calendar is taller, and the date, flag and moon have their own strip at the top of each day, so they no longer cover the faces.
- Finding your PC: if pop-os.local doesn't work, the app now asks your Wi-Fi itself in more ways, and remembers the PC's address once it has found it.
- With large text, the tour's buttons no longer get squeezed, and the tab names fit.

### 1.0.0 (2026-11-19)

- The first version: log your mood, energy, anxiety and a note, a month calendar, charts, events, a dream journal, the welcome tour, English and Italian.
- Encrypted sync with Moodlog on your PC (2.5 or newer), over your own Wi-Fi.
- Sleep, steps and resting heart rate from Health Connect, sent to your PC if you turn it on.

