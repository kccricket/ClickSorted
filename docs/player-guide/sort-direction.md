# Sort Direction

By default, sorted items are placed starting from the **top-left corner**, filling row by row left to right. You can change this per-player with two independent settings.

## Start corner

`/clicksorted sort start-corner <TOP_LEFT|TOP_RIGHT|BOTTOM_LEFT|BOTTOM_RIGHT>`

Controls which corner of the inventory grid receives the first sorted item. All subsequent items fill outward from that corner according to the fill axis.

## Fill axis

`/clicksorted sort fill-axis <HORIZONTAL|VERTICAL>`

Controls whether items fill **row by row** (`HORIZONTAL`, the default) or **column by column** (`VERTICAL`) from the chosen corner.
