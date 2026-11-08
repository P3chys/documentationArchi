allprojects {
    group = "io.github.p3chys.documentationarchi"
    repositories { mavenCentral() }
}

subprojects {
    apply(plugin = "java")
    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }
    tasks.withType<JavaCompile>().configureEach {
        // Java 11 is the oldest runtime among the supported Archi lines (Archi 4.9), so one build serves them all
        options.release.set(11)
        options.encoding = "UTF-8"
    }
}
