object Libs {
    const val kotlinLogging = "io.github.microutils:kotlin-logging:${Versions.kotlinLogging}"
    const val kotlinCoroutinesCore = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinCoroutines}"
    const val kotlinSerializationJson =
        "org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinSerialization}"
    const val kotlinSerializationCore =
        "org.jetbrains.kotlinx:kotlinx-serialization-core:${Versions.kotlinSerialization}"
    const val kotlinStdLib = "stdlib-jdk8"
    const val awaitility = "org.awaitility:awaitility:${Versions.awaitility}"
    const val kotlinTest = "org.jetbrains.kotlin:kotlin-test"
    const val kotlinTestJunit = "org.jetbrains.kotlin:kotlin-test-junit"
    const val hamkrest = "com.natpryce:hamkrest:${Versions.hamkrest}"
    const val config4k = "io.github.config4k:config4k:${Versions.config4k}"
    const val mapdb = "org.mapdb:mapdb:${Versions.mapdb}"
    const val logbackCore = "ch.qos.logback:logback-core:${Versions.logback}"
    const val logbackClassic = "ch.qos.logback:logback-classic:${Versions.logback}"
    const val cloudbeesSyslog = "com.cloudbees:syslog-java-client:${Versions.cloudbeesSyslog}"
    const val jug = "com.fasterxml.uuid:java-uuid-generator:${Versions.jug}"
    const val ktorServerCore = "io.ktor:ktor-server-core:${Versions.ktor}"
    const val ktorServerNetty = "io.ktor:ktor-server-netty:${Versions.ktor}"
    const val ktorServerSerialization = "io.ktor:ktor-serialization:${Versions.ktor}"
    const val ktorClientCore = "io.ktor:ktor-client-core:${Versions.ktor}"
    const val ktorClientCIO = "io.ktor:ktor-client-cio:${Versions.ktor}"
    const val mockk = "io.mockk:mockk:${Versions.mockk}"

    val logging = listOf(
        logbackCore, logbackClassic, kotlinLogging
    )
}
