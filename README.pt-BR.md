# Time Machine Keyboard

[English](README.md) · **Português**

Teclado Android que guarda versões do texto enquanto você digita e deixa voltar a qualquer uma delas, como um controle de versões embutido no teclado. Funciona offline, sem backend, sem IA, e o histórico fica só na memória do aparelho.

> Princípio central: o teclado nunca promete recuperar o que não conseguiu ler. Versões em que o app não deixou ler o campo inteiro ficam marcadas como **parciais** e não substituem o campo.

## Estado

| Fase | Situação |
| --- | --- |
| 1. Fundação: IME, QWERTY pt-BR, ativação | Pronta |
| 2. Histórico: sessões, captura, deduplicação, memória | Pronta |
| 3. Time Machine: linha do tempo, prévia, restauração segura | Pronta (adiantada, porque o MVP depende dela) |
| 4. Evolução: comparação e ramos | Comparação por região alterada e ramos visuais prontos; falta diff de várias regiões |
| 5. Persistência criptografada (Room + Keystore) | **Não iniciada**: o histórico é só em memória |

Validado no emulador Pixel 6 (Android 12, API 31) e com testes automatizados (veja [Testes](#testes)). Num aparelho físico (Redmi Note 14, Android 16), o app instalou, o teclado foi ativado e os 10 testes instrumentados passaram; o roteiro visual completo ainda não foi repetido lá.

## Requisitos

- JDK 21, Android SDK com `platforms;android-37.0` e `build-tools;36.0.0`
- Android 9 (API 28) ou superior no aparelho

## Compilar e instalar

```bash
./gradlew :app:assembleDebug
```

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Ativar o teclado

O Android exige que o usuário faça isso; o app não tenta ativar sozinho.

1. Abra o app **Time Machine**. A tela inicial mostra o que falta.
2. **Ativar o teclado** → abre Configurações › Teclados. Ligue "Time Machine Keyboard". O Android mostra um aviso padrão sobre teclados poderem ler o que é digitado; o app não tem permissão de Internet.
3. **Escolher como teclado atual** → escolha Time Machine no seletor do sistema.
4. Use a **Área de teste** do próprio app para experimentar.

Para trocar de teclado, use o ícone de teclado que o Android mostra na barra de navegação. O Time Machine não tem tecla de globo: ela trocava de teclado sem querer.

## Usar

- **Digitar**: QWERTY pt-BR com `ç`. Segure uma vogal para acentos (`á ã â à`…), o ponto para pontuação e, em campos de e-mail/URL, para `.com.br`. `?123` e `=\<` trazem números e símbolos; campos numéricos e de telefone abrem um teclado numérico. Um toque no shift ativa maiúscula, dois toques travam (Caps Lock). A tecla Enter segue o app: Enviar, Buscar, Ir, Próximo, OK ou nova linha.
- **Sugestões de palavras**: três sugestões na faixa enquanto você digita, a melhor no meio. Ignoram acento ("nao" → "não") e seguem as maiúsculas que você digitou. Tocar troca a palavra e põe espaço. O teclado aprende as palavras e as sequências que você usa ("bom" → "dia") **só na memória**, nunca em senha, modo privado ou campos que pedem privacidade. Dá para desligar em Aparência › Previsão.
- **Emojis**: a tecla ☺ abre todos os emojis do Unicode que a fonte do aparelho consegue desenhar, em abas (recentes, carinhas, pessoas, animais, comida, viagem, atividades, objetos, símbolos, bandeiras). Segure um emoji com ponto para escolher o tom de pele. O apagar remove um emoji composto (como 👋🏽 ou 👨‍👩‍👧) de uma vez.
- **Mover o cursor**: arraste a barra de espaço para os lados.
- **Ajustes rápidos** (engrenagem na faixa): Vibração, Som, Privado, Tema, **Modos**, **Tamanho**, **Números** (fileira de 1 a 0) e Mais (abre o app).
- **Modos**: *Completo*, *Uma mão* (teclado encostado num lado; a seta troca de lado e ↔ volta ao completo) e *Polegar* (letras divididas em duas metades, com a barra de espaço atravessando o vão).
- **Tamanho**: uma moldura com alça em cada borda. A de cima muda a altura (80–130%), as laterais estreitam o teclado e a de baixo o levanta. O teclado muda ao vivo, só fica salvo no **OK**, e **Redefinir** volta ao padrão. Os mesmos ajustes existem em controles deslizantes no app (Aparência), como alternativa ao arrasto.
- **Layout e teclas** (no app): fileira de números, teclas de seta (← ↓ ↑ →, repetem ao segurar) e tempo para manter pressionado.
- **Ver o histórico**: a faixa acima das teclas mostra o trilho das últimas versões (a atual em âmbar). Toque no relógio, no trilho, ou **segure a barra de espaço**.
- **Linha do tempo**: o trilho mostra as versões em ordem, com ramos em faixas separadas. Toque em uma versão ou use ‹ ›. "Mudanças" mostra o que sairia (riscado) e o que voltaria (sublinhado) em relação ao texto atual. Nada muda no app até você tocar em **Restaurar**.
- **Desfazer uma restauração**: logo depois de restaurar, a faixa mostra "Restaurado · Desfazer". O texto substituído também fica na linha do tempo como "Antes de restaurar".
- **Ramos**: restaurar uma versão antiga e continuar escrevendo cria um ramo. As versões descartadas não são apagadas.
- **Excluir / Limpar**: excluem uma versão ou o histórico do campo (pedem um segundo toque para confirmar). Nas configurações, "Apagar todo o histórico" limpa tudo.

## Privacidade

- Sem `android.permission.INTERNET` (conferido no APK com `aapt2 dump permissions`), sem telemetria, sem AccessibilityService, sem backup.
- Palavras aprendidas para as sugestões ficam **só na memória**, com limite de tamanho, e são apagadas junto com o histórico. Emojis recentes (só os emojis) ficam salvos no aparelho.
- Histórico **só em memória**. Reiniciar o aparelho ou o processo apaga tudo. Versões expiram pela retenção configurada (padrão 1 h) e são limitadas por campo (padrão 100).
- Nunca gravados: campos de senha (todas as variações), campos com `IME_FLAG_NO_PERSONALIZED_LEARNING` (ex.: aba anônima), campos numéricos/telefone/data (costumam ser códigos e cartões), campos sem texto (`TYPE_NULL`), apps excluídos e tudo em modo privado. A faixa sempre diz quando não está gravando e por quê.
- Captura só com o teclado aberto (entre `onStartInputView` e `onFinishInputView`).
- Nenhum texto digitado vai para o logcat.
- **Limitação**: a detecção depende de como o app declara seus campos. Um campo de senha não marcado como senha não é detectado. Para isso existem o modo privado e a lista de apps excluídos.

## Dados embutidos e licenças

Gerados por [tools/build_assets.py](tools/build_assets.py) em `app/src/main/assets/`:

- `emoji.txt`: emojis e tons de pele do [emoji-test.txt da Unicode](https://unicode.org/Public/emoji/latest/emoji-test.txt) (versão 18.0), sob os [termos de uso da Unicode](https://www.unicode.org/terms_of_use.html).
- `words_pt_br.txt`: 40 mil palavras com frequência, derivadas de [FrequencyWords](https://github.com/hermitdave/FrequencyWords) (Hermit Dave, a partir do OpenSubtitles), sob [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). O arquivo derivado segue a mesma licença.

## Arquitetura

```
com.timemachine.keyboard
├── TimeMachineApp.kt          instâncias compartilhadas (teclado e configurações no mesmo processo)
├── ime/
│   ├── TimeMachineIME.kt       InputMethodService: liga teclas, histórico, privacidade e restauração
│   ├── FieldReader.kt          lê o campo e decide se a leitura é completa ou parcial
│   ├── KeyboardFrame.kt        raiz da visão: área do teclado, recuos, elevação e camadas
│   └── ImeViewTreeOwner.kt     Lifecycle/SavedState para usar Compose dentro do IME
├── toolbar/Toolbar.kt          ajustes rápidos, modos, painel de uma mão e moldura de redimensionar
├── keyboard/
│   ├── KeyboardLayout.kt       modelo de teclas, layouts pt-BR/símbolos/numérico, Enter contextual
│   ├── KeyboardView.kt         teclas em Canvas, multitoque, repetição, acentos, gestos, TalkBack
│   ├── KeyPreviewOverlay.kt    prévia da tecla e seletor de acentos
│   ├── ShiftController.kt      shift, Caps Lock e capitalização automática
│   ├── KeyboardPalette.kt      cores (com contraste medido)
│   └── Glyphs.kt               ícones de traço compartilhados por View e Compose
├── history/
│   ├── Snapshot.kt             Snapshot, FieldText, FieldKey, Completeness, Operation
│   ├── VersionGraph.kt         grafo de versões com head e ramos
│   ├── HistorySession.kt       histórico de um campo: deduplicação, limite, expiração
│   ├── SnapshotPolicy.kt       debounce, limites, classificação da operação
│   ├── HistoryEngine.kt        sessões, debounce com corrotinas, StateFlow para a interface
│   └── TextDiff.kt             diferença por região alterada
├── restoration/RestorationEngine.kt
├── timeline/                   StripBar, TimelinePanel, RailLayout (faixas dos ramos), Wording
├── predict/WordPredictor.kt    dicionário por prefixo sem acento, aprendizado em memória, próxima palavra
├── emoji/                      catálogo (filtrado pela fonte do aparelho), recentes e painel
├── privacy/PrivacyPolicy.kt
└── settings/                   MainActivity, SettingsScreen, SettingsRepository, AppTheme
```

### Decisões

1. **Histórico como grafo, não pilha.** Cada `Snapshot` aponta para o pai; `head` é a versão atual. Restaurar move o head; novas edições descendem dele e formam um ramo. Remover uma versão religa os filhos ao pai dela. Texto idêntico a uma versão existente move o head para ela em vez de duplicar.
2. **Completo só com duas fontes concordando.** `getExtractedText` precisa começar no offset 0 e bater exatamente com `getTextBeforeCursor` + seleção + `getTextAfterCursor`. Qualquer dúvida vira `PARTIAL`. Textos acima de 10.000 caracteres são sempre parciais.
3. **Restauração nunca apaga o que não leu.** O `RestorationEngine` exige versão alvo completa, mesma sessão e mesmo campo, e uma leitura completa do texto atual. Esse texto vira a versão "Antes de restaurar"; depois, em lote, `deleteSurroundingText` remove exatamente os trechos lidos antes e depois da seleção e `commitText` troca a seleção. *Por que não `setSelection(0, n)` + conferir a seleção:* no emulador, o `TextField` do Compose só aplica edições no `endBatchEdit` e atualiza o estado de forma assíncrona; a conferência lia um valor antigo e abortava. O resultado é conferido na hora e de novo 300 ms depois; se o app não aplicar, a faixa avisa e o texto anterior continua salvo.
4. **Agrupamento.** Debounce (padrão 500 ms) após pausas, mais captura imediata antes do primeiro apagar de uma sequência, antes de digitar sobre uma seleção, antes do Enter/Enviar (que costuma limpar o campo) e antes de restaurar. Colagens e mudanças feitas pelo app são classificadas comparando o que mudou com quantos caracteres o teclado digitou.
5. **Sessões por campo** (`pacote + fieldId + classe/variação do inputType`), retomadas se o mesmo campo voltar dentro da retenção. No máximo 8 sessões em memória.
6. **Teclas em Canvas, painéis em Compose.** O teclado é uma View própria (latência, multitoque, `ExploreByTouchHelper` para TalkBack). A faixa e a linha do tempo usam Compose dentro do IME com um `LifecycleOwner` próprio. Um `ViewGroup` raiz (`KeyboardFrame`) dá à linha do tempo exatamente a altura do teclado, para nada pular na tela do app.
7. **Sem Room por enquanto.** A persistência é a Fase 5 e precisa ser opcional, criptografada (AES-GCM com chave no Android Keystore) e explicitamente ligada. Adicionar Room agora seria dependência sem uso. O ponto de entrada é o `HistoryEngine`, onde ficam todas as gravações.
8. **Configurações em SharedPreferences.** Leitura síncrona, adequada ao IME. Não guarda nenhum texto digitado.

### Design

Guiado pelos princípios da Human Interface Guidelines da Apple, traduzidos para Android: componentes e navegação seguem o Material 3, e a identidade fica na cor, nos ícones e na linha do tempo.

- **Uma cor, um significado.** O âmbar marca só o tempo: a versão atual, "Restaurar" e "Desfazer". A tecla de ação (Enviar, Buscar) usa contraste invertido, não âmbar (`color.md`, "Avoid using the same color to mean different things").
- **Contraste medido.** Todo texto fica em pelo menos 4,5:1; no escuro, ≥ 6,9:1 (valores em `KeyboardPalette.kt`).
- **O elemento marcante é o trilho de versões.** Pontos numa linha, em forma de grafo de git deitado, que crescem na faixa enquanto você digita e viram a linha do tempo. Versões parciais são pontos vazados: a forma comunica, não só a cor.
- **Movimento contido.** Toque de tecla instantâneo; ao soltar, o destaque some em ~90 ms. Painéis entram com fade e um leve deslize (200 ms) e saem mais rápido (160 ms); a troca entre painéis, de modo, de lado e de tamanho desliza em 260 ms; o trilho avança um passo quando entra uma versão. Tudo segue a "Escala de duração da animação" do Android e some com animações desligadas (`motion.md`, evitar movimento em interações frequentes).
- **Haptics com significado consistente.** Toque de tecla, um tique ao percorrer versões, confirmação/rejeição na restauração. Tudo pode ser desligado (`playing-haptics.md`).
- **Alternativa a todo gesto.** Segurar espaço tem o botão do relógio; percorrer o trilho tem ‹ ›. Texto explica estados (`accessibility.md`).
- **Ajuda fora do teclado.** As instruções ficam no app, não dentro do teclado (`virtual-keyboards.md`, "Avoid displaying help content within the keyboard itself"). Há uma tecla óbvia para trocar de teclado.
- **Desvio consciente:** a HIG recomenda não ter configuração própria de aparência (`dark-mode.md`). Aqui o pedido era escuro por padrão, então o teclado começa escuro e oferece "Sistema". O app de configurações sempre acompanha o sistema.

## Testes

```bash
./gradlew :app:testDebugUnitTest
```

```bash
./gradlew :app:connectedDebugAndroidTest
```

- **67 testes JVM**: previsão de palavras (prefixo sem acento, maiúsculas, aprendizado, próxima palavra), catálogo de emojis, grafo e ramos, deduplicação, limite e expiração, debounce (tempo virtual), captura antes de apagar, classificação (digitação/exclusão/colagem/externa), bloqueio por privacidade, sessões, exclusão/limpeza, completude da leitura, pré-condições da restauração, Enter contextual, layouts (toda fileira soma 10 unidades), acentos, shift/Caps Lock.
- **10 testes instrumentados** com `EditText` e o `InputConnection` real do Android: leitura completa com cursor no meio e com seleção, colagem e limpeza feitas pelo app, movimento de cursor sem gerar versão, restauração sem duplicar texto e com cursor restaurado, desfazer, restauração com seleção no meio, troca de campo, versão de outro campo recusada, sem conexão, campo que não pode ser lido por inteiro, limpeza total.
- **Validação manual no emulador** (Pixel 6, API 31): digitar no `TextField` do Compose, ver as versões na faixa, abrir pela barra de espaço, comparar, restaurar, desfazer e criar um ramo.

Não cobertos automaticamente: o fluxo de ponta a ponta com o teclado selecionado como IME do sistema (foi validado à mão), WebView/Chrome e apps de terceiros.

## Riscos e limitações conhecidos

- Apps que limitam o que um teclado lê (alguns WebViews, editores próprios) geram só versões parciais, que não podem ser restauradas.
- Editores que reformatam o texto podem não confirmar a restauração; o texto anterior continua salvo.
- Campos reutilizados para conteúdos diferentes (ex.: a mesma caixa de mensagem em conversas diferentes) compartilham a sessão dentro da retenção. A restauração continua segura, porque o texto substituído é sempre guardado antes.
- Sem autocorreção nem swipe. As sugestões só completam a palavra e preveem a próxima pelo que você já escreveu; não há modelo de linguagem nem correção automática.
- A lista de palavras vem de legendas de filmes: inclui nomes próprios em minúsculas e algumas gírias. Palavrões e termos ofensivos foram removidos das sugestões (continuam podendo ser digitados).
- Não há busca de emojis por nome.
- Sem modo flutuante: ele exige uma janela de teclado que não empurra o app, com regiões de toque próprias; fica para uma próxima etapa.
- Encerrar o processo do app (atualizar pelo adb, rodar testes com `force-stop`) faz o Android desselecionar o teclado; é preciso escolhê-lo de novo.
- Teclas não crescem com o tamanho de fonte do sistema além de 115%, para caber na tela.
