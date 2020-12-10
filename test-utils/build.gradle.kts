group = "com.devo.feeds.testutils"

plugins {
    id("feeds.library-conventions")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":data"))
    implementation(kotlin(Libs.kotlinStdLib))
    implementation(Libs.ktorServerCore)
    implementation(Libs.ktorServerNetty)
    implementation(Libs.ktorServerSerialization)
    implementation(Libs.kotlinLogging)
    implementation(Libs.kotlinSerializationJson)
    implementation(Libs.kotlinCoroutinesCore)
    implementation(Libs.awaitility)
}
