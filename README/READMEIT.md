# Demeter-reshader

Grafica sbalorditiva nel palmo della tua mano. Trasforma l'aspetto del tuo Android con Demeter, uno strumento ispirato direttamente a ReShade.

[← Torna all'indice](../README.md)

---

⚠️ **Hardware minimo testato**

Questo è il minimo su cui Demeter è stato testato e funziona bene:

| Componente | Minimo | Perché |
|---|---|---|
| SoC | Unisoc T606 / MediaTek Helio G85 | Processori entry-level ultra economici. |
| GPU | Mali-G57 MP1 / Mali-G52 MC2 | Gestiscono OpenGL ES 2.0 senza problemi — è una API del 2007, qualsiasi chip moderno la fa girare a 60 FPS senza sudare. |
| RAM | 4 GB LPDDR4X | Il giusto per tenere Android 13 funzionante insieme all'app in esecuzione. |
| Memoria | eMMC 5.1 (64 GB) | Lo standard più economico della fascia d'ingresso. |
| Schermo | LCD HD+ (720p) | Meno risoluzione = meno carico grafico in render. |
| Sistema | Android 13 | Versione base per avere la cattura parziale dello schermo (MediaProjection API). |

Esempi reali che rispettano questi requisiti:

· Samsung Galaxy A05 (Helio G85, 4 GB RAM, LCD 720p, aggiornabile ad Android 14)
· Moto G14 (Unisoc T600 series, 4 GB RAM, Android 13)
· Poco C65 / Redmi 13C (Helio G85, 4 GB RAM nella versione base, Android 13)

Se il tuo dispositivo è sotto questo livello, potrebbe comunque avviarsi — ma non aspettarti miracoli con effetti pesanti.

📌 **Versioni precedenti ad Android 13:** Demeter gira tecnicamente su Android 12 o inferiore, ma la sovrapposizione non è ideale. Il capturer di sistema finisce per catturare l'overlay stesso, rompendo l'effetto (vedi il filtro applicato su sé stesso, feedback visivo e così via). Per questo fissiamo Android 13 come base reale.

**Suggerimento:** se è la tua prima volta con Demeter, provalo su un video con filtri già applicati, tipo CRT tube. È il modo più rapido per vedere il potenziale reale di ogni effetto senza dipendere da un gioco specifico.

---

🎮 **Cos'è Demeter?**

Un arsenale di effetti grafici in tempo reale per i tuoi giochi mobile.

Stanco di vedere sempre la stessa grafica? Cerchi un tocco retrò, glitch, cinematografico o direttamente psichedelico? Demeter ti lascia regolare l'estetica di qualsiasi gioco senza complicazioni:

· Applica filtri sull'immagine del gioco in tempo reale, con latenza quasi nulla.
· Anima gli effetti così l'immagine respira, pulsa o si distorce mentre giochi.
· Regola intensità, contrasto, saturazione e altro con slider dal vivo, vedendo il cambiamento all'istante.
· Sceglie solo un'area dello schermo (un angolo, l'HUD, quello che vuoi) o prende tutto.
· Posiziona l'overlay dove vuoi — angolo, centro, o modalità adattiva per farlo combaciare 1:1 con l'area catturata, senza deformazioni.
· Cambia effetto al volo e li combina senza riavviare nulla.

---

⚡ **Cosa noterai dal primo minuto**

| Funzione | Cosa cambia nella tua partita |
|---|---|
| 🎯 Cattura su misura | Delimita un rettangolo dello schermo e applica effetti solo lì. |
| 📐 Overlay flottante | Sposta e ridimensiona la finestra degli effetti. La modalità adattiva la adatta all'area catturata. |
| 🎨 Effetti personalizzati | Da una sfocatura morbida al caos glitch totale. Sono inclusi, e puoi aggiungere i tuoi. |
| ⚙️ Controlli dal vivo | Più distorsione, meno, più freddo, più caldo — tutto a caldo mentre giochi. |
| 🔄 Animazioni fluide | Gli effetti animati si aggiornano da soli, senza caricare il gioco. |
| 👁️ Anteprima | Prova l'effetto su un'immagine di riferimento prima di lanciarti. |
| 🔒 Modalità immersiva | L'app si nasconde da sola per non disturbare. |
| 🧩 Gestore effetti | Attiva, disattiva, seleziona o elimina con un tocco. |

---

📱 **Requisiti**

· Android 13 o superiore (raccomandato; vedi nota sopra).
· Permesso di sovrapposizione (per disegnare sul gioco).
· Permesso di cattura schermo.
· (Opzionale) Permesso di archiviazione, se vuoi importare i tuoi effetti.

---

🏁 **In cinque passi**

1. Installa e concedi i permessi quando richiesti.
2. Scegli un effetto con **Seleziona** — ne vengono diversi di default, oppure aggiungi i tuoi dalla cartella.
3. (Opzionale) Delimita l'area con **Area cattura**.
4. (Opzionale) Posiziona l'overlay con **Overlay**.
5. Premi **Avvia cattura** e fatto. Regola i parametri a caldo con l'ingranaggio (⚙) e guarda tutto cambiare in diretta.

💡 **Consigliato:** prima di buttarti su un gioco, fai una prova veloce su un video con filtri tipo CRT tube. Ti dà un riferimento chiaro di come si comporta ogni effetto e ti risparmia tentativi ed errori dopo.

---

🎬 **Effetti inclusi nello store**

Abbiamo un ampio catalogo di shader nello store, pronti da scaricare e provare.

---

🎉 **Dacci dentro**

Demeter nasce dalla passione per i giochi e la sperimentazione visiva. Provalo con i tuoi titoli preferiti, mescola effetti, tocca i parametri… e guarda le tue partite sembrare nuove di nuovo.

🚀 Buon gioco!

---

📘 **Per i creatori:** nel repository c'è una guida tecnica con tutto il necessario per creare, importare e condividere i tuoi effetti. I contributi sono benvenuti.

---

Copyright © 2026 LexusYTG — leonpackpro@gmail.com
