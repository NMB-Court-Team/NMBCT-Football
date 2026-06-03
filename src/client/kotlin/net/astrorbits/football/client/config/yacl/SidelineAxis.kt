package net.astrorbits.football.client.config.yacl

enum class SidelineAxis(val id: String, val translationKey: String) {
    X("x", "yacl3.config.nmbct-football.match.field.sideline_axis.x"),
    Z("z", "yacl3.config.nmbct-football.match.field.sideline_axis.z"),
    ;

    companion object {
        fun fromString(value: String): SidelineAxis =
            entries.find { it.id == value.lowercase() } ?: X
    }
}
