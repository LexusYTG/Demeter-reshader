# Demeter-reshader

Dazzling graphics in the palm of your hand. Transform the look of your Android with Demeter, a tool directly inspired by ReShade.

[← Back to index](../README.md)

📦 **[Download the latest release →](https://github.com/LexusYTG/Demeter-reshader/releases)**

---

⚠️ **Minimum tested hardware**

This is the minimum hardware Demeter has been tested on and runs well:

| Component | Minimum | Why |
|---|---|---|
| SoC | Unisoc T606 / MediaTek Helio G85 | Ultra-budget entry-level processors. |
| GPU | Mali-G57 MP1 / Mali-G52 MC2 | They handle OpenGL ES 2.0 easily — it's a 2007 API, any modern chip runs it at 60 FPS without breaking a sweat. |
| RAM | 4 GB LPDDR4X | Just enough to keep Android 13 running alongside the app. |
| Storage | eMMC 5.1 (64 GB) | The cheapest entry-level standard. |
| Display | HD+ LCD (720p) | Lower resolution = less GPU load when rendering. |
| System | Android 13 | Base version required for partial screen capture (MediaProjection API). |

Real devices that meet this:

· Samsung Galaxy A05 (Helio G85, 4 GB RAM, 720p LCD, upgradable to Android 14)
· Moto G14 (Unisoc T600 series, 4 GB RAM, Android 13)
· Poco C65 / Redmi 13C (Helio G85, 4 GB RAM on base model, Android 13)

If your device is below this, it may still boot — but don't expect miracles with heavy effects.

📌 **About versions earlier than Android 13:** Demeter technically runs on Android 12 or lower, but the overlay isn't ideal. The system capturer ends up capturing the overlay itself, which breaks the effect (you see the filter applied on top of itself, visual feedback loops and so on). That's why we set Android 13 as the real baseline.

**Tip:** if this is your first time with Demeter, try it on a video that already has filters applied, like a CRT tube effect. It's the fastest way to see the real potential of each effect without relying on a specific game.

---

🎮 **What is Demeter?**

An arsenal of real-time graphics effects for your mobile games.

Tired of always seeing the same graphics? Looking for a retro, glitch, cinematic or straight-up psychedelic touch? Demeter lets you fine-tune the aesthetics of any game without complications:

· Apply filters on top of the game image in real time, with near-zero latency.
· Animate the effects so the image breathes, pulses or distorts while you play.
· Adjust intensity, contrast, saturation and more with live sliders, seeing changes instantly.
· Pick just an area of the screen (a corner, the HUD, whatever you want) or take the whole thing.
· Place the overlay wherever you want — corner, center, or adaptive mode so it fits the captured area 1:1, without distortion.
· Switch effects on the fly and combine them without restarting anything.

---

⚡ **What you'll notice from minute one**

| Feature | What changes in your game |
|---|---|
| 🎯 Custom capture | Define a rectangle on screen and apply effects only there. |
| 📐 Floating overlay | Move and resize the effects window. Adaptive mode snaps it to the captured area. |
| 🎨 Custom effects | From a soft blur to total glitch chaos. They're included, and you can add your own. |
| ⚙️ Live controls | More distortion, less, cooler, warmer — all on the fly while you play. |
| 🔄 Smooth animations | Animated effects update by themselves, without loading the game. |
| 👁️ Preview | Try the effect on a reference image before you commit. |
| 🔒 Immersive mode | The app hides itself so it doesn't get in the way. |
| 🧩 Effect manager | Enable, disable, select or delete with a tap. |

---

📱 **Requirements**

· Android 13 or higher (recommended; see note above).
· Overlay permission (to draw over the game).
· Screen capture permission.
· (Optional) Storage permission, if you want to import your own effects.

---

🏁 **In five steps**

1. Install and grant permissions when asked.
2. Pick an effect with **Select** — several come by default, or add your own from the folder.
3. (Optional) Define the area with **Capture area**.
4. (Optional) Place the overlay with **Overlay**.
5. Hit **Start capture** and you're done. Tweak parameters live with the gear icon (⚙) and watch everything change in real time.

💡 **Recommended:** before diving into a game, do a quick test on a video with CRT-tube-style filters. It gives you a clear reference of how each effect behaves and saves you trial and error later.

---

🎬 **Effects included in the store**

We have a broad catalog of shaders in the store, ready to download and try.

---

🎉 **Go wild**

Demeter is born from a passion for games and visual experimentation. Try it with your favorite titles, mix effects, tweak parameters… and watch your sessions feel new again.

🚀 Game on!

---

📘 **For creators:** the repository includes a technical guide with everything you need to create, import and share your own effects. Contributions are welcome.

---

Copyright © 2026 LexusYTG — leonpackpro@gmail.com
