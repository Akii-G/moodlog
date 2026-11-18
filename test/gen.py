import csv, datetime as dt, random, sys, os
random.seed(7)
out = sys.argv[1]; os.makedirs(out, exist_ok=True)
today = dt.date(2026, 11, 13)
notes = ["Slept badly, tired all day", "Long walk in the afternoon", "Played with friends on the server",
         "Stayed in, nothing much", "Good food, good evening", "Headache", ""]
rows = []
d = dt.date(2026, 7, 20)
m = 3
while d <= today:
    if random.random() > 0.15:
        m = max(1, min(5, m + random.choice([-1, -1, 0, 0, 1, 1])))
        rows.append([d.isoformat(), m, random.choice(notes) if random.random() < 0.5 else ""])
    d += dt.timedelta(days=1)
rows.append(["2026-11-02", "", "note without a mood, with a comma, and \"quotes\""])
with open(os.path.join(out, "moods.csv"), "w", newline="", encoding="utf-8") as f:
    w = csv.writer(f); w.writerow(["date", "mood", "note"]); w.writerows(rows)
