# SleepGuard — Build & Install Guide
### From your Samsung Chromebook → S25 Ultra

---

## What you're building

- **Blue screen** during your sleep window → tap to open Calm
- **Orange screen** when it's time to wake up
- **30-minute cycling countdown** so you never need to check the time
- Lives on your lock screen, no unlocking needed

---

## Step 1 — Enable Linux on your Chromebook

Your Chromebook can run Android Studio via Linux (built in, free).

1. Open **Settings** on your Chromebook
2. Go to **Advanced → Developers**
3. Click **"Turn on"** next to *Linux development environment*
4. Follow the setup wizard (takes ~5 minutes, installs automatically)
5. A **Terminal** app will appear in your launcher — open it

---

## Step 2 — Install Android Studio in Linux

In the Terminal, run these commands one at a time:

```bash
# Update packages
sudo apt update && sudo apt upgrade -y

# Install Java (required)
sudo apt install -y openjdk-17-jdk wget unzip

# Download Android Studio
wget https://redirector.gvt1.com/edgedl/android/studio/ide-zips/2023.3.1.18/android-studio-2023.3.1.18-linux.tar.gz -O android-studio.tar.gz

# Extract it
tar -xzf android-studio.tar.gz

# Launch it
./android-studio/bin/studio.sh
```

3. Android Studio will open. Go through the setup wizard:
   - Choose **Standard** install
   - Let it download the Android SDK (may take 10–20 mins on first run)

---

## Step 3 — Open the SleepGuard project

1. In Android Studio, choose **"Open"** (not New Project)
2. Navigate to the **SleepGuard** folder you downloaded
3. Click **OK** — Android Studio will sync the project (takes 1–2 mins)
4. Wait for the bottom bar to say **"Gradle sync finished"**

---

## Step 4 — Enable Developer Mode on your S25 Ultra

Do this on your **phone**:

1. Go to **Settings → About phone → Software information**
2. Tap **"Build number"** 7 times rapidly
3. You'll see *"Developer mode enabled"*
4. Go back to **Settings → Developer options**
5. Turn on **USB debugging**

---

## Step 5 — Connect your phone to your Chromebook

1. Use a **USB-C cable** to connect S25 to Chromebook
2. On your phone, a popup asks **"Allow USB debugging?"** — tap **Allow**
3. In Android Studio, look at the top toolbar — you should see your phone listed (e.g. *"Samsung S25 Ultra"*)

> If the phone doesn't appear: in Android Studio Terminal run `adb devices` — your phone's serial number should appear.

---

## Step 6 — Build and install

1. In Android Studio, click the **green ▶ Run button** (top right)
2. Select your S25 Ultra as the target device
3. Click **OK**

Android Studio will compile the app and install it directly onto your phone. The SleepGuard icon will appear in your app drawer.

---

## Step 7 — Set up SleepGuard on your phone

1. Open **SleepGuard** from your app drawer
2. Set your **Bedtime** (e.g. 22:30)
3. Set your **Wake time** (e.g. 07:00)
4. Tap **"Activate SleepGuard"**

That's it. The app runs silently in the background.

---

## Step 8 — Make it appear on the lock screen

On your S25 Ultra:

1. Go to **Settings → Lock screen → Widgets** (or *Lock screen apps*)
2. You may need to allow **"Display over other apps"** for SleepGuard:
   - Settings → Apps → SleepGuard → Special app access → Display over other apps → Allow

SleepGuard uses Android's built-in lock screen overlay system — when the timer ticks, the screen will show the blue or orange display.

---

## How to use it at night

| What you see | What it means | What to do |
|---|---|---|
| 🔵 Blue screen | Still in sleep window | Do breathing exercises |
| Timer at ~10:00 left | You've been awake ~20 mins | Tap screen → Calm opens |
| 🟠 Orange screen | Sleep window ended | Safe to get up |

**The 30-min timer restarts automatically** — you never need to touch it.

---

## Troubleshooting

**Phone not detected?**
- Try a different cable (some cables are charge-only, not data)
- Re-enable USB debugging on the phone

**Lock screen not showing?**
- Check "Display over other apps" permission (Step 8)
- Samsung may require disabling "Secure lock" temporarily to test

**Calm not opening on tap?**
- Make sure Calm is installed on your phone
- The app falls back to the Play Store page for Calm if it can't find it

---

*Built with Android SDK 34 · Minimum Android 10 · Tested for Samsung One UI*
