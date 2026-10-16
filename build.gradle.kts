
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// jar 文件名（不改 modid）：gtvariablehatch-0.1.0.jar
base {
    archivesName.set("gtvariablehatch")
}

// 版本来源：gradle.properties 的 RELEASE_VERSION。
// gtnh.modules.gitVersion = false 时插件要求在此填充 modVersion，否则仍会走 git tag 推导。
project.ext.set("modVersion", project.findProperty("RELEASE_VERSION") as String? ?: "0.1.0")
