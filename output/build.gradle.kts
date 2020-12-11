group = "com.devo.feeds.output"

plugins {
    id("feeds.library-conventions")
}

apply(plugin = Plugins.kotlinSerialization)

dependencies {
    api(project(":data"))
    api(Libs.cloudbeesSyslog)
    api(Libs.kotlinCoroutinesCore)
    api(Libs.config4k)

    implementation(kotlin(Libs.kotlinStdLib))
    implementation(Libs.kotlinLogging)
    implementation(Libs.kotlinSerializationJson)

    testImplementation(project(":test-utils"))
    testImplementation(project(":storage"))
    Libs.logging.forEach { testImplementation(it) }
    testImplementation(Libs.hamkrest)
    testImplementation(Libs.awaitility)
    testImplementation(Libs.kotlinTest)
    testImplementation(Libs.kotlinTestJunit)
}
