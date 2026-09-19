# Demeter-reshader

Atemberaubende Grafik in deiner Handfläche. Verändere das Aussehen deines Android mit Demeter, einem direkt von ReShade inspirierten Tool.

[← Zurück zum Index](../README.md)

📦 **[Neueste Version herunterladen →](https://github.com/LexusYTG/Demeter-reshader/releases)**

📘 **[Demeter kompilieren →](BUILDINGDE.md)**

---

⚠️ **Getestete Mindesthardware**

Das ist das Minimum, auf dem Demeter getestet wurde und gut läuft:

| Komponente | Minimum | Warum |
|---|---|---|
| SoC | Unisoc T606 / MediaTek Helio G85 | Ultra-günstige Einstiegsprozessoren. |
| GPU | Mali-G57 MP1 / Mali-G52 MC2 | Bewältigen OpenGL ES 2.0 locker — es ist eine API von 2007, jeder moderne Chip schafft 60 FPS ohne ins Schwitzen zu kommen. |
| RAM | 4 GB LPDDR4X | Gerade genug, um Android 13 zusammen mit der App am Laufen zu halten. |
| Speicher | eMMC 5.1 (64 GB) | Der günstigste Einstiegsstandard. |
| Display | HD+ LCD (720p) | Geringere Auflösung = weniger Grafiklast beim Rendern. |
| System | Android 13 | Basisversion für partielle Bildschirmaufnahme (MediaProjection API). |

Reale Beispiele, die das erfüllen:

· Samsung Galaxy A05 (Helio G85, 4 GB RAM, 720p LCD, upgradebar auf Android 14)
· Moto G14 (Unisoc T600 series, 4 GB RAM, Android 13)
· Poco C65 / Redmi 13C (Helio G85, 4 GB RAM in der Basisversion, Android 13)

Wenn dein Gerät darunter liegt, startet es vielleicht trotzdem — aber erwarte keine Wunder bei schweren Effekten.

📌 **Zu Versionen vor Android 13:** Demeter läuft technisch auch auf Android 12 oder niedriger, aber die Überlagerung ist nicht ideal. Der System-Capturer fängt am Ende das Overlay selbst ein, was den Effekt zerstört (du siehst den Filter auf sich selbst angewendet, visuelles Feedback und so weiter). Deshalb setzen wir Android 13 als echte Basis.

**Tipp:** Wenn du zum ersten Mal mit Demeter arbeitest, teste es auf einem Video mit bereits angewendeten Filtern, z. B. CRT-Tube. So siehst du am schnellsten das echte Potenzial jedes Effekts, ohne von einem bestimmten Spiel abhängig zu sein.

---

🎮 **Was ist Demeter?**

Ein Arsenal an Echtzeit-Grafikeffekten für deine Mobile-Games.

Müde, immer dieselbe Grafik zu sehen? Suchst du einen Retro-, Glitch-, cineastischen oder direkt psychedelischen Touch? Mit Demeter kannst du die Ästhetik jedes Spiels unkompliziert tunen:

· Wendet Filter in Echtzeit auf das Spielbild an, mit nahezu keiner Latenz.
· Animiert die Effekte, damit das Bild atmet, pulsiert oder sich verzerrt, während du spielst.
· Regelt Intensität, Kontrast, Sättigung und mehr mit Live-Slidern und siehst die Änderung sofort.
· Wählt nur einen Bildschirmbereich (eine Ecke, das HUD, was du willst) oder nimmt alles.
· Platziert das Overlay, wo du willst — Ecke, Mitte oder adaptiver Modus, damit es 1:1 auf den aufgenommenen Bereich passt, ohne Verzerrung.
· Wechselt Effekte im laufenden Betrieb und kombiniert sie ohne Neustart.

---

⚡ **Was du ab der ersten Minute merkst**

| Funktion | Was sich in deiner Partie ändert |
|---|---|
| 🎯 Maßgeschneiderte Aufnahme | Definiere ein Rechteck auf dem Bildschirm und wende Effekte nur dort an. |
| 📐 Schwebendes Overlay | Verschiebe und skaliere das Effektfenster. Der adaptive Modus passt es an den Aufnahmebereich an. |
| 🎨 Eigene Effekte | Von sanftem Weichzeichnen bis zu totalem Glitch-Chaos. Sie sind enthalten, und du kannst eigene hinzufügen. |
| ⚙️ Live-Steuerung | Mehr Verzerrung, weniger, kühler, wärmer — alles live während des Spielens. |
| 🔄 Flüssige Animationen | Animierte Effekte aktualisieren sich selbst, ohne das Spiel zu belasten. |
| 👁️ Vorschau | Teste den Effekt auf einem Referenzbild, bevor du loslegst. |
| 🔒 Immersiver Modus | Die App versteckt sich selbst, um nicht zu stören. |
| 🧩 Effektmanager | Aktivieren, deaktivieren, auswählen oder löschen mit einem Tipp. |

---

📱 **Voraussetzungen**

· Android 13 oder höher (empfohlen; siehe Hinweis oben).
· Overlay-Berechtigung (um über das Spiel zu zeichnen).
· Bildschirmaufnahme-Berechtigung.
· (Optional) Speicherberechtigung, wenn du eigene Effekte importieren willst.

---

🏁 **In fünf Schritten**

1. Installiere und erteile die Berechtigungen, wenn sie angefragt werden.
2. Wähle einen Effekt mit **Auswählen** — einige sind standardmäßig dabei, oder füge deine eigenen aus dem Ordner hinzu.
3. (Optional) Definiere den Bereich mit **Aufnahmebereich**.
4. (Optional) Platziere das Overlay mit **Overlay**.
5. Drücke **Aufnahme starten** und fertig. Passe Parameter live über das Zahnrad (⚙) an und sieh alles in Echtzeit.

💡 **Empfohlen:** Bevor du dich in ein Spiel stürzt, mach einen schnellen Test auf einem Video mit CRT-Tube-Filtern. Das gibt dir eine klare Referenz, wie sich jeder Effekt verhält, und spart dir später Trial-and-Error.

---

🎬 **In der Store enthaltene Effekte**

Wir haben einen breiten Katalog an Shadern in der Store, bereit zum Herunterladen und Ausprobieren.

---

🎉 **Leg los**

Demeter entsteht aus der Leidenschaft für Spiele und visuelles Experimentieren. Probiere es mit deinen Lieblingstiteln, mische Effekte, dreh an den Parametern… und sieh, wie sich deine Runden wieder neu anfühlen.

🚀 Viel Spaß!

---

📘 **Für Creator:** Im Repository gibt es einen technischen Leitfaden mit allem, was du brauchst, um eigene Effekte zu erstellen, zu importieren und zu teilen. Beiträge sind willkommen.

---

Copyright © 2026 LexusYTG — leonpackpro@gmail.com
