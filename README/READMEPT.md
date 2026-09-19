# Demeter-reshader

Gráficos deslumbrantes na palma da sua mão. Transforme a aparência do seu Android com o Demeter, uma ferramenta inspirada diretamente no ReShade.

[← Voltar ao índice](../README.md)

📦 **[Baixar a última versão →](https://github.com/LexusYTG/Demeter-reshader/releases)**

📘 **[Compilar o Demeter →](BUILDINGPT.md)**

---

⚠️ **Hardware mínimo testado**

Este é o mínimo em que o Demeter foi testado e roda bem:

| Componente | Mínimo | Por quê |
|---|---|---|
| SoC | Unisoc T606 / MediaTek Helio G85 | Processadores de entrada ultra baratos. |
| GPU | Mali-G57 MP1 / Mali-G52 MC2 | Rodam OpenGL ES 2.0 com folga — é uma API de 2007, qualquer chip moderno a executa a 60 FPS sem esforço. |
| RAM | 4 GB LPDDR4X | O suficiente para manter o Android 13 funcional junto com o app em execução. |
| Armazenamento | eMMC 5.1 (64 GB) | O padrão mais barato da linha de entrada. |
| Tela | LCD HD+ (720p) | Menos resolução = menos carga gráfica ao renderizar. |
| Sistema | Android 13 | Versão base para ter captura parcial de tela (MediaProjection API). |

Exemplos reais que atendem a isso:

· Samsung Galaxy A05 (Helio G85, 4 GB RAM, LCD 720p, atualizável para Android 14)
· Moto G14 (Unisoc T600 series, 4 GB RAM, Android 13)
· Poco C65 / Redmi 13C (Helio G85, 4 GB RAM na versão básica, Android 13)

Se o seu dispositivo estiver abaixo disso, até pode iniciar — mas não espere milagres com efeitos pesados.

📌 **Sobre versões anteriores ao Android 13:** o Demeter tecnicamente roda no Android 12 ou inferior, mas a sobreposição não é ideal. O capturador do sistema acaba capturando o próprio overlay, o que quebra o efeito (você vê o filtro aplicado sobre si mesmo, feedback visual e tal). Por isso definimos o Android 13 como base real.

**Dica:** se for sua primeira vez com o Demeter, teste em um vídeo com filtros já aplicados, tipo CRT tube. É a forma mais rápida de ver o potencial real de cada efeito sem depender de um jogo específico.

---

🎮 **O que é o Demeter?**

Um arsenal de efeitos gráficos em tempo real para os seus jogos mobile.

Cansado de ver sempre os mesmos gráficos? Procurando um toque retrô, glitch, cinematográfico ou direto psicodélico? O Demeter deixa você ajustar a estética de qualquer jogo sem complicação:

· Aplica filtros sobre a imagem do jogo em tempo real, com latência quase nula.
· Anima os efeitos para a imagem respirar, pulsar ou distorcer enquanto você joga.
· Ajusta intensidade, contraste, saturação e mais com sliders ao vivo, vendo a mudança na hora.
· Escolhe só uma área da tela (um canto, o HUD, o que quiser) ou pega ela inteira.
· Coloca o overlay onde quiser — canto, centro, ou modo adaptativo para encaixar 1:1 na área capturada, sem deformação.
· Troca de efeito na hora e combina sem reiniciar nada.

---

⚡ **O que você vai notar do primeiro minuto**

| Recurso | O que muda na sua partida |
|---|---|
| 🎯 Captura sob medida | Delimite um retângulo da tela e aplique efeitos só ali. |
| 📐 Overlay flutuante | Mova e redimensione a janela de efeitos. O modo adaptativo ajusta à área capturada. |
| 🎨 Efeitos personalizados | De um desfoque suave até caos glitch total. Vêm inclusos, e você pode adicionar os seus. |
| ⚙️ Controles ao vivo | Mais distorção, menos, mais frio, mais quente — tudo em tempo real enquanto joga. |
| 🔄 Animações fluidas | Os efeitos animados se atualizam sozinhos, sem carregar o jogo. |
| 👁️ Pré-visualização | Teste o efeito sobre uma imagem de referência antes de se lançar. |
| 🔒 Modo imersivo | O app se esconde sozinho para não atrapalhar. |
| 🧩 Gerenciador de efeitos | Ativa, desativa, seleciona ou apaga com um toque. |

---

📱 **Requisitos**

· Android 13 ou superior (recomendado; ver observação acima).
· Permissão de sobreposição (para desenhar sobre o jogo).
· Permissão de captura de tela.
· (Opcional) Permissão de armazenamento, se quiser importar seus próprios efeitos.

---

🏁 **Em cinco passos**

1. Instale e conceda as permissões quando pedir.
2. Escolha um efeito com **Selecionar** — vêm vários por padrão, ou adicione os seus da pasta.
3. (Opcional) Delimite a área com **Área de captura**.
4. (Opcional) Posicione o overlay com **Overlay**.
5. Aperte **Iniciar captura** e pronto. Ajuste parâmetros ao vivo na engrenagem (⚙) e veja tudo mudar em tempo real.

💡 **Recomendado:** antes de mergulhar num jogo, faça um teste rápido em um vídeo com filtros tipo CRT tube. Dá uma referência clara de como cada efeito se comporta e economiza tentativa e erro depois.

---

🎬 **Efeitos inclusos na store**

Temos um amplo catálogo de shaders na store, prontos para baixar e testar.

---

🎉 **Manda ver**

O Demeter nasce da paixão por jogos e experimentação visual. Teste com seus títulos favoritos, misture efeitos, mexa nos parâmetros… e veja suas partidas parecerem novas de novo.

🚀 Bora jogar!

---

📘 **Para criadores:** no repositório há um guia técnico com tudo o que você precisa para criar, importar e compartilhar seus próprios efeitos. Contribuições são bem-vindas.

---

Copyright © 2026 LexusYTG — leonpackpro@gmail.com
