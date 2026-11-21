# Expense Tracker brand kit

**The mark:** an H whose crossbar is the lower bar of a rupee sign. The rupee sits between two tall stems, so it reads as an H first and a rupee second.

## Files
| File | Use |
|---|---|
| `svg/expense-tracker-symbol.svg` | Master mark, pine stems + gold rupee, on light backgrounds |
| `svg/expense-tracker-symbol-reversed.svg` | On a pine background (cream stems, gold rupee) |
| `svg/expense-tracker-symbol-black.svg`, `-white.svg` | One-colour (print, stamps, themed icons) |
| `svg/expense-tracker-lockup-horizontal.svg`, `-reversed.svg` | Mark + wordmark, side by side (headers, README, store graphics) |
| `svg/expense-tracker-lockup-stacked.svg` | Mark above wordmark (splash, posters) |
| `svg/app-icon-512.svg`, `png/app-icon-512.png` | Google Play 512 px icon (full bleed, opaque; Play applies its own mask) |
| `png/expense-tracker-symbol*.png` | 1024 px raster of the symbol |
| `svg/expense-tracker-notification-24.svg` | Small-size cut used for the notification icon (white on transparent) |

The Android icons live in `app/src/main/res/drawable/`:
`ic_launcher_fg.xml` (adaptive foreground, on `@color/launcher_bg` pine), `ic_launcher_mono.xml` (Android 13+ themed icon), `ic_stat.xml` (notification).
They are hand-written VectorDrawables of the same geometry. If the mark changes, regenerate all of these together.

## Colours
| Name | Hex | Role |
|---|---|---|
| Pine | `#14604C` | Primary, stems, wordmark, icon background |
| Gold | `#F2B632` | The rupee only |
| Cream | `#FFF8E6` | Stems on pine |
| Paper | `#F7F1E3` | Light backgrounds |

## Wordmark
Set in **Nunito ExtraBold (800)**, converted to outlines in the lockup SVGs (so the font is not needed to use them). Nunito is licensed under the SIL Open Font License 1.1, which permits this use. Letter-spacing is slightly tightened (-6/1000 em units at the lockup size).

## Rules
- **Clear space:** at least the width of one stem (about 1/4 of the mark width) on every side.
- **Minimum size:** symbol 24 px (below that, use the notification cut); lockup 120 px wide.
- **Background:** pine or cream/white. On photos or mid-tone colours use the one-colour white or black version.
- **One colour:** stems, crossbar and rupee all take the same colour; the rupee shape still reads because of the gaps between the stems and the rupee's top bar.
- **Don't:** recolour the rupee, rotate or skew the mark, add outlines or shadows, put the gold rupee on a yellow/orange background, or redraw the rupee in a different style.

## Notes and limits
- The rupee glyph in the SVGs is built from strokes (round caps), not expanded outlines. Fine for screens and normal printing; expand strokes before sending to a vendor that needs outlined artwork.
- At 16 px the rupee detail blurs and the mark reads as an H with a gold bar. That is the intended fallback.
- No trademark or app-store name clearance has been done for "Expense Tracker" or the mark.
