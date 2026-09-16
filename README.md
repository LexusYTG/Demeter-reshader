Demeter-reshader

Gráficos deslumbrantes en la palma de tu mano. Transforma la apariencia de tu Android con Demeter, una herramienta inspirada directamente en ReShade.

---

⚠️ Hardware mínimo probado

Esto es lo mínimo con lo que Demeter ha sido probado y funciona bien:

Componente Mínimo Por qué
SoC Unisoc T606 / MediaTek Helio G85 Procesadores de entrada ultra económicos.
GPU Mali-G57 MP1 / Mali-G52 MC2 Mueven OpenGL ES 2.0 sobradamente — es una API de 2007, cualquier chip moderno la corre a 60 FPS sin despeinarse.
RAM 4 GB LPDDR4X Lo justo para mantener Android 13 funcional junto a la app en ejecución.
Almacenamiento eMMC 5.1 (64 GB) El estándar más barato de la gama de entrada.
Pantalla LCD HD+ (720p) Menos resolución = menos carga gráfica al renderizar.
Sistema Android 13 Versión base para disponer de captura parcial de pantalla (MediaProjection API).

Ejemplos reales que cumplen esto:

· Samsung Galaxy A05 (Helio G85, 4 GB RAM, 720p LCD, actualizable a Android 14)
· Moto G14 (Unisoc T600 series, 4 GB RAM, Android 13)
· Poco C65 / Redmi 13C (Helio G85, 4 GB RAM en version básica, Android 13)

Si tu dispositivo está por debajo de esto, igual arranca — pero no esperes milagros con efectos pesados.

📌 Sobre versiones anteriores a Android 13: Demeter técnicamente corre en Android 12 o inferior, pero la superposición no es ideal. El capturador del sistema termina capturando el propio overlay, lo que rompe el efecto (ves el filtro aplicado sobre sí mismo, realimentación visual y demás). Por eso establecemos Android 13 como base real.

Tip: si es tu primera vez con Demeter, pruébalo sobre un video con filtros ya aplicados, tipo CRT tube. Es la forma más rápida de ver el potencial real de cada efecto sin depender de un juego en concreto.

---

🎮 ¿Qué es Demeter?

Un arsenal de efectos gráficos en tiempo real para tus juegos móviles.

¿Cansado de ver siempre los mismos gráficos? ¿Buscas un toque retro, glitch, cinematográfico o directamente psicodélico? Demeter te deja tunear la estética de cualquier juego sin complicaciones:

· Aplica filtros sobre la imagen del juego en tiempo real, con latencia casi nula.
· Anima los efectos para que la imagen respire, pulse o se distorsione mientras juegas.
· Ajusta intensidad, contraste, saturación y más con deslizadores en vivo, viendo el cambio al instante.
· Elige solo un área de la pantalla (una esquina, el HUD, lo que quieras) o tómala entera.
· Coloca el overlay donde te dé la gana — esquina, centro, o modo adaptativo para que encaje 1:1 con el área capturada, sin deformaciones.
· Cambia de efecto sobre la marcha y combina sin reiniciar nada.

---

⚡ Lo que notarás desde el primer minuto

Función Qué cambia en tu partida
🎯 Captura a medida Delimita un rectángulo de la pantalla y aplica efectos solo ahí.
📐 Overlay flotante Mueve y redimensiona la ventana de efectos. El modo adaptativo la ajusta al área capturada.
🎨 Efectos personalizados Desde un desenfoque suave hasta caos glitch total. Vienen incluidos, y puedes añadir los tuyos.
⚙️ Controles en vivo Más distorsión, menos, más frío, más cálido — todo en caliente mientras juegas.
🔄 Animaciones fluidas Los efectos animados se actualizan solos, sin cargar el juego.
👁️ Vista previa Prueba el efecto sobre una imagen de referencia antes de lanzarte.
🔒 Modo inmersivo La app se esconde sola para no molestar.
🧩 Gestor de efectos Activa, desactiva, selecciona o borra con un toque.

---

📱 Requisitos

· Android 13 o superior (recomendado; ver aclaración arriba).
· Permiso de superposición (para dibujar sobre el juego).
· Permiso de captura de pantalla.
· (Opcional) Permiso de almacenamiento, si quieres importar tus propios efectos.

---

🏁 En cinco pasos

1. Instala y concede los permisos cuando los pida.
2. Elige un efecto con Seleccionar — vienen varios por defecto, o añade los tuyos desde la carpeta.
3. (Opcional) Delimita el área con Área captura.
4. (Opcional) Coloca el overlay con Overlay.
5. Pulsa Iniciar captura y listo. Ajusta parámetros en caliente con el engranaje (⚙) y mira cómo cambia todo en directo.

💡 Recomendado: antes de meterte de lleno con un juego, haz una prueba rápida sobre un video con filtros tipo CRT tube. Te da una referencia clara de cómo se comporta cada efecto y te ahorra ensayo y error después.

---

🎬 Efectos incluidos en la store

· Retro / CRT — líneas de barrido, curvatura, parpadeo de tubo.
· Glitch / Cyberpunk — ruido RGB, desplazamiento de píxeles, interferencia.
· Mejora de imagen — nitidez, contraste, saturación, HDR simulado.
· Distorsiones — ondas, espirales, espejo, ojo de pez.
· Animaciones — colores en movimiento, pulsos, partículas.

Y los que vayan llegando.

---

🎉 Dale caña

Demeter nace de la pasión por los juegos y la experimentación visual. Pruébalo con tus títulos favoritos, mezcla efectos, toca parámetros… y mira cómo tus partidas se sienten nuevas otra vez.

🚀 ¡A jugar!

---

📘 Para creadores: en el repositorio hay una guía técnica con todo lo necesario para crear, importar y compartir tus propios efectos. Las contribuciones son bienvenidas.

---

Copyright © 2026 LexusYTG — leonpackpro@gmail.com

---

AnaCronix