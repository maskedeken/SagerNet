/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.fmt.trojan_go

import cn.hutool.json.JSONArray
import cn.hutool.json.JSONObject
import com.github.shadowsocks.plugin.PluginConfiguration
import com.github.shadowsocks.plugin.PluginManager
import com.github.shadowsocks.plugin.PluginOptions
import io.nekohasekai.sagernet.IPv6Mode
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.LOCALHOST
import io.nekohasekai.sagernet.fmt.shadowsocks.fixInvalidParams
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.isIpAddress
import io.nekohasekai.sagernet.ktx.queryParameter
import io.nekohasekai.sagernet.ktx.urlSafe
import libcore.Libcore

fun parseTrojanGo(server: String): TrojanGoBean {
    val link = Libcore.parseURL(server)

    return TrojanGoBean().apply {
        serverAddress = link.host
        serverPort = link.port
        password = link.username
        link.queryParameter("sni")?.let {
            sni = it
        }
        link.queryParameter("type")?.let { lType ->
            type = lType

            when (type) {
                "ws" -> {
                    link.queryParameter("host")?.let {
                        host = it
                    }
                    link.queryParameter("path")?.let {
                        path = it
                    }
                }
                else -> {
                }
            }
        }
        link.queryParameter("encryption")?.let {
            encryption = it
        }
        link.queryParameter("plugin")?.let {
            plugin = it
        }
        link.fragment.takeIf { !it.isNullOrBlank() }?.let {
            name = it
        }
    }
}

fun TrojanGoBean.toUri(): String {
    val builder = Libcore.newURL("trojan-go")
    builder.host = serverAddress
    builder.port = serverPort
    builder.username = password

    if (sni.isNotBlank()) {
        builder.addQueryParameter("sni", sni)
    }
    if (type.isNotBlank() && type != "original") {
        builder.addQueryParameter("type", type)

        when (type) {
            "ws" -> {
                if (host.isNotBlank()) {
                    builder.addQueryParameter("host", host)
                }
                if (path.isNotBlank()) {
                    builder.addQueryParameter("path", path)
                }
            }
        }
    }
    if (type.isNotBlank() && type != "none") {
        builder.addQueryParameter("encryption", encryption)
    }
    if (plugin.isNotBlank()) {
        builder.addQueryParameter("plugin", plugin)
    }

    if (name.isNotBlank()) {
        builder.setRawFragment(name.urlSafe())
    }

    return builder.string
}

fun TrojanGoBean.buildTrojanGoConfig(port: Int, mux: Boolean): String {
    return JSONObject().also { conf ->
        conf["run_type"] = "client"
        conf["local_addr"] = LOCALHOST
        conf["local_port"] = port
        conf["remote_addr"] = finalAddress
        conf["remote_port"] = finalPort
        conf["password"] = JSONArray().apply {
            add(password)
        }
        conf["log_level"] = if (DataStore.enableLog) 0 else 2
        if (mux) conf["mux"] = JSONObject().also {
            it["enabled"] = true
            it["concurrency"] = DataStore.muxConcurrency
        }
        conf["tcp"] = JSONObject().also {
            it["prefer_ipv4"] = DataStore.ipv6Mode <= IPv6Mode.ENABLE
        }

        when (type) {
            "original" -> {
            }
            "ws" -> conf["websocket"] = JSONObject().also {
                it["enabled"] = true
                it["host"] = host
                it["path"] = path
            }
        }

        if (sni.isBlank() && finalAddress == LOCALHOST && !serverAddress.isIpAddress()) {
            sni = serverAddress
        }

        conf["ssl"] = JSONObject().also {
            if (sni.isNotBlank()) it["sni"] = sni
            if (allowInsecure) it["verify"] = false
            if (fingerprint.isNotBlank()) it["fingerprint"] = fingerprint
        }

        when {
            encryption == "none" -> {
            }
            encryption.startsWith("ss;") -> conf["shadowsocks"] = JSONObject().also {
                it["enabled"] = true
                it["method"] = encryption.substringAfter(";").substringBefore(":")
                it["password"] = encryption.substringAfter(":")
            }
        }

        if (plugin.isNotBlank()) {
            val pluginConfiguration = PluginConfiguration(plugin ?: "")
            PluginManager.init(pluginConfiguration)?.let { (path, opts, isV2) ->
                conf["transport_plugin"] = JSONObject().also {
                    it["enabled"] = true
                    it["type"] = "shadowsocks"
                    it["command"] = path
                    it["option"] = opts.toString()
                }
            }
        }
    }.toStringPretty()
}

fun buildCustomTrojanConfig(config: String, port: Int): String {
    val conf = JSONObject(config)
    conf["local_port"] = port
    return conf.toStringPretty()
}

fun JSONObject.parseTrojanGo(): TrojanGoBean {
    return TrojanGoBean().applyDefaultValues().apply {
        serverAddress = getStr("remote_addr", serverAddress)
        serverPort = getInt("remote_port", serverPort)
        when (val pass = get("password")) {
            is String -> {
                password = pass
            }
            is List<*> -> {
                password = pass[0] as String
            }
        }
        getJSONObject("ssl")?.apply {
            sni = getStr("sni", sni)
            allowInsecure = getBool("verify", true)
            fingerprint = getStr("fingerprint", fingerprint)
        }
        getJSONObject("websocket")?.apply {
            if (getBool("enabled", false)) {
                type = "ws"
                host = getStr("host", host)
                path = getStr("path", path)
            }
        }
        getJSONObject("shadowsocks")?.apply {
            if (getBool("enabled", false)) {
                encryption = "ss;${getStr("method", "")}:${getStr("password", "")}"
            }
        }
        getJSONObject("transport_plugin")?.apply {
            if (getBool("enabled", false)) {
                when (type) {
                    "shadowsocks" -> {
                        val pl = PluginConfiguration()
                        pl.selected = getStr("command")
                        getJSONArray("arg")?.also {
                            pl.pluginsOptions[pl.selected] = PluginOptions().also { opts ->
                                var key = ""
                                it.forEachIndexed { index, param ->
                                    if (index % 2 != 0) {
                                        key = param.toString()
                                    } else {
                                        opts[key] = param.toString()
                                    }
                                }
                            }
                        }
                        getStr("option")?.also {
                            pl.pluginsOptions[pl.selected] = PluginOptions(it)
                        }
                        pl.fixInvalidParams()
                        plugin = pl.toString()
                    }
                }
            }
        }
    }
}