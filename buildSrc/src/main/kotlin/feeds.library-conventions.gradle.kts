import java.util.Date

plugins {
    `maven-publish`
    `java-library`
    id("com.jfrog.bintray")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            val versionInfo = (project.version as io.wusa.Info)
            groupId = project.group.toString()
            artifactId = "${rootProject.name}-${project.name}"
            version = versionInfo.toString()
            from(components["java"])
            pom {
                packaging = "jar"
                name.set(rootProject.name)
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
            vcsTag = versionInfo.tag
        }
    }
}
