package holdout.app.lab

import com.tencent.mmkv.MMKV
import holdout.repcounter.Stakes

/**
 * How a Lab set is counted: exactly as the member app counts that sitting
 * (spec 004 FR-003). Nothing is staked — Lab has no wallet and no chain.
 */
enum class LabMode(val label: String, val detail: String, val stakes: Stakes) {
    DAILY_25("Daily 25", "45° · 40–60° · knees allowed", Stakes.DAILY),
    EASY("Easy Holdout", "45° · 40–60° · knees allowed", Stakes.EASY),
    HARD("Hard Holdout", "45° · from the toes", Stakes.STAKED),
}

/** Where a set banks: its own scope, so a new set never inherits reps (research R3). */
fun labScope(mode: LabMode, startMillis: Long): String = "lab:${mode.stakes.name}:$startMillis"

/** Lab's own settings, kept on the phone (data-model §2). */
interface LabPrefs {
    fun bool(key: String, default: Boolean): Boolean
    fun setBool(key: String, value: Boolean)
    fun int(key: String, default: Int): Int
    fun setInt(key: String, value: Int)
}

class InMemoryLabPrefs : LabPrefs {
    private val values = mutableMapOf<String, Any>()
    override fun bool(key: String, default: Boolean) = values[key] as? Boolean ?: default
    override fun setBool(key: String, value: Boolean) {
        values[key] = value
    }
    override fun int(key: String, default: Int) = values[key] as? Int ?: default
    override fun setInt(key: String, value: Int) {
        values[key] = value
    }
}

/** MMKV, opened after `MmkvStore.create` has initialised it. */
class MmkvLabPrefs(private val kv: MMKV = requireNotNull(MMKV.mmkvWithID("lab-settings"))) : LabPrefs {
    override fun bool(key: String, default: Boolean) = kv.decodeBool(key, default)
    override fun setBool(key: String, value: Boolean) {
        kv.encode(key, value)
    }
    override fun int(key: String, default: Int) = kv.decodeInt(key, default)
    override fun setInt(key: String, value: Int) {
        kv.encode(key, value)
    }
}

class LabSettings(private val prefs: LabPrefs) {
    /** Off on every fresh install: only people who agreed in writing may be filmed (constitution X). */
    var recordsVideo: Boolean
        get() = prefs.bool(RECORDS_VIDEO, false)
        set(value) = prefs.setBool(RECORDS_VIDEO, value)

    var cleanScreen: Boolean
        get() = prefs.bool(CLEAN_SCREEN, false)
        set(value) = prefs.setBool(CLEAN_SCREEN, value)

    /** Reps that end a set by themselves; Stop ends one sooner. */
    var target: Int
        get() = prefs.int(TARGET, DEFAULT_TARGET).coerceIn(MIN_TARGET, MAX_TARGET)
        set(value) = prefs.setInt(TARGET, value.coerceIn(MIN_TARGET, MAX_TARGET))

    companion object {
        const val DEFAULT_TARGET = 25
        const val MIN_TARGET = 1
        const val MAX_TARGET = 100
        private const val RECORDS_VIDEO = "records_video"
        private const val CLEAN_SCREEN = "clean_screen"
        private const val TARGET = "target"
    }
}
