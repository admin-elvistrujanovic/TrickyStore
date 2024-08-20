package io.github.a13e300.tricky_store

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.FileObserver
import android.os.ServiceManager
import io.github.a13e300.tricky_store.keystore.CertHack
import java.io.File

// Ensure IPackageManager is imported correctly
import android.content.pm.IPackageManager

object Config {
    private val hackPackages = mutableSetOf<String>()
    private val generatePackages = mutableSetOf<String>()

    private fun getAllInstalledPackages(context: Context): List<String> {
        return try {
            val packageManager = context.packageManager
            val packages: List<PackageInfo> = packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
            packages.map { it.packageName }
        } catch (e: Exception) {
            Logger.e("Error getting installed packages: ${e.message}")
            emptyList()
        }
    }

    private fun updateTargetPackages(f: File?, context: Context) = runCatching {
        hackPackages.clear()
        generatePackages.clear()
        f?.readLines()?.forEach { line ->
            if (line.isNotBlank() && !line.startsWith("#")) {
                val trimmedLine = line.trim()
                if (trimmedLine.startsWith("all")) {
                    val allPackages = getAllInstalledPackages(context)
                    hackPackages.addAll(allPackages)
                    if (trimmedLine.endsWith("!")) {
                        generatePackages.addAll(allPackages)
                    }
                } else {
                    if (trimmedLine.endsWith("!")) {
                        generatePackages.add(trimmedLine.removeSuffix("!").trim())
                    } else {
                        hackPackages.add(trimmedLine)
                    }
                }
            }
        }
        Logger.i("update hack packages: $hackPackages, generate packages=$generatePackages")
    }.onFailure {
        Logger.e("failed to update target files", it)
    }

    private fun updateKeyBox(f: File?, context: Context) = runCatching {
        CertHack.readFromXml(f?.readText())
    }.onFailure {
        Logger.e("failed to update keybox", it)
    }

    private const val CONFIG_PATH = "/data/adb/tricky_store"
    private const val TARGET_FILE = "target.txt"
    private const val KEYBOX_FILE = "keybox.xml"
    private val root = File(CONFIG_PATH)

    // Modified ConfigObserver to accept context
    class ConfigObserver(private val context: Context) : FileObserver(root, CLOSE_WRITE or DELETE or MOVED_FROM or MOVED_TO) {
        override fun onEvent(event: Int, path: String?) {
            path ?: return
            val f = when (event) {
                CLOSE_WRITE, MOVED_TO -> File(root, path)
                DELETE, MOVED_FROM -> null
                else -> return
            }
            when (path) {
                TARGET_FILE -> updateTargetPackages(f, context) // Pass context here
                KEYBOX_FILE -> updateKeyBox(f, context) // Pass context here
            }
        }
    }

    fun initialize(context: Context) {
        root.mkdirs()
        val scope = File(root, TARGET_FILE)
        if (scope.exists()) {
            updateTargetPackages(scope, context) // Pass context here
        } else {
            Logger.e("target.txt file not found, please put it to $scope !")
        }
        val keybox = File(root, KEYBOX_FILE)
        if (!keybox.exists()) {
            Logger.e("keybox file not found, please put it to $keybox !")
        } else {
            updateKeyBox(keybox, context) // Pass context here
        }
        ConfigObserver(context).startWatching() // Pass context here
    }

    private var iPm: IPackageManager? = null

    fun getPm(): IPackageManager? {
        if (iPm == null) {
            iPm = IPackageManager.Stub.asInterface(ServiceManager.getService("package"))
        }
        return iPm
    }

    fun needHack(callingUid: Int) = kotlin.runCatching {
        if (hackPackages.isEmpty()) return false
        val ps = getPm()?.getPackagesForUid(callingUid)
        ps?.any { it in hackPackages }
    }.onFailure { Logger.e("failed to get packages", it) }.getOrNull() ?: false

    fun needGenerate(callingUid: Int) = kotlin.runCatching {
        if (generatePackages.isEmpty()) return false
        val ps = getPm()?.getPackagesForUid(callingUid)
        ps?.any { it in generatePackages }
    }.onFailure { Logger.e("failed to get packages", it) }.getOrNull() ?: false

}
