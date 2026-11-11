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
    val release = coreReleaseVersion.get()
    val releaseTokens = mapOf("CORE_RELEASE" to release)
    inputs.property("coreRelease", release)
    filesMatching(listOf("mcmod.info", "META-INF/miaokatze_core_marker.json")) {
        filter<ReplaceTokens>("tokens" to releaseTokens)
    }
}

tasks.test { failOnNoDiscoveredTests = false }
tasks.register<JavaExec>("runCoreContracts") {
    group = "verification"
    mainClass = "com.miaokatze.miaokatzecore.CoreContracts"
    classpath = sourceSets.getByName("test").runtimeClasspath
}
tasks.named("check") { dependsOn("runCoreContracts") }

tasks.register<JavaExec>("runModelRendererMeshContracts") {
    group = "verification"
    mainClass = "com.miaokatze.miaokatzecore.client.preview.ModelRendererMeshContracts"
    classpath = sourceSets.getByName("test").runtimeClasspath
}

tasks.named("check") { dependsOn("runModelRendererMeshContracts") }

tasks.register<JavaExec>("runNativeItemIdsContracts") {
    group = "verification"
    mainClass = "com.miaokatze.miaokatzecore.api.items.NativeItemIdsContracts"
    classpath = sourceSets.getByName("test").runtimeClasspath
}

tasks.named("check") { dependsOn("runNativeItemIdsContracts") }

mapOf(
    "runAnimationContracts" to "com.miaokatze.miaokatzecore.api.animation.AnimationContracts",
    "runAnimationRuntimeContracts" to "com.miaokatze.miaokatzecore.api.animation.RuntimeContracts",
    "runVisualAnimationContracts" to "com.miaokatze.miaokatzecore.api.animation.VisualContracts",
    "runQualityDemandContracts" to "com.miaokatze.miaokatzecore.api.animation.QualityDemandContracts"
).forEach { (taskName, entryPoint) ->
    tasks.register<JavaExec>(taskName) {
        group = "verification"
        description = "Checks the opt-in animation SDK without a game or graphics context."
        mainClass = entryPoint
        classpath = sourceSets.getByName("test").runtimeClasspath
    }
    tasks.named("check") { dependsOn(taskName) }
}

tasks.register<JavaExec>("runMiaoVisionContracts") {
    group = "verification"
    description = "Checks opt-in MiaoVision simulation, snapshots, bounds and compatibility contracts."
    mainClass = "com.miaokatze.miaokatzecore.api.miaovision.MiaoVisionContracts"
    classpath = sourceSets.getByName("test").runtimeClasspath
}
tasks.named("check") { dependsOn("runMiaoVisionContracts") }

tasks.register<JavaExec>("runWorkGovernorContracts") {
    group = "verification"
    mainClass = "com.miaokatze.miaokatzecore.api.work.WorkGovernorContracts"
    classpath = sourceSets.getByName("test").runtimeClasspath
}
tasks.named("check") { dependsOn("runWorkGovernorContracts") }

tasks.register<JavaExec>("runRecipeRegistrationContracts") {
    group = "verification"
    mainClass = "com.miaokatze.miaokatzecore.api.recipe.RecipeRegistrationContracts"
    classpath = sourceSets.getByName("test").runtimeClasspath + sourceSets.getByName("main").compileClasspath
}

tasks.named("check") { dependsOn("runRecipeRegistrationContracts") }

// Native optional API contracts compile against the same compile-only surfaces as production.
configurations.named("testCompileOnly") { extendsFrom(configurations.getByName("compileOnly")) }

mapOf(
    "runQuestPreflightContracts" to "com.miaokatze.miaokatzecore.api.bq.QuestPreflightContracts",
    "runAngelicaHandContracts" to "com.miaokatze.miaokatzecore.client.camera.AngelicaHandContracts",
    "runAngelicaProgramContracts" to "com.miaokatze.miaokatzecore.client.camera.AngelicaProgramContracts",
    "runAtomicFilePublisherContracts" to "com.miaokatze.miaokatzecore.api.io.AtomicFilePublisherContracts",
    "runSliceReloadContracts" to "com.miaokatze.miaokatzecore.client.camera.slice.SliceReloadContracts"
).forEach { (taskName, entryPoint) ->
    tasks.register<JavaExec>(taskName) {
        group = "verification"
        mainClass = entryPoint
        classpath = sourceSets.getByName("test").runtimeClasspath + sourceSets.getByName("main").compileClasspath
    }
    tasks.named("check") { dependsOn(taskName) }
}

tasks.register<JavaExec>("runUiComponentContracts") {
    group = "verification"
    mainClass = "com.miaokatze.miaokatzecore.api.ui.component.UiComponentContracts"
    classpath = sourceSets.getByName("test").runtimeClasspath
}
tasks.named("check") { dependsOn("runUiComponentContracts") }

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
    "runOrientedSceneContracts" to "com.miaokatze.miaokatzecore.api.miaovision.OrientedSceneContracts",
    "runGuidanceContracts" to "com.miaokatze.miaokatzecore.api.ui.guidance.GuidanceContracts",
    "runViewportCoordinateContracts" to "com.miaokatze.miaokatzecore.api.camera.ViewportCoordinateContracts",
    "runCamera014MathContracts" to "com.miaokatze.miaokatzecore.api.camera.Camera014MathContracts",
    "runCamera014ValueContracts" to "com.miaokatze.miaokatzecore.client.camera.Camera014ValueContracts",
    "runCameraFrameStoreContracts" to "com.miaokatze.miaokatzecore.client.camera.CameraFrameStoreContracts",
    "runCameraPointerContracts" to "com.miaokatze.miaokatzecore.client.camera.CameraPointerContracts",
    "runCameraHudContracts" to "com.miaokatze.miaokatzecore.client.camera.hud.CameraHudContracts",
    "runSliceMeshContracts" to "com.miaokatze.miaokatzecore.client.camera.slice.SliceMeshContracts",
    "runSliceBackendContracts" to "com.miaokatze.miaokatzecore.client.camera.slice.SliceBackendContracts",
    "runAngelicaSliceContracts" to "com.miaokatze.miaokatzecore.client.camera.slice.AngelicaSliceContracts",
    "runTessellatorSliceContracts" to "com.miaokatze.miaokatzecore.client.render.TessellatorSliceContracts",
    "runScreenLineContracts" to "com.miaokatze.miaokatzecore.client.render.ScreenLineContracts",
    "runCameraRenderBoundaryContracts" to "com.miaokatze.miaokatzecore.mixin.camera.CameraRenderBoundaryContracts"
).forEach { (taskName, entryPoint) ->
    tasks.register<JavaExec>(taskName) {
        group = "verification"
        mainClass = entryPoint
        classpath = sourceSets.getByName("test").runtimeClasspath
        if (taskName == "runCameraFrameStoreContracts" || taskName == "runAngelicaSliceContracts" || taskName == "runTessellatorSliceContracts" || taskName == "runScreenLineContracts") {
            // Exercise the production slice click fence without launching Minecraft or an OpenGL context.
            classpath += sourceSets.getByName("main").compileClasspath
        }
    }
    tasks.named("check") { dependsOn(taskName) }
}

// A complete replacement variant, never an addon to the ordinary Core jar.
val testlabSourceSet = sourceSets.create("testlab") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += output + compileClasspath + sourceSets.main.get().runtimeClasspath
}
configurations.named(testlabSourceSet.implementationConfigurationName) { extendsFrom(configurations.implementation.get()) }
configurations.named(testlabSourceSet.compileOnlyConfigurationName) { extendsFrom(configurations.compileOnly.get()) }
configurations.named(testlabSourceSet.annotationProcessorConfigurationName) { extendsFrom(configurations.annotationProcessor.get()) }
sourceSets.test {
    compileClasspath += testlabSourceSet.output
    runtimeClasspath += testlabSourceSet.output
}
val testlabJar = tasks.register<Jar>("testlabJar") {
    group = "build"
    description = "Complete opt-in testlab replacement; do not install alongside the ordinary jar."
    archiveClassifier.set("testlab-dev")
    // Reuse every production CopySpec, including convention-added injected tags and mixin source sets.
    with(tasks.jar.get())
    from(testlabSourceSet.output)
    manifest.from(tasks.jar.get().manifest)
    manifest.attributes("Miaokatze-Testlab-Variant" to "replacement")
}
// RFG 2.x creates and fully configures this task through its reobf<JarTaskName> rule.
tasks.named<com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar>("reobfTestlabJar") {
    archiveClassifier.set("testlab")
    referenceClasspath.from(testlabSourceSet.compileClasspath)
    val productionReobf = tasks.named<com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar>("reobfJar")
    extraSrgFiles.from(productionReobf.map { it.extraSrgFiles })
    extraSrgEntries.addAll(productionReobf.flatMap { it.extraSrgEntries })
}
val testlabSourceCommit = providers.exec { commandLine("git", "rev-parse", "HEAD") }.standardOutput.asText.map { it.trim() }
val testlabSourceDirty = providers.exec { commandLine("git", "status", "--porcelain", "--untracked-files=normal") }.standardOutput.asText.map { it.isNotBlank().toString() }
tasks.withType<Jar>().configureEach {
    manifest.attributes("Miaokatze-Source-Commit" to testlabSourceCommit.get(), "Miaokatze-Source-Dirty" to testlabSourceDirty.get())
}
tasks.register<JavaExec>("runTestlabContracts") {
    group = "verification"
    mainClass = "com.miaokatze.miaokatzecore.api.testlab.TestlabContracts"
    classpath = sourceSets.test.get().runtimeClasspath + sourceSets.main.get().compileClasspath
}
tasks.named("check") { dependsOn("runTestlabContracts") }

// Both complete variants share the main publication coordinate and are explicit replacement classifiers.
extensions.configure<PublishingExtension> {
    publications.withType<MavenPublication>().configureEach {
        version = coreReleaseVersion.get()
        groupId = "com.miaokatze.miaokatzecore"
        artifactId = "miaokatzecore"
        artifact(testlabJar)
        artifact(tasks.named("reobfTestlabJar"))
    }
}

// The convention computes its own Git-derived publication version in afterEvaluate.
// Apply the explicit public coordinate after that callback, without changing archive provenance names.
afterEvaluate {
    extensions.configure<PublishingExtension> {
        publications.withType<MavenPublication>().configureEach { version = coreReleaseVersion.get() }
    }
}
tasks.named("assemble") { dependsOn("reobfTestlabJar") }

// Package and verify the project-specific code and independent-asset licenses.
apply(from = "gradle/licensing.gradle.kts")
