package app.flipscan.data

import android.content.Context
import android.content.SharedPreferences

enum class Paper(val label: String, val wMm: Float, val hMm: Float) {
    A4("A4", 210f, 297f),
    A5("A5", 148f, 210f),
    LETTER("Letter", 215.9f, 279.4f),
    CUSTOM("Custom", 0f, 0f)
}

enum class CardLayout(val label: String) { STACKED("Stacked"), SIDE_BY_SIDE("Side by side") }

enum class Filter(val label: String) {
    ORIGINAL("Original"), GRAY("Grayscale"), BW("Black & white"), MAGIC("Magic color")
}

enum class Format(val label: String, val ext: String, val mime: String) {
    PDF("PDF", "pdf", "application/pdf"),
    JPG("JPG", "jpg", "image/jpeg"),
    PNG("PNG", "png", "image/png")
}

/** Output settings shared by both modes. Persisted between runs. */
data class ExportSettings(
    val paper: Paper = Paper.A4,
    val customW: Float = 210f,
    val customH: Float = 297f,
    val layout: CardLayout = CardLayout.STACKED,
    val marginMm: Float = 10f,
    val cutLine: Boolean = false,
    val realSize: Boolean = true,
    val filter: Filter = Filter.ORIGINAL,
    val format: Format = Format.PDF,
    /** 0 = no limit. */
    val targetKb: Int = 0
) {
    val paperW: Float get() = if (paper == Paper.CUSTOM) customW else paper.wMm
    val paperH: Float get() = if (paper == Paper.CUSTOM) customH else paper.hMm
}

object SettingsStore {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("flipscan_settings", Context.MODE_PRIVATE)

    fun load(ctx: Context): ExportSettings {
        val p = prefs(ctx)
        val d = ExportSettings()
        return ExportSettings(
            paper = p.enum("paper", d.paper),
            customW = p.getFloat("customW", d.customW),
            customH = p.getFloat("customH", d.customH),
            layout = p.enum("layout", d.layout),
            marginMm = p.getFloat("marginMm", d.marginMm),
            cutLine = p.getBoolean("cutLine", d.cutLine),
            realSize = p.getBoolean("realSize", d.realSize),
            filter = p.enum("filter", d.filter),
            format = p.enum("format", d.format),
            targetKb = p.getInt("targetKb", d.targetKb)
        )
    }

    fun save(ctx: Context, s: ExportSettings) {
        prefs(ctx).edit()
            .putString("paper", s.paper.name)
            .putFloat("customW", s.customW)
            .putFloat("customH", s.customH)
            .putString("layout", s.layout.name)
            .putFloat("marginMm", s.marginMm)
            .putBoolean("cutLine", s.cutLine)
            .putBoolean("realSize", s.realSize)
            .putString("filter", s.filter.name)
            .putString("format", s.format.name)
            .putInt("targetKb", s.targetKb)
            .apply()
    }
}

private inline fun <reified T : Enum<T>> SharedPreferences.enum(key: String, def: T): T =
    runCatching { enumValueOf<T>(getString(key, def.name) ?: def.name) }.getOrDefault(def)
