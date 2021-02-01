object Libs {
    const val kotlinLogging = "io.github.microutils:kotlin-logging:${Versions.kotlinLogging}"
    const val kotlinCoroutinesCore = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinCoroutines}"
    const val kotlinSerializationJson =
        "org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinSerialization}"
    const val kotlinSerializationCore =
        "org.jetbrains.kotlinx:kotlinx-serialization-core:${Versions.kotlinSerialization}"
    const val kotlinStdLib = "stdlib-jdk8"
    const val awaitility = "org.awaitility:awaitility:${Versions.awaitility}"
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
    const val kafkaClient = "org.apache.kafka:kafka-clients:${Versions.kafka}"
    const val junitJupiterApi = "org.junit.jupiter:junit-jupiter-api:${Versions.junit}"
    const val junitJupiterEngine = "org.junit.jupiter:junit-jupiter-engine:${Versions.junit}"
    const val junitJupiterParams = "org.junit.jupiter:junit-jupiter-params:${Versions.junit}"
    const val kafkaTestContainers = "org.testcontainers:kafka:1.15.1"
    const val commonsValidator = "commons-validator:commons-validator:${Versions.commonsValidator}"

    val logging = listOf(
        logbackCore, logbackClassic, kotlinLogging
    )
}
