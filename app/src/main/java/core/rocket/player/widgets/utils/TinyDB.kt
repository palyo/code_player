package core.rocket.player.widgets.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TinyDB(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)

    fun getInt(key: String?, value: Int): Int {
        return preferences.getInt(key, value)
    }

    fun getLong(key: String?, value: Long): Long {
        return preferences.getLong(key, value)
    }

    fun getFloat(key: String?, value: Float): Float {
        return preferences.getFloat(key, value)
    }

    fun getDouble(key: String?, value: Double): Double {
        val number = getString(key, "")
        return try {
            number?.toDouble() ?: value
        } catch (e: NumberFormatException) {
            value
        }
    }

    fun getString(key: String?, value: String?): String? {
        return preferences.getString(key, value)
    }

    fun getBoolean(key: String?, value: Boolean): Boolean {
        return preferences.getBoolean(key, value)
    }

    fun <T> getObject(key: String?, classOfT: Class<T>?): T? {
        val json = getString(key, "")
        val value: T = Gson().fromJson(json, classOfT) ?: return null
        return value
    }

    fun putInt(key: String?, value: Int) {
        checkForNullKey(key)
        preferences.edit { putInt(key, value) }
    }

    fun putLong(key: String?, value: Long) {
        checkForNullKey(key)
        preferences.edit { putLong(key, value) }
    }

    fun putFloat(key: String?, value: Float) {
        checkForNullKey(key)
        preferences.edit { putFloat(key, value) }
    }

    fun putDouble(key: String?, value: Double) {
        checkForNullKey(key)
        putString(key, value.toString())
    }

    fun putString(key: String?, value: String?) {
        checkForNullKey(key)
        checkForNullValue(value)
        preferences.edit { putString(key, value) }
    }

    fun putBoolean(key: String?, value: Boolean) {
        checkForNullKey(key)
        preferences.edit { putBoolean(key, value) }
    }

    fun putObject(key: String?, obj: Any?) {
        checkForNullKey(key)
        val gson = Gson()
        putString(key, gson.toJson(obj))
    }

    fun remove(key: String?) {
        preferences.edit { remove(key) }
    }

    fun clear() {
        preferences.edit { clear() }
    }

    fun putStringList(key: String, list: MutableList<String>) {
        checkForNullKey(key)
        val json = Gson().toJson(list)
        putString(key, json)
    }

    fun getStringList(key: String, default: MutableList<String> = mutableListOf()): MutableList<String> {
        val json = getString(key, null)
        return if (!json.isNullOrEmpty()) {
            try {
                Gson().fromJson(json, object : TypeToken<MutableList<String>>() {}.type)
            } catch (e: Exception) {
                default
            }
        } else {
            default
        }
    }

    private fun checkForNullKey(key: String?) {
        if (key == null) {
            throw NullPointerException()
        }
    }

    private fun checkForNullValue(value: String?) {
        if (value == null) {
            throw NullPointerException()
        }
    }

    inline fun <reified T : Enum<T>> getEnum(key: String, defaultValue: T): T {
        val name = getString(key, defaultValue.name)
        return try {
            enumValueOf<T>(name ?: defaultValue.name)
        } catch (e: IllegalArgumentException) {
            defaultValue
        }
    }

    fun putEnum(key: String, value: Enum<*>) {
        putString(key, value.name)
    }
}