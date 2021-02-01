group = "com.devo.feeds.storage"

plugins {
    id("feeds.library-conventions")
}

repositories {
    mavenCentral()
}

dependencies {
    api(Libs.config4k)
    api(Libs.mapdb)

    implementation(kotlin(Libs.kotlinStdLib))

    testImplementation(Libs.hamkrest)
    testImplementation(Libs.junitJupiterParams)
    testImplementation(Libs.junitJupiterApi)

    testRuntimeOnly(Libs.junitJupiterEngine)
}
