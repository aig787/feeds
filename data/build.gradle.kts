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
    implementation(Libs.commonsValidator)

    testImplementation(Libs.hamkrest)
    testImplementation(Libs.junitJupiterApi)
    testRuntimeOnly(Libs.junitJupiterEngine)
}
