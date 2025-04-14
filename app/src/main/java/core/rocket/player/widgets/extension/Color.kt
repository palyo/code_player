package core.rocket.player.widgets.extension

@OptIn(ExperimentalStdlibApi::class)
fun Int.toColorHexString() = "#" + this.toHexString().uppercase()