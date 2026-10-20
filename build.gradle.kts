import org.apache.tools.ant.filters.ReplaceTokens

plugins { id("com.gtnewhorizons.gtnhconvention") }

extensions.configure<com.gtnewhorizons.retrofuturagradle.MinecraftExtension>("minecraft") {
    injectedTags.put("RELEASE_VERSION", providers.gradleProperty("RELEASE_VERSION"))
}

val coreReleaseVersion = providers.gradleProperty("RELEASE_VERSION")

tasks.jar {
    // IEarlyMixinLoader alone selects the client list. The convention's default config does not exist here.
    manifest.attributes.remove("MixinConfigs")
    manifest.attributes(
        "FMLCorePlugin" to "com.miaokatze.miaokatzecore.bootstrap.CoreLoadingPlugin",
        "FMLCorePluginContainsFMLMod" to "true"
    )
}

tasks.processResources {
    inputs.property("coreRelease", coreReleaseVersion)
    filesMatching(listOf("mcmod.info", "META-INF/miaokatze_core_marker.json")) { filter<ReplaceTokens>("tokens" to mapOf("CORE_RELEASE" to coreReleaseVersion.get())) }
}

tasks.test { failOnNoDiscoveredTests = false }
tasks.register<JavaExec>("runCoreContracts") {
    group = "verification"
    mainClass = "com.miaokatze.miaokatzecore.CoreContracts"
    classpath = sourceSets.getByName("test").runtimeClasspath
}
tasks.named("check") { dependsOn("runCoreContracts") }

tasks.register<JavaExec>("runCameraContracts") {
    group = "verification"
    mainClass = "com.miaokatze.miaokatzecore.api.camera.CameraContracts"
    classpath = sourceSets.getByName("test").runtimeClasspath
}
tasks.register<JavaExec>("runCameraLeaseContracts") {
    group = "verification"
    mainClass = "com.miaokatze.miaokatzecore.client.camera.CameraLeaseContracts"
    classpath = sourceSets.getByName("test").runtimeClasspath
}
tasks.named("check") { dependsOn("runCameraContracts", "runCameraLeaseContracts") }
