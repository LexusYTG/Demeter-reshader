GUIDE

📘 Guía para crear efectos visuales en Demeter

¿Quieres dar rienda suelta a tu creatividad y diseñar tus propios efectos para Demeter? ¡Estás en el lugar adecuado! Esta guía te explica paso a paso cómo crear un módulo de efecto que podrás importar y usar en la app, sin necesidad de tocar el código fuente de Demeter.

---

🧩 ¿Qué es un módulo Demeter?

Un módulo es un archivo con extensión .demeter que contiene toda la información necesaria para aplicar un efecto visual: los shaders (programas que se ejecutan en la GPU), los parámetros ajustables y los metadatos del creador.

La app lee este archivo, compila los shaders y te permite jugar con los parámetros en tiempo real.

---

📁 Estructura del archivo .demeter

Es un archivo JSON con la siguiente estructura:

```json
{
  "name": "Nombre del efecto",
  "author": "Tu nombre o nick",
  "version": "1.0",
  "type": "MODIFIER",
  "vertexShader": "código GLSL del vertex shader",
  "fragmentShader": "código GLSL del fragment shader",
  "paramDefs": {
    "nombreUniform": {
      "label": "Etiqueta en la UI",
      "min": 0.0,
      "max": 1.0,
      "default": 0.5
    }
  },
  "params": {
    "nombreUniform": 0.5
  }
}
```

Campos obligatorios

· name: (string) Nombre del efecto que se mostrará en la lista.
· author: (string) Tu nombre o alias.
· version: (string) Versión del efecto (ej. "1.0").
· type: (string) Siempre "MODIFIER" para efectos visuales (por ahora).
· vertexShader: (string) Código fuente del vertex shader (GLSL).
· fragmentShader: (string) Código fuente del fragment shader (GLSL).

Campos opcionales (pero muy recomendables)

· paramDefs: (objeto) Define parámetros que el usuario puede ajustar con deslizadores. Cada clave es el nombre del uniform en el shader, y el valor es otro objeto con:
  · label: texto que se muestra en la interfaz.
  · min: valor mínimo del deslizador (float).
  · max: valor máximo (float).
  · default: valor por defecto.
· params: (objeto) Valores iniciales de los parámetros (sobrescriben el default). Si no se define, se usarán los defaults.

---

🎨 Los shaders: el corazón del efecto

Demeter usa OpenGL ES 2.0 y espera shaders escritos en GLSL (versión 100). Los shaders reciben la imagen capturada como una textura y la procesan píxel a píxel.

Vertex Shader

El vertex shader transforma las coordenadas de los vértices. Por lo general, no necesitas modificarlo; puedes usar el estándar que proporcionamos:

```glsl
attribute vec4 aPosition;
attribute vec2 aTexCoord;
varying vec2 vTexCoord;
void main() {
  gl_Position = aPosition;
  vTexCoord = aTexCoord;
}
```

Este shader pasa las coordenadas de textura al fragment shader sin modificarlas.

Fragment Shader

Aquí ocurre la magia. Recibe la textura de entrada y produce el color final. La firma básica es:

```glsl
precision highp float;
varying vec2 vTexCoord;
uniform sampler2D uTexture;
uniform float uTime;
// ... tus uniforms personalizados

void main() {
  vec4 color = texture2D(uTexture, vTexCoord);
  // Aplica tu transformación
  gl_FragColor = color;
}
```

Uniforms disponibles por defecto

· uTexture: (sampler2D) La imagen capturada.
· uTime: (float) Tiempo en segundos desde que se inició la captura. Útil para animaciones.

¡Importante! Si tu shader usa uTime, la app activará automáticamente el render loop para que la animación sea fluida. No necesitas hacer nada especial.

---

⚙️ Parámetros ajustables (sliders)

Para que el usuario pueda modificar valores en tiempo real, declara tus paramDefs. Por ejemplo, si quieres un control de intensidad:

```json
"paramDefs": {
  "uIntensidad": {
    "label": "Intensidad",
    "min": 0.0,
    "max": 2.0,
    "default": 1.0
  }
}
```

Luego, en el fragment shader, declaras el uniform:

```glsl
uniform float uIntensidad;
```

Y lo usas, por ejemplo, para mezclar la imagen original con un efecto:

```glsl
vec4 efecto = ...;
gl_FragColor = mix(color, efecto, uIntensidad);
```

El usuario verá un deslizador con la etiqueta "Intensidad" y podrá moverlo entre 0 y 2, con valor inicial 1.

---

📝 Ejemplo completo: Escala de grises con intensidad

Creamos un efecto que convierte la imagen a blanco y negro, con control de intensidad (0 = original, 1 = gris total).

Archivo grises.demeter:

```json
{
  "name": "Escala de grises",
  "author": "TuNick",
  "version": "1.0",
  "type": "MODIFIER",
  "vertexShader": "attribute vec4 aPosition; attribute vec2 aTexCoord; varying vec2 vTexCoord; void main() { gl_Position = aPosition; vTexCoord = aTexCoord; }",
  "fragmentShader": "precision highp float; varying vec2 vTexCoord; uniform sampler2D uTexture; uniform float uIntensidad; void main() { vec4 color = texture2D(uTexture, vTexCoord); float gris = dot(color.rgb, vec3(0.299, 0.587, 0.114)); vec4 grisColor = vec4(gris, gris, gris, color.a); gl_FragColor = mix(color, grisColor, uIntensidad); }",
  "paramDefs": {
    "uIntensidad": {
      "label": "Intensidad",
      "min": 0.0,
      "max": 1.0,
      "default": 0.8
    }
  },
  "params": {
    "uIntensidad": 0.8
  }
}
```

¡Listo! Solo guarda esto como grises.demeter e impórtalo desde la app.

---

💡 Consejos para crear efectos alucinantes

1. Experimenta con las coordenadas: vTexCoord va de (0,0) a (1,1). Puedes desplazarlas, escalarlas, distorsionarlas…
      Ejemplo: vec2 uv = vTexCoord * 2.0 - 1.0; para obtener coordenadas centradas en -1..1.
2. Aprovecha uTime: para animaciones, usa sin(uTime), cos(uTime), fract(uTime), etc.
3. Combina texturas: puedes generar patrones procedimentales (ruido, rayas, círculos) y mezclarlos con la imagen.
4. Optimiza: evita bucles largos o cálculos pesados por píxel, ya que se ejecutan muchas veces por frame.
5. Prueba en la vista previa de la app antes de lanzar la captura.
6. Comparte tus creaciones: la comunidad agradecerá tus efectos.

---

📦 ¿Cómo importar un módulo en Demeter?

1. Coloca el archivo .demeter en tu dispositivo.
2. Abre la app y pulsa el botón con el icono de carpeta (📁) para importar.
3. Navega hasta el archivo y selecciónalo.
4. El efecto aparecerá en la lista de módulos. ¡Ya puedes seleccionarlo y usarlo!

---

🔗 Recursos útiles

· Documentación de GLSL ES: https://www.khronos.org/opengles/sdk/docs/reference_cards/OpenGL-ES-2_0-Reference-card.pdf
· The Book of Shaders (inspiración y ejemplos): https://thebookofshaders.com/
· ShaderToy (para probar ideas): https://www.shadertoy.com/

---

🎉 ¡Manos a la obra!

Crear efectos para Demeter es divertido y accesible. No necesitas ser un experto en gráficos: con un poco de curiosidad y experimentación, lograrás resultados sorprendentes. ¡Anímate a compartir tus creaciones con la comunidad y a darle un toque único a tus juegos favoritos!

¡Que la fuerza (y los píxeles) te acompañen! 🚀


se aproxima un IDE para creacion de shaders ,paciencia ,solo e un hoby
