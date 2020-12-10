group = "com.devo"

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
