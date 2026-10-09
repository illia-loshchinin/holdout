# App icon

The Holdout app icon, v2: a 5×5 crowd of equal members on black, seven of them in Tide Light (`#80ECFF`) forming an H joined by three strokes. See **App icon** under Iconography in the brand book.

- `holdout-icon.svg`: the master, square on `#000000`. Inks `#FFFFFF` (members) and `#80ECFF` (the H).
- `holdout-icon-1024.png`, `holdout-icon-store-512.png`: the same, for the store and decks; the store applies its own mask.
- `holdout-icon-squircle-512.png`: squircle-masked, for presentations only.
- `holdout-icon-foreground.svg`, `holdout-icon-foreground-1024-transparent.png`: the adaptive-icon foreground on the 108dp grid, transparent ground, everything inside the 66dp safe zone.
- `holdout-icon-mono.svg`: the themed-icon layer: the H alone, one ink `#FFFFFF` (dot radius 5.6, strokes 5).
- `holdout-status-icon-24.svg`: the status-bar icon: the same H alone, one ink `#FFFFFF`, for Android to tint.
- `holdout-launch-loop.svg`: the launch loop, 4000 ms, as an animated SVG for the web and decks (Android: the splash, `avd_holdout_splash`, plays the same motion once in 1000 ms).
- `holdout-loader.svg`, `holdout-loader-on-black.svg`: the loader, 2400 ms, on a transparent and on a black ground (Compose: `HoldoutLoader`, cropped to the crowd).

The animated SVGs and Android vectors use one fixed pick of survivors; the app's Compose loader picks seven new ones every loop. All are static under reduced motion.
