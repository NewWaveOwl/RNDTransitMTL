# RNDTransitMTL colour system
Updated: 2026-10-27

Source: RND / Android design foundations.

| Token | Hex | Kotlin constant | Use |
| --- | --- | --- | --- |
| Blue | #0076B3 | TransitMain | Header, transport panel, primary controls, planned route |
| Teal | #28454C | TransitComplementary | Dark text, preference bands, sheet handle background |
| White | #F8F8F8 | TransitWhite | Light surfaces, text and icons on dark surfaces |
| Yellow | #FFBA00 | TransitHighlight | Transit-zone band and highlighted progress |
| Lime | #98CA45 | TransitSelected | Selected transport modes, selected zones, GO gradient |
| Orange | #FF8B00 | TransitOrange | Existing route-marker accents |

Use blue text on white controls and teal text on lime or yellow surfaces. Selection must also be exposed through accessibility semantics.

The GO background transitions horizontally from blue to lime. Use LINE Seed JP Regular and Bold.

The foundations palette supersedes the older cream #F7F0E5, lime #9ACA45, and accent #BC9E5C values in previous documentation. Existing Kotlin constants already match this palette.

Semantic error colours remain defined by the Material theme; yellow and orange are not substitutes for error feedback.

Trip legs use `tripLegColor(index)` from `ui/theme/TripLegColors.kt`, keyed by travel
order rather than transport mode. The first eight colours are #0072B2, #D55E00,
#009E73, #AA4499, #827000, #5648CF, #B83C55 and #007F86; longer trips use distinct
generated hues. These route colours are shared by both map renderers, the current
section indicator and the information key. Numbered sections and mode/line labels
also distinguish legs without relying on colour alone.

The attraction-intensity band uses teal with white text. Its slider reverses the earlier blue-track/lime-fill pairing: lime track, blue filled amount, and a white value on blue (teal on the unfilled lime area).
