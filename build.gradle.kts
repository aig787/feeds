group = "com.devo.feeds"

plugins {
    jacoco
    application
    kotlin("jvm") version Versions.kotlin
    kotlin("plugin.serialization") version Versions.kotlin
    id(Plugins.dokka) version Versions.kotlin
    id(Plugins.detekt) version Versions.detekt
    id(Plugins.testLogger) version Versions.testLogger
    id(Plugins.semverGit)
}

val defaultVersionFormatter = Transformer<Any, io.wusa.Info> { info ->
    "${info.version.major}.${info.version.minor}.${info.version.patch}.build.${info.count}.sha.${info.shortCommit}"
}

semver {
    initialVersion = "0.0.0"
    branches {
        branch {
            regex = "master"
            incrementer = "PATCH_INCREMENTER"
            formatter = Transformer<Any, io.wusa.Info> { info ->
                "${info.version.major}.${info.version.minor}.${info.version.patch}"
            }
        }
        branch {
            regex = ".+"
            incrementer = "NO_VERSION_INCREMENTER"
            formatter = defaultVersionFormatter
        }
    }
}

version = semver.info

allprojects {

    apply(plugin = Plugins.kotlinJvm)
    apply(plugin = Plugins.jacoco)
    apply(plugin = Plugins.detekt)
//    apply(plugin = Plugins.testLogger)
//    apply(plugin = Plugins.kotlinSerialization)

    repositories {
        mavenCentral()
        jcenter()
    }

    val javaVersion = JavaVersion.VERSION_11.toString()

    tasks.compileKotlin {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
        kotlinOptions {
            jvmTarget = javaVersion
        }
    }

    tasks.compileTestKotlin {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
        kotlinOptions {
            jvmTarget = javaVersion
        }
    }

    tasks.detekt {
        jvmTarget = javaVersion
    }

    tasks.test {
        finalizedBy(tasks.jacocoTestReport)
    }
}

application {
    mainClass.set("com.devo.feeds.FeedsServiceKt")
}

tasks.startScripts {
    doLast {
        val contents = unixScript.readText()
        val classpath = contents.split("\n").find { it.startsWith("CLASSPATH") }!!
        val updatedClasspath = classpath.replace("CLASSPATH=", "CLASSPATH=\$CLASSPATH:")
        unixScript.writeText(contents.replace(classpath, updatedClasspath))
    }
}

dependencies {
    implementation(project(":output"))
    implementation(project(":data"))
    implementation(project(":storage"))

    Libs.logging.forEach { implementation(it) }
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("bom"))
    implementation(kotlin("reflect"))
    implementation(Libs.kotlinCoroutinesCore)
    implementation(Libs.kotlinSerializationJson)
    implementation(Libs.config4k)
    implementation(Libs.ktorClientCore)
    implementation(Libs.ktorClientCIO)
    implementation(Libs.jug)

    testImplementation(project(":test-utils"))
    testImplementation(Libs.kotlinTest)
    testImplementation(Libs.kotlinTestJunit)
    testImplementation(Libs.hamkrest)
    testImplementation(Libs.awaitility)
    testImplementation(Libs.mockk)
    testImplementation(Libs.ktorServerCore)
    testImplementation(Libs.ktorServerNetty)
}
