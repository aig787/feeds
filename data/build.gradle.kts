group = "com.devo.feeds.data"

plugins {
    id("feeds.library-conventions")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin(Libs.kotlinStdLib))
    implementation(Libs.kotlinSerializationCore)
}
