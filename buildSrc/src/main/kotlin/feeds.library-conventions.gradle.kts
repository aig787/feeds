import java.util.Date

plugins {
    `maven-publish`
    `java-library`
    id("com.jfrog.bintray")
}

val sourcesJar by tasks.creating(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.getByName("main").allSource)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            val versionInfo = (project.version as io.wusa.Info)
            groupId = rootProject.group.toString()
            artifactId = "${rootProject.name}-${project.name}"
            version = versionInfo.toString()
            from(components["java"])
            artifact(sourcesJar)

            pom {
                packaging = "jar"
                licenses {
                    license {
                        name.set("MIT")
                    }
                }
            }
        }
    }
}

bintray {
    user = project.findProperty("bintrayUser").toString()
    key = project.findProperty("bintrayKey").toString()

    val versionInfo = (project.version as io.wusa.Info)
    val versionString = versionInfo.toString()
    publish = !versionString.endsWith("-SNAPSHOT")

    setPublications("maven")

    pkg.apply {
        val githubUrl = "https://github.com/aig787/feeds"
        repo = "feeds"
        name = "${rootProject.name}-${project.name}"
        userOrg = "aig787"
        githubRepo = "aig787/feeds"
        websiteUrl = githubUrl
        vcsUrl = "$githubUrl.git"
        setLicenses("MIT")
        version.apply {
            name = versionInfo.toString()
            released = Date().toString()
            vcsTag = "v${versionInfo.tag}"
        }
    }
}
