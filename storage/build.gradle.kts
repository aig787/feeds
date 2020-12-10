group = "com.devo.feeds.storage"

plugins {
    id("feeds.library-conventions")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin(Libs.kotlinStdLib))
    implementation(Libs.mapdb)
    implementation(Libs.config4k)

    testImplementation(Libs.hamkrest)
    testImplementation(Libs.kotlinTest)
    testImplementation(Libs.kotlinTestJunit)
}

