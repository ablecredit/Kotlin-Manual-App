package com.ablecredit.manualkotlinclient.data.repository

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class DropdownItemsRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "DropdownItemsPrefs"
        private const val KEY_CUSTOM_PRODUCTS = "custom_products"
        private const val KEY_CUSTOM_BUSINESS_MODELS = "custom_business_models"

        private val DEFAULT_PRODUCTS = listOf("LAP", "Unsecured")
        private val DEFAULT_BUSINESS_MODELS =
            listOf("Trading", "Manufacturing", "Service", "Agri", "Job Work", "Daily Wages", "Salaried")
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getProducts(): List<String> = merged(DEFAULT_PRODUCTS, loadCustom(KEY_CUSTOM_PRODUCTS))

    fun getBusinessModels(): List<String> =
        merged(DEFAULT_BUSINESS_MODELS, loadCustom(KEY_CUSTOM_BUSINESS_MODELS))

    fun addProduct(product: String) = addCustom(KEY_CUSTOM_PRODUCTS, product.trim())

    fun addBusinessModel(model: String) = addCustom(KEY_CUSTOM_BUSINESS_MODELS, model.trim())

    private fun addCustom(key: String, value: String) {
        if (value.isEmpty()) return
        val existing = loadCustom(key)
        if (existing.any { it.equals(value, ignoreCase = true) }) return
        saveCustom(key, existing + value)
    }

    private fun loadCustom(key: String): List<String> {
        val raw = prefs.getString(key, "[]") ?: "[]"
        val out = mutableListOf<String>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val v = arr.optString(i, "").trim()
                if (v.isNotEmpty()) out.add(v)
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun saveCustom(key: String, items: List<String>) {
        val arr = JSONArray().also { a -> items.forEach { a.put(it) } }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun merged(defaults: List<String>, customs: List<String>): List<String> {
        val result = defaults.toMutableList()
        for (custom in customs) {
            if (result.none { it.equals(custom, ignoreCase = true) }) result.add(custom)
        }
        return result
    }
}
