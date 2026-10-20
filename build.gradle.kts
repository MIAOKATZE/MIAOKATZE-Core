import org.apache.tools.ant.filters.ReplaceTokens

plugins { id("com.gtnewhorizons.gtnhconvention") }

extensions.configure<com.gtnewhorizons.retrofuturagradle.MinecraftExtension>("minecraft") {
    injectedTags.put("RELEASE_VERSION", providers.gradleProperty("RELEASE_VERSION"))
}

val coreReleaseVersion = providers.gradleProperty("RELEASE_VERSION")

tasks.processResources {
    inputs.property("coreRelease", coreReleaseVersion)
    filesMatching("mcmod.info") { filter<ReplaceTokens>("tokens" to mapOf("CORE_RELEASE" to coreReleaseVersion.get())) }
}

tasks.test { failOnNoDiscoveredTests = false }
tasks.register<JavaExec>("runCoreContracts") {
    group = "verification"
    mainClass = "com.miaokatze.miaokatzecore.CoreContracts"
    classpath = sourceSets.getByName("test").runtimeClasspath
}
tasks.named("check") { dependsOn("runCoreContracts") }
