# Oberjein

Sintetizador polifônico para Android, inspirado no Oberheim OB-8. Modelagem de analógico virtual
(sem usar o firmware/ROM original). Motor de áudio em C++ (Oboe, baixa latência) e interface em
Kotlin + Jetpack Compose.

## Como compilar
1. Abra a pasta `Oberjein` no **Android Studio** (Ladybug 2024.2 ou mais novo).
2. Deixe o Gradle sincronizar; ele baixa o Gradle 8.9, o Oboe 1.9.3 e o NDK/CMake se faltarem
   (SDK Manager > SDK Tools > NDK e CMake 3.22.1).
3. Rode em um aparelho real (o emulador tem latência alta). O app abre em paisagem.

## O que tem
- 8 vozes, 2 VCOs por voz (serra e/ou pulso, largura de pulso, semitom, detune, sync) e ruído
- Filtro passa-baixa tipo escada, com chave 12/24 dB, ressonância, envelope e key track
- 2 envelopes ADSR (filtro e amplificador), LFO (5 formas) para pitch, filtro e largura de pulso
- Glide, modo Unison (8 vozes empilhadas com spread), 8 presets, teclado de 2 oitavas multitoque
  (a velocidade depende de onde você toca na tecla), pitch bend e mod wheel via MIDI USB/Bluetooth

## Estrutura
- `app/src/main/cpp/synth.h` — todo o DSP (osciladores PolyBLEP, filtro, envelopes, alocação de vozes)
- `app/src/main/cpp/native-lib.cpp` — stream Oboe + ponte JNI
- `Params.kt` — ids dos parâmetros (mesma ordem de `enum Param` em `synth.h`) e presets
- `ui/` — knobs, teclado e tela principal

Para adicionar um parâmetro: acrescente em `P` (Kotlin) e em `enum Param` (C++) na mesma posição,
trate-o em `updateControl()` e crie o controle em `MainScreen.kt`.

## Observações
- "Oberheim" e "OB-8" são marcas registradas: troque o nome do app antes de publicar na Play Store.
- Os `.BIN` do zip enviado são o firmware Z80 do OB-8 (direitos autorais). Eles não estão no projeto.
