import csv, datetime as dt, random, sys, os
random.seed(11)
out = sys.argv[1]; os.makedirs(out, exist_ok=True)
today = dt.date.today()
notes = ["Slept badly, tired", "Long walk", "Played on the server with friends", "Nothing much", "Good food", "Headache", ""]
rows = []
d = today - dt.timedelta(days=75)
while d <= today:
    if random.random() > 0.12:
        base = random.choice([2, 3, 3, 4, 4, 5])
        times = sorted(random.sample(["08:10", "10:45", "13:20", "16:05", "19:30", "22:40"], random.choice([1, 1, 2, 3])))
        if d == today: times = ["08:20", "11:05"]
        for t in times:
            hour = int(t[:2])
            mood = max(1, min(5, base + (1 if 12 <= hour < 20 else -1 if hour >= 22 else 0) + random.choice([-1, 0, 0, 1])))
            rows.append([d.isoformat(), t, mood, random.choice(notes) if random.random() < 0.4 else ""])
    d += dt.timedelta(days=1)
with open(os.path.join(out, "moods.csv"), "w", newline="", encoding="utf-8") as f:
    w = csv.writer(f); w.writerow(["date", "time", "mood", "note"]); w.writerows(rows)
