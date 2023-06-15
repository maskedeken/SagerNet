include(":library:stub")
include(":library:include")
include(":library:proto")
include(":library:proto-stub")

include(":plugin:api")

val buildPlugin = System.getenv("BUILD_PLUGIN")
when {
    buildPlugin.isNullOrBlank() -> {
        include(":plugin:naive")
        include(":plugin:pingtunnel")
        include(":plugin:relaybaton")
        include(":plugin:brook")
        include(":plugin:trojan-go")
        include(":plugin:hysteria")
        include(":plugin:hysteria-v2")
        include(":plugin:mieru")
        include(":plugin:tuic-v5")
    }
    buildPlugin == "none" -> {
    }
    else -> {
        include(":plugin:$buildPlugin")
    }
}

include(":external:preferencex:preferencex")
include(":external:preferencex:preferencex-simplemenu")
include(":external:preferencex:flexbox")
include(":external:preferencex:colorpicker")
include(":external:preferencex:preferencex-colorpicker")

includeBuild("external/editorkit") {
    name = "editorkit"
    dependencySubstitution {
        substitute(module("editorkit:editorkit:2.0.0")).using(project(":editorkit"))
        substitute(module("editorkit:feature-editor:2.0.0")).using(project(":features:feature-editor"))
        substitute(module("editorkit:language-json:2.0.0")).using(project(":languages:language-json"))
    }
}

includeBuild("external/termux-view") {
    name = "termux-view"
    dependencySubstitution {
        substitute(module("termux:terminal-view:1.0")).using(project(":terminal-view"))
    }
}

include(":app")
rootProject.name = "SagerNet"