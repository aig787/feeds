group = "com.devo.feeds.output"

plugins {
    id("feeds.library-conventions")
}

dependencies {
    api(project(":data"))
    api(Libs.cloudbeesSyslog)
    api(Libs.kotlinCoroutinesCore)

    implementation(kotlin(Libs.kotlinStdLib))
    implementation(Libs.kotlinLogging)
    implementation(Libs.kotlinSerializationJson)
    implementation(Libs.config4k)

    testImplementation(project(":test-utils"))
    testImplementation(project(":storage"))
    Libs.logging.forEach { testImplementation(it) }
    testImplementation(Libs.hamkrest)
    testImplementation(Libs.awaitility)
    testImplementation(Libs.kotlinTest)
    testImplementation(Libs.kotlinTestJunit)
}
