import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Archi plugin (OSGi bundle). Compiled against the jars of a local Archi installation (-ParchiHome),
// and packaged as a .archiplugin file that analysts install via Archi > Help > Manage Plug-ins.
val archiHome = providers.gradleProperty("archiHome").get()
val bundleId = "io.github.p3chys.documentationarchi"
val qualifier = providers.gradleProperty("qualifier").getOrElse("v" + LocalDateTime.now()
    .format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")))
val bundleVersion = "${project.version}.$qualifier"

val archiPlugins = fileTree(archiHome) {
    include("plugins/*.jar", "plugins/*/*.jar", "Contents/Eclipse/plugins/*.jar", "Contents/Eclipse/plugins/*/*.jar")
}

// Archi's model bundle is plain EMF: enough to build and read models headlessly in unit tests.
val archiModelJars = fileTree(archiHome) {
    for (dir in listOf("plugins", "Contents/Eclipse/plugins")) {
        include("$dir/com.archimatetool.model_*/*.jar", "$dir/org.eclipse.emf.common_*.jar",
            "$dir/org.eclipse.emf.ecore_*.jar", "$dir/org.eclipse.emf.ecore.xmi_*.jar", "$dir/org.eclipse.osgi_*.jar",
            // the editor's model commands, which PrintOrder builds and its tests run
            "$dir/com.archimatetool.editor_*/*.jar", "$dir/org.eclipse.gef_*.jar", "$dir/org.eclipse.draw2d_*.jar")
    }
}

val core = project(":core")

dependencies {
    implementation(core)
    compileOnly(archiPlugins)
    compileOnly("commons-cli:commons-cli:1.9.0") // inside Archi's command line bundle, as a nested jar
    testImplementation(archiModelJars)
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("org.assertj:assertj-core:3.27.6")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

// the PDF library and what it needs, carried inside the plugin (no Archi line bundles them)
val libs = configurations.runtimeClasspath.get().incoming.artifactView {
    componentFilter { it !is ProjectComponentIdentifier }
}.files

tasks.jar {
    archiveFileName.set("${bundleId}_$bundleVersion.jar")
    from(core.extensions.getByType<SourceSetContainer>()["main"].output)
    from(projectDir) { include("plugin.xml") }
    from(libs) { into("lib") }
    inputs.property("bundleVersion", bundleVersion)
    inputs.files(libs)
    manifest {
        from("META-INF/MANIFEST.MF") {
            eachEntry { value = value.toString().replace("\${version}", bundleVersion) }
        }
    }
    doFirst {
        manifest.attributes("Bundle-ClassPath" to (listOf(".") + libs.map { "lib/${it.name}" }).joinToString(","))
    }
}

// The format Archi's "Manage Plug-ins > Install" understands: a zip with an "archi-plugin" marker entry.
val archiplugin = tasks.register<Zip>("archiplugin") {
    group = "distribution"
    description = "Builds the installable documentationArchi .archiplugin file"
    archiveFileName.set("documentationArchi-${project.version}.archiplugin")
    destinationDirectory.set(rootProject.layout.buildDirectory.dir("dist"))
    from(tasks.jar)
    from("archi-plugin") // empty marker entry
}

tasks.assemble { dependsOn(archiplugin) }

// Developer shortcut: copy the bundle straight into Archi's dropins folder (restart Archi afterwards).
tasks.register<Copy>("installDev") {
    group = "distribution"
    description = "Installs the plugin into the local Archi dropins folder"
    val os = System.getProperty("os.name").lowercase()
    val home = System.getProperty("user.home")
    val data = providers.gradleProperty("archiData").getOrElse("Archi") // Archi 4.9 keeps its files in Archi4
    val dropins = if (os.contains("mac")) "$home/Library/Application Support/$data/dropins"
        else if (os.contains("win")) "$home/AppData/Roaming/$data/dropins" else "$home/.${data.lowercase()}/dropins"
    doFirst {
        val running = ProcessHandle.allProcesses().anyMatch { p ->
            p.info().command().map { it.lowercase().replace('\\', '/').let { c -> c.endsWith("/archi.exe") || c.endsWith("/archi") } }
                .orElse(false)
        }
        if (running) throw GradleException("Archi is running - close it (all instances) and run this again")
        // an old copy left behind keeps being loaded by Archi, so a file that cannot be removed is an error
        file(dropins).listFiles { f -> f.name.startsWith(bundleId) }?.forEach {
            if (!it.delete()) throw GradleException("Cannot remove $it - close Archi (all instances) and run it again")
        }
        // Archi only reinstalls dropins it believes changed: forget its notes so the new copy is picked up
        file(dropins).parentFile.resolve("config/org.eclipse.osgi").takeIf { it.isDirectory }?.walkTopDown()
            ?.filter { it.isFile && (it.name == "cache.timestamps" || it.name.startsWith("timestamps")) }
            ?.forEach { it.delete() }
    }
    from(tasks.jar)
    into(dropins)
}
