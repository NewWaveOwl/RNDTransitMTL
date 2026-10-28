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

The attraction-intensity band uses teal with white text. Its slider reverses the earlier blue-track/lime-fill pairing: lime track, blue filled amount, and a white value on blue (teal on the unfilled lime area).
