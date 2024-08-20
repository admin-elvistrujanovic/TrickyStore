package io.github.a13e300.tricky_store

import java.io.File
import java.security.MessageDigest
import kotlin.system.exitProcess
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.github.a13e300.tricky_store.Config.initialize

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize Config with the Android context
        initialize(this)
    }
}

fun main(args: Array<String>) {
    verifySelf()
    Logger.i("Welcome to TrickyStore!")
    while (true) {
        if (!KeystoreInterceptor.tryRunKeystoreInterceptor()) {
            Thread.sleep(1000)
            continue
        }
        initialize() // Ensure this method is called with the correct context if needed
        while (true) {
            Thread.sleep(1000000)
        }
    }
}

fun initialize() {
    TODO("Not yet implemented")
}

@OptIn(ExperimentalStdlibApi::class)
fun verifySelf() {
    val kv = mutableMapOf<String, String>()
    val prop = File("./module.prop")
    runCatching {
        if (prop.canonicalPath != "/data/adb/modules/tricky_store/module.prop") error("wrong directory ${prop.canonicalPath}!")
        prop.forEachLine(Charsets.UTF_8) {
            val a = it.split("=", limit = 2)
            if (a.size != 2) return@forEachLine
            kv[a[0]] = a[1]
        }
        MessageDigest.getInstance("SHA-256").run {
            update(kv["id"]!!.toByteArray(Charsets.UTF_8))
            update(kv["name"]!!.toByteArray(Charsets.UTF_8))
            update(kv["version"]!!.toByteArray(Charsets.UTF_8))
            update(kv["versionCode"]!!.toByteArray(Charsets.UTF_8))
            update(kv["author"]!!.toByteArray(Charsets.UTF_8))
            update(kv["description"]!!.toByteArray(Charsets.UTF_8))
            digest().toHexString()
        }
    }
}
