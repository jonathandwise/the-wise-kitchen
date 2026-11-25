# How the cookbook files are written

Each `NN-name.txt` file is one chapter. `tools/build_cookbook.py` turns them into `data/recipes.json`,
which the app ships with and also downloads from this repo, so a recipe pushed to `main` appears in
the app the next time it opens. No new app version is needed.

```
#chapter Poultry
#blurb One or two sentences shown under the chapter name.

= Recipe Title
serves: 4 (3 tenders each)        <- optional: serves / makes / time / temp
time: Prep 5 min · Cook 30 min
temp: 400°F / 200°C
from: Site or author | domain.com <- the source, shown as "Adapted from ..."
note: An optional intro paragraph.
[Ingredients]                     <- or [Ingredients: Glaze] for a named group
- 12 chicken tenders (1¼ lb)
[Method]                          <- or [Method: Pan gravy]
- One step per line, numbered automatically.
  - An indented line is a sub-point of the step above it.
[Tips]
- One tip per line.
```

Guides (the Kitchen Reference chapter) add `kind: guide` and use sections instead:

```
= Guide Title
kind: guide
from: Source | domain.com
note: Intro paragraph.
[Section: Heading]                <- or [Section] with no heading
p: A paragraph.
h: A small subheading.
- A bullet point.
n: A numbered step.
|! Column | Column               <- table header row
| Cell | Cell                     <- table row (same number of cells)
```

`**bold**` works anywhere. Temperatures are written °F with °C alongside, e.g. `350°F (175°C)`.

After editing, run `python3 tools/build_cookbook.py` and commit the `.txt` file, `data/recipes.json`
and `The Wise Kitchen.pdf`. The app build checks that the text and JSON match; the "Cookbook PDF"
workflow rebuilds the PDF if it's behind.
