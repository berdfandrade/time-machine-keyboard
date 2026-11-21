#!/usr/bin/env python3
"""
Gera os dados embutidos no app a partir das fontes públicas:

  emoji-test.txt   https://unicode.org/Public/emoji/latest/emoji-test.txt  (Unicode, termos de uso livres)
  pt_br_50k.txt    https://github.com/hermitdave/FrequencyWords (content/2018/pt_br, CC BY-SA 4.0)

Saídas em app/src/main/assets/:
  emoji.txt        "@grupo" por categoria (nenhum emoji começa com @; "#️⃣" começa com #); depois uma linha por emoji: base e variações de tom de pele, separados por espaço
  words_pt_br.txt  "palavra<TAB>frequência", ordenado por frequência

Uso: tools/build_assets.py <pasta com os dois arquivos de origem>
"""
import re
import sys
import unicodedata
from pathlib import Path

src = Path(sys.argv[1])
out = Path(__file__).resolve().parent.parent / "app/src/main/assets"
out.mkdir(parents=True, exist_ok=True)

# ---- emojis ---------------------------------------------------------------------------------
GROUPS = {  # grupos do Unicode -> categorias do teclado (Pessoas e Corpo junto de Carinhas vira duas abas)
    "Smileys & Emotion": "smileys",
    "People & Body": "people",
    "Animals & Nature": "animals",
    "Food & Drink": "food",
    "Travel & Places": "travel",
    "Activities": "activities",
    "Objects": "objects",
    "Symbols": "symbols",
    "Flags": "flags",
}
TONES = {"\U0001F3FB", "\U0001F3FC", "\U0001F3FD", "\U0001F3FE", "\U0001F3FF"}

groups: dict[str, list[list[str]]] = {v: [] for v in GROUPS.values()}
index: dict[str, list[str]] = {}
group = None
for line in (src / "emoji-test.txt").read_text(encoding="utf-8").splitlines():
    if line.startswith("# group:"):
        group = GROUPS.get(line.split(":", 1)[1].strip())
        continue
    if not line or line.startswith("#") or group is None or "; fully-qualified" not in line:
        continue
    cps = line.split(";")[0].split()
    emoji = "".join(chr(int(c, 16)) for c in cps)
    toned = [c for c in emoji if c in TONES]
    if toned:
        base = "".join(c for c in emoji if c not in TONES)
        # só variações de um tom (as de dois tons, em casais, ficariam longas demais no seletor)
        if base in index and len(toned) == 1:
            index[base].append(emoji)
        continue
    entry = [emoji]
    index[emoji] = entry
    groups[group].append(entry)

with open(out / "emoji.txt", "w", encoding="utf-8") as f:
    for name, entries in groups.items():
        f.write(f"@{name}\n")
        for e in entries:
            f.write(" ".join(e) + "\n")
print("emoji:", {k: len(v) for k, v in groups.items()}, "total", sum(len(v) for v in groups.values()))

# ---- palavras -------------------------------------------------------------------------------
# Palavras nunca sugeridas (ainda podem ser digitadas normalmente).
BLOCK = {
    "porra", "caralho", "merda", "foda", "fodase", "foda-se", "fodido", "fodida", "puta", "puto", "putaria",
    "buceta", "boceta", "cu", "cuzão", "viado", "veado", "bosta", "cacete", "piranha", "vadia", "vagabunda",
    "vagabundo", "otário", "otária", "babaca", "arrombado", "arrombada", "desgraçado", "desgraçada",
    "fdp", "pqp", "vsf", "rola", "pau", "piroca", "pica", "xoxota", "punheta", "bunda", "safado", "safada",
    "corno", "corna", "retardado", "retardada", "bicha", "sapatão", "macaco", "macaca",
}
SINGLE = {"a", "e", "é", "o", "à", "ó", "ô", "ã"}
word_re = re.compile(r"^[a-záàâãéêíóôõúüç]+(-[a-záàâãéêíóôõúüç]+)*$")
words = []
for line in (src / "pt_br_50k.txt").read_text(encoding="utf-8").splitlines():
    parts = line.split()
    if len(parts) != 2:
        continue
    w, n = parts[0].lower(), int(parts[1])
    w = unicodedata.normalize("NFC", w)
    if not word_re.match(w) or w in BLOCK:
        continue
    if len(w) == 1 and w not in SINGLE:
        continue
    words.append((w, n))
words = words[:40000]
with open(out / "words_pt_br.txt", "w", encoding="utf-8") as f:
    for w, n in words:
        f.write(f"{w}\t{n}\n")
print("palavras:", len(words))
