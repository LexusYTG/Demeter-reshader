# Demeter-reshader

Des graphismes éblouissants dans le creux de votre main. Transformez l'apparence de votre Android avec Demeter, un outil directement inspiré de ReShade.

[← Retour à l'index](../README.md)

---

⚠️ **Matériel minimum testé**

Voici le minimum sur lequel Demeter a été testé et fonctionne bien :

| Composant | Minimum | Pourquoi |
|---|---|---|
| SoC | Unisoc T606 / MediaTek Helio G85 | Processeurs d'entrée de gamme ultra économiques. |
| GPU | Mali-G57 MP1 / Mali-G52 MC2 | Gèrent OpenGL ES 2.0 sans problème — c'est une API de 2007, n'importe quelle puce moderne la fait tourner à 60 FPS sans transpirer. |
| RAM | 4 GB LPDDR4X | Juste ce qu'il faut pour garder Android 13 fonctionnel avec l'app en cours d'exécution. |
| Stockage | eMMC 5.1 (64 GB) | Le standard le moins cher de l'entrée de gamme. |
| Écran | LCD HD+ (720p) | Moins de résolution = moins de charge graphique au rendu. |
| Système | Android 13 | Version de base pour disposer de la capture partielle d'écran (MediaProjection API). |

Exemples réels qui remplissent ces critères :

· Samsung Galaxy A05 (Helio G85, 4 GB RAM, LCD 720p, évolutif vers Android 14)
· Moto G14 (Unisoc T600 series, 4 GB RAM, Android 13)
· Poco C65 / Redmi 13C (Helio G85, 4 GB RAM en version de base, Android 13)

Si votre appareil est en dessous, ça peut quand même démarrer — mais n'espérez pas de miracles avec les effets lourds.

📌 **À propos des versions antérieures à Android 13 :** Demeter tourne techniquement sous Android 12 ou inférieur, mais la superposition n'est pas idéale. Le captureur du système finit par capturer l'overlay lui-même, ce qui casse l'effet (on voit le filtre appliqué sur lui-même, feedback visuel, etc.). C'est pourquoi nous fixons Android 13 comme base réelle.

**Astuce :** si c'est votre première fois avec Demeter, testez-le sur une vidéo avec filtres déjà appliqués, type CRT tube. C'est le moyen le plus rapide de voir le potentiel réel de chaque effet sans dépendre d'un jeu précis.

---

🎮 **Qu'est-ce que Demeter ?**

Un arsenal d'effets graphiques en temps réel pour vos jeux mobiles.

Marre de voir toujours les mêmes graphismes ? Vous cherchez une touche rétro, glitch, cinématographique ou carrément psychédélique ? Demeter vous laisse régler l'esthétique de n'importe quel jeu sans complications :

· Applique des filtres sur l'image du jeu en temps réel, avec une latence quasi nulle.
· Anime les effets pour que l'image respire, pulse ou se déforme pendant que vous jouez.
· Ajuste l'intensité, le contraste, la saturation et plus avec des curseurs en direct, en voyant le changement à l'instant.
· Choisit juste une zone de l'écran (un coin, le HUD, ce que vous voulez) ou prend tout.
· Place l'overlay où vous voulez — coin, centre, ou mode adaptatif pour qu'il colle 1:1 à la zone capturée, sans déformation.
· Change d'effet à la volée et les combine sans rien redémarrer.

---

⚡ **Ce que vous remarquerez dès la première minute**

| Fonction | Ce qui change dans votre partie |
|---|---|
| 🎯 Capture sur mesure | Délimitez un rectangle de l'écran et appliquez les effets uniquement là. |
| 📐 Overlay flottant | Déplacez et redimensionnez la fenêtre d'effets. Le mode adaptatif l'ajuste à la zone capturée. |
| 🎨 Effets personnalisés | D'un flou doux au chaos glitch total. Ils sont inclus, et vous pouvez ajouter les vôtres. |
| ⚙️ Contrôles en direct | Plus de distorsion, moins, plus froid, plus chaud — tout à chaud pendant que vous jouez. |
| 🔄 Animations fluides | Les effets animés se mettent à jour tout seuls, sans charger le jeu. |
| 👁️ Aperçu | Testez l'effet sur une image de référence avant de vous lancer. |
| 🔒 Mode immersif | L'app se cache toute seule pour ne pas déranger. |
| 🧩 Gestionnaire d'effets | Activez, désactivez, sélectionnez ou supprimez d'un tap. |

---

📱 **Prérequis**

· Android 13 ou supérieur (recommandé ; voir la note plus haut).
· Permission de superposition (pour dessiner par-dessus le jeu).
· Permission de capture d'écran.
· (Optionnel) Permission de stockage, si vous voulez importer vos propres effets.

---

🏁 **En cinq étapes**

1. Installez et accordez les permissions quand elles sont demandées.
2. Choisissez un effet avec **Sélectionner** — plusieurs sont fournis par défaut, ou ajoutez les vôtres depuis le dossier.
3. (Optionnel) Délimitez la zone avec **Zone de capture**.
4. (Optionnel) Placez l'overlay avec **Overlay**.
5. Appuyez sur **Démarrer la capture** et c'est parti. Ajustez les paramètres à chaud avec l'engrenage (⚙) et regardez tout changer en direct.

💡 **Recommandé :** avant de vous lancer à fond dans un jeu, faites un test rapide sur une vidéo avec filtres type CRT tube. Ça vous donne une référence claire du comportement de chaque effet et vous évite les essais-erreurs ensuite.

---

🎬 **Effets inclus dans la store**

Nous avons un large catalogue de shaders dans la store, prêts à télécharger et à tester.

---

🎉 **Lâchez-vous**

Demeter naît de la passion pour les jeux et l'expérimentation visuelle. Testez-le avec vos titres préférés, mélangez les effets, touchez aux paramètres… et voyez vos parties se sentir nouvelles à nouveau.

🚀 Bon jeu !

---

📘 **Pour les créateurs :** le dépôt contient un guide technique avec tout le nécessaire pour créer, importer et partager vos propres effets. Les contributions sont bienvenues.

---

Copyright © 2026 LexusYTG — leonpackpro@gmail.com
