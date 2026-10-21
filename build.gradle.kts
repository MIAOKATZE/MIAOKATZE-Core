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

mapOf(
    "runViewportCoordinateContracts" to "com.miaokatze.miaokatzecore.api.camera.ViewportCoordinateContracts",
    "runCamera014MathContracts" to "com.miaokatze.miaokatzecore.api.camera.Camera014MathContracts",
    "runCamera014ValueContracts" to "com.miaokatze.miaokatzecore.client.camera.Camera014ValueContracts",
    "runCameraFrameStoreContracts" to "com.miaokatze.miaokatzecore.client.camera.CameraFrameStoreContracts",
    "runCameraPointerContracts" to "com.miaokatze.miaokatzecore.client.camera.CameraPointerContracts",
    "runCameraHudContracts" to "com.miaokatze.miaokatzecore.client.camera.hud.CameraHudContracts",
    "runSliceMeshContracts" to "com.miaokatze.miaokatzecore.client.camera.slice.SliceMeshContracts"
).forEach { (taskName, entryPoint) ->
    tasks.register<JavaExec>(taskName) {
        group = "verification"
        mainClass = entryPoint
        classpath = sourceSets.getByName("test").runtimeClasspath
        if (taskName == "runCameraFrameStoreContracts") {
            // Exercise the production slice click fence without launching Minecraft or an OpenGL context.
            classpath += sourceSets.getByName("main").compileClasspath
        }
    }
    tasks.named("check") { dependsOn(taskName) }
}
