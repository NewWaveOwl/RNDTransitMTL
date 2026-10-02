# Material Symbols

Source: [Google Material Symbols](https://fonts.google.com/icons) and the
[official Google icon repository](https://github.com/google/material-design-icons/tree/master/symbols/web).

The shared drawable folder contains `ic_account_circle.xml`, `ic_receipt_long.xml`,
`ic_settings.xml`, `ic_arrow_drop_up.xml`, `ic_arrow_drop_down.xml`, `ic_close.xml`,
and `ic_home.xml`. Home is included from the linked selection for future use.

These use Material Symbols, weight 400, optical size 24. The three header icons
use fill 1 to match the Figma reference; the remaining symbols use fill 0.
The original SVG paths were converted to Compose-compatible XML vector drawables
with `#F7F0E5` fills. Their original 960-unit viewport and vertical origin are
preserved with a group translation, and each file records its source URL.

The header uses account, receipt, and settings symbols. Receipt opens saved trips;
settings returns to trip selection. Profile is currently a visual marker.
The time controls use the arrow symbols and saved-trip removal uses close.

License: [Apache License 2.0](material-symbols-LICENSE.txt).
