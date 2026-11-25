# The Wise Kitchen

My cookbook as an Android app, made for reading recipes from across the kitchen: big text you can
size with A− / A+, search across recipes and ingredients, favorites, and light or dark looks.

## Install
1. On the phone, open the [latest release](https://github.com/jonathandwise/the-wise-kitchen/releases/latest)
   and download `WiseKitchen-<version>.apk`.
2. Open it and allow your browser to install apps when Android asks.

## Updates
- **New recipes** appear by themselves: the app checks `data/recipes.json` in this repo each time it opens.
- **New app versions** download in the background; the app then asks to install them.

## The printable cookbook
[The Wise Kitchen.pdf](The%20Wise%20Kitchen.pdf) is the same cookbook as a PDF. It's rebuilt from the
same recipe files every time a recipe is added or changed.

## Adding recipes
Recipes live in `cookbook/` as plain text (see `cookbook/FORMAT.md`). After an edit, run
`python3 tools/build_cookbook.py` (updates `data/recipes.json` and the PDF) and push.
