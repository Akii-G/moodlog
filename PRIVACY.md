# Moodlog privacy policy

*Leggi in italiano: [PRIVACY.it.md](PRIVACY.it.md)*

Last updated: 21 November 2026. This covers Moodlog for the PC 2.10.0, Moodlog for iPhone 1.8.1 and Moodlog for Android 1.4.0, and newer.

## The short version

- **Nobody collects your data.** Moodlog has no account, no server, no analytics, no ads and no tracking. I, the developer, never see anything you write.
- **Your data stays on your own devices:** your PC and, if you use one, your phone.
- **Only two things travel, and only if you turn them on:** phone sync, from your phone straight to your PC on your own network, encrypted; and a backup file in a place you pick, such as Google Drive or iCloud Drive.
- **Neither app connects to the internet.**

## Who makes Moodlog

Moodlog is a free, open-source personal project by Giosuè ([Akii-G on GitHub](https://github.com/Akii-G)). It isn't a company and doesn't sell anything. All the code is public in [this repository](https://github.com/Akii-G/moodlog), so anyone can check what this page says.

## What Moodlog stores, and where

Everything you enter: moods and their notes, tags, events (such as a medication change), dreams, and, if you turn it on, health data from your phone.

- **On the PC**, it lives in plain files in your data folder: `~/.local/share/moodlog/` unless you chose another folder. The files are `moods.csv`, `events.csv`, `dreams.csv`, `health.csv` and, if you use medication from Apple Health, `medications.json`. Questionnaires you answer (PHQ-9, GAD-7) are in `surveys.json`. Your settings are in `~/.config/moodlog/config.json`. The phone sync token is in `sync.json`, and a one-line note about the last sync is in `sync-last.txt`.
- **On the iPhone and on Android**, it lives in the app's private storage, which other apps can't read.

The files on the PC aren't encrypted by Moodlog. They are protected by your user account, and by disk encryption if you turned it on when you installed your system. If you keep your data folder in a folder that another program syncs (for example Syncthing, Nextcloud or Dropbox), that program, and any server it uses, will hold a copy under its own privacy policy.

If your iPhone backs up to iCloud or to a computer, the backup includes Moodlog's data, as it does for every app. Apple handles those backups. The same goes for Android's own backup to your Google account, if it's on: Google handles it, encrypted with your screen lock if your phone has one.

## What leaves your device

**Phone sync (optional).** If you set it up, the phone app sends your entries to the receiver on your PC and gets the PC's entries back.
- This happens only between your own devices, over your own network. It never goes through the internet or any server of mine.
- Everything is encrypted with AES-256-GCM, using a key made from your sync token. The token itself is never sent.
- The receiver listens on port 8765 of your PC and answers only requests made with your token.
- If your Android phone can't find an address like `pop-os.local` by itself, Moodlog asks your local network for it (the same way your PC announces its name). That question never leaves your Wi-Fi.
- Android lists this as the "full network access" permission: it is only used for your own PC.

**Health data (optional, off by default).** The iPhone app reads health data only after you allow it in Apple Health, and only when you turn on "Send with each sync" or tap "Send the last 7 days now" (iPhone Settings → Apple Health). It then reads:
- your steps,
- your resting heart rate,
- your sleep,

all for the last 7 days, and sends them to your PC with phone sync. Moodlog never writes to Apple Health.

**Medication (optional, off by default, iPhone with iOS 26 or newer).** If you turn on "Read my medications from Apple Health" (iPhone Settings → Medication), Apple Health asks you which medications Moodlog may read. Moodlog then reads those medications and whether you took or skipped each dose, and shows them on each day and in the report for your doctor. When it sends health data to your PC, it also sends the medication doses (the whole history the first time, then the last 30 days). Moodlog never writes to Apple Health and never suggests anything about a medication. You can change which medications it may read in the Health app.

On Android it works the same way with Health Connect: the app reads steps, resting heart rate and sleep for the last 7 days only after you allow it in Health Connect, and only when you turn on "Send with each sync" or tap "Send the last 7 days now" (Settings → Health Connect). It never writes to Health Connect. Health data is used only to show it next to your mood. It is never used for ads, never sold and never given to anyone.

**The Apple Shortcut (optional, older way of sending health data).** Shortcuts can't encrypt. If you use it, its data and token travel across your network unencrypted, so use it only on a network you trust. The iPhone app's own sync is the encrypted way.

**Backup (optional).** On the phones you can pick a file or folder, for example in Google Drive or iCloud Drive. Moodlog then keeps a copy of everything there after each change. Moodlog only writes the file; the Drive app or iCloud Drive uploads it, under Google's or Apple's privacy policy. Stop it any time in Settings → Backup.

**Reports and exports.** The PDF report, the spreadsheet and the CSV file are saved where you choose. What happens to them next (sending them to your doctor, for example) is up to you. A note you tick **Private** never goes in the report, even when you include your notes; a private dream is only counted there, and what it was about is never shown. Private notes and dreams stay in your own data, backups and sync like everything else.

**Reminder and kind word.** The daily reminder is a notification made on your own device; nothing is sent anywhere. The kind word on hard days is worked out on your device from what you logged; it never tells anyone, and tapping a helpline only opens your phone app or browser.

**Links.** A few buttons, such as "Read the full policy", open a page in your web browser. That only happens when you click them, and then the browser and the website's own policies apply.

## What the receiver writes to the system log

For each request, the receiver writes one line to your PC's system log (the journal): the time, your phone's local address, and which part of the receiver was asked (for example `/api/e/sync`). It never writes your entries, your health data or your token.

## Your control

- **See everything:** your data are ordinary CSV files. Open them from ☰ → Open data folder.
- **Take it with you:** back it up or export it at any time.
- **Delete it:** delete an entry inside the app. To delete everything, delete the data folder on the PC (and `~/.config/moodlog/`), and delete the app on the phone.
- **Stop syncing:** clear the PC address in the phone app's Settings, or stop the receiver with `systemctl --user disable --now moodlog-sync.service`. Click "Change token…" in the PC's Phone sync window to make the old token useless.

## Children

Moodlog isn't aimed at children, and it collects nothing from anyone.

## Changes

If Moodlog ever starts handling data differently, this page will change first, and the change will be listed in the app's "What's new". The date at the top always shows the latest version.

## Questions

Open an [issue on GitHub](https://github.com/Akii-G/moodlog/issues).
