package settings

import kotlin.reflect.KProperty

class JoozdlogServerSettings<T>(private val settingsMap: Map<String, String>, private val defaultValue: T){
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return getPreference(property.name, defaultValue)
    }
/*
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        setPreference(property.name, value)
    }
 */

    private fun getPreference(key: String, defaultValue: T): T {
        with(settingsMap[key]) {
            if (this == null) return defaultValue
            @Suppress("UNCHECKED_CAST")
            return when (defaultValue) {
                is Boolean -> this.toBoolean()
                is Int -> this.toInt()
                is Long -> this.toLong()
                is Float -> this.toFloat()
                is Double -> this.toDouble()
                is String -> this
                else -> throw IllegalArgumentException()
            } as T
        }
    }
/*
    private fun setPreference(key: String, value: T) {
        sharedPrefs.edit {
            when (value) {
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Float -> putFloat(key, value)
                is String -> putString(key, value)
                else -> throw IllegalArgumentException()
            }
        }
    }

 */
}