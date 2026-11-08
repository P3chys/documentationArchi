// Pure Java engine, no Eclipse: document model, keyword formatting, HTML / PDF / Confluence rendering.
val fonts = configurations.create("fonts")

dependencies {
    implementation("io.github.openhtmltopdf:openhtmltopdf-pdfbox:1.1.108")
    fonts("net.sf.jasperreports:jasperreports-fonts:6.21.5") { isTransitive = false }
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

// PDFs need a font with full Unicode coverage embedded: the base PDF fonts have no Czech, Polish, Cyrillic...
tasks.processResources {
    from(fonts.incoming.files.elements.map { files -> files.map { zipTree(it) } }) {
        include("**/DejaVuSans.ttf", "**/DejaVuSans-Bold.ttf", "**/DejaVuSans-Oblique.ttf", "**/DejaVuSans-BoldOblique.ttf",
            "**/DejaVuSansMono.ttf")
        eachFile { path = "io/github/p3chys/documentationarchi/render/fonts/$name" }
        includeEmptyDirs = false
    }
}
