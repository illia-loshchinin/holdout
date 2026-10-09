# App icon

The Holdout app icon: a 5×5 crowd on black with one Holdout in Solana Green. See **App icon** under Iconography in the brand book.

- `holdout-app-icon-512.svg`: the master, square on `#000000`. Inks `#FFFFFF` (members) and `#14F195` (the Holdout).
- `holdout-app-icon-1024.png`, `holdout-app-icon-512.png`: the same, for the store and decks.
- `holdout-app-icon-preview-512.png`: squircle-masked, for presentations only.
- `holdout-app-icon-foreground-108.svg`: the adaptive-icon foreground on the 108dp grid, transparent ground, every dot inside the 66dp safe zone.
- `holdout-app-icon-mono-108.svg`: the themed-icon layer, one ink `#FFFFFF`; the Holdout is the largest dot.
- `holdout-status-icon-24.svg`: the status-bar icon, a 3×3 crowd with the Holdout larger in the middle, one ink `#FFFFFF`.
- `holdout-mark-loop.svg`: the mark's 4 s loop as an animated SVG (CSS), for the web and decks.
- `holdout-splash-wave.svg`: the launch version, the same loop with one green ring spreading out as the Holdout grows (Android: `avd_holdout_splash_wave`, played once in 1000 ms).
- `holdout-loader.svg`: the app's loader, 2400 ms: the members fade out one by one and back in around a steady Holdout. Cropped to the crowd, as the app draws it (Compose: `HoldoutLoader`).

All three are static under reduced motion. The app builds its own versions to the same spec: an AnimatedVectorDrawable for the splash, a Compose canvas for the loader.
