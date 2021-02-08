group = "com.devo.feeds.data"

plugins {
    id("feeds.library-conventions")
}

apply(plugin = Plugins.kotlinSerialization)

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin(Libs.kotlinStdLib))
    implementation(Libs.kotlinSerializationCore)
    implementation("commons-validator:commons-validator:1.7")

    testImplementation(Libs.hamkrest)
    testImplementation(Libs.kotlinTest)
    testImplementation(Libs.kotlinTestJunit)
}
