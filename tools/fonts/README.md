Fonts for the printable cookbook (tools/build_pdf.py), kept here so every machine builds the same PDF.

- Fira Sans and Crimson Text: SIL Open Font License (OFL-*.txt).
- WenQuanYiZenHei-subset.ttf: a few Chinese characters from WenQuanYi Zen Hei (GPL v2 with font embedding exception), used where a recipe mentions a Chinese ingredient name. Add characters with pyftsubset if a new recipe needs them; build_pdf.py draws any character the main fonts lack with this font.
