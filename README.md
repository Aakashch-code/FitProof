# FitProof – Hackathon Edition

## Project Overview

FitProof is a fitness tracking and verification app I built for the **Proof of Concept 2025 Hackathon**. It connects to **Google Fit** to fetch workout data (steps, distance, heart rate & pace) and adds a simple verification workflow so users can prove their workouts are real. My motivation was to solve the issue of fake or manipulated workout screenshots by creating a transparent way to sync and verify activity.

---

## Key Features

* **Workout Verification** – Generates a unique proof code for each session, secured with SHA-256 hashing.
* **Google Fit Integration** – Pulls live fitness data directly from Google Fit APIs.
* **Clean UI** – Two main actions: **SYNC NOW** (fetch latest stats) and **VERIFY NOW** (lock and verify).
* **Independent Verifier Tool** – I built a small standalone verifier where proof codes can be checked for authenticity.

---

## How It Works

1. **Login** – Secure sign-in with Google account.
2. **Sync** – Pressing **SYNC NOW** pulls the latest metrics. Example: `Last Sync: Aug 28, 2025 09:18 IST`.
3. **Verify** – Pressing **VERIFY NOW** locks the session and generates a proof code.
4. **Check** – The proof code can be pasted into the standalone verifier to confirm authenticity.

---

## Technical Notes

* **Data Source**: Exclusively Google Fit.
* **Privacy**: No personal data is stored—only fitness metrics and hashes are used.
* **Independence**: The verifier tool works without needing the main app.
* **Platform**: Built for Android using **Android Studio (Java + XML)**.

---

## Hackathon Reflection

Since I worked on this solo, the biggest challenge was balancing UI design with backend logic in a short time frame. The Google Fit API setup took longer than expected, but once it was integrated, verification using SHA-256 hashing worked smoothly.

---

## Future Roadmap

* Improve real-time sync (reduce refresh delays).
* Add more metrics like sleep and hydration.
* Build a simple web-based verifier for broader use.
* Experiment with ML-based insights for personal fitness recommendations.

---

## Contact

If you’d like to check out or collaborate on FitProof, you can reach me at **[aakashch.code@gmail.com](mailto:aakashch.code@gmail.com)**.

