group = "com.devo"

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
