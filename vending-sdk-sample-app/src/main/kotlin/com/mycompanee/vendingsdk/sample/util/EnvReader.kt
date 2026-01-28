package com.mycompanee.vendingsdk.sample.util

import android.content.Context
import android.content.pm.ApplicationInfo
import java.io.File

object EnvReader {
    private var cachedEnv: Map<String, String>? = null

    /**
     * Reads .env file from the app's external files directory or assets
     * Only works in debug builds
     */
    fun loadEnv(context: Context): Map<String, String> {
        if (cachedEnv != null) {
            return cachedEnv!!
        }

        val env = mutableMapOf<String, String>()

        try {
            // Try to read from external files directory first (for easy editing)
            val externalFilesDir = context.getExternalFilesDir(null)
            if (externalFilesDir != null) {
                val envFile = File(externalFilesDir, ".env")
                if (envFile.exists() && envFile.canRead()) {
                    android.util.Log.d("EnvReader", "Loading .env from external files: ${envFile.absolutePath}")
                    envFile.readLines().forEach { line ->
                        parseLine(line, env)
                    }
                    android.util.Log.d("EnvReader", "Loaded ${env.size} values from external .env file")
                }
            }
            
            // If no values loaded from external, try assets folder
            if (env.isEmpty()) {
                try {
                    android.util.Log.d("EnvReader", "Loading .env from assets")
                    // Try both .env and env (Android sometimes has issues with dotfiles)
                    val assetFiles = listOf("env", ".env")
                    var loaded = false
                    for (fileName in assetFiles) {
                        try {
                            val inputStream = context.assets.open(fileName)
                            inputStream.bufferedReader().useLines { lines ->
                                lines.forEach { line ->
                                    parseLine(line, env)
                                }
                            }
                            android.util.Log.d("EnvReader", "Loaded ${env.size} values from assets file: $fileName")
                            loaded = true
                            break
                        } catch (e: java.io.FileNotFoundException) {
                            // Try next filename
                            android.util.Log.d("EnvReader", "File $fileName not found, trying next...")
                            continue
                        } catch (e: Exception) {
                            android.util.Log.d("EnvReader", "Error reading $fileName: ${e.message}")
                            continue
                        }
                    }
                    if (!loaded) {
                        android.util.Log.w("EnvReader", ".env not found in assets (tried: ${assetFiles.joinToString()})")
                    }
                } catch (e: Exception) {
                    // .env not in assets, that's OK
                    android.util.Log.w("EnvReader", ".env not found in assets: ${e.message}")
                }
            }
        } catch (e: Exception) {
            // .env file not found or error reading - this is OK, just return empty map
            android.util.Log.e("EnvReader", "Could not load .env file: ${e.message}", e)
        }

        cachedEnv = env
        return env
    }

    private fun parseLine(line: String, env: MutableMap<String, String>) {
        val trimmed = line.trim()
        // Skip empty lines and comments
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return
        }

        val equalsIndex = trimmed.indexOf('=')
        if (equalsIndex > 0) {
            val key = trimmed.substring(0, equalsIndex).trim()
            val value = trimmed.substring(equalsIndex + 1).trim()
            // Remove quotes if present
            val cleanValue = value.removeSurrounding("\"").removeSurrounding("'")
            env[key] = cleanValue
        }
    }

    fun getValue(context: Context, key: String, defaultValue: String = ""): String {
        // Only load .env in debug builds
        val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return if (isDebug) {
            loadEnv(context)[key] ?: defaultValue
        } else {
            defaultValue
        }
    }
}
