package com.devo.feeds.data

import org.apache.commons.validator.routines.InetAddressValidator

object TypeInference {

    private val IP_VALIDATOR = InetAddressValidator()
    private val DOMAIN_REGEX = Regex(
        "^(?=.{1,255}$)[0-9A-Za-z](?:(?:[0-9A-Za-z]|-){0,61}[0-9A-Za-z])?" +
                "(?:\\.[0-9A-Za-z](?:(?:[0-9A-Za-z]|-){0,61}[0-9A-Za-z])?)*\\.?$"
    )
    private val MD5_REGEX = Regex("[0-9a-f]{32}$")
    private val SHA1_REGEX = Regex("[0-9a-f]{40}\$")
    private val SHA256_REGEX = Regex("[0-9a-f]{64}\$")
    private val URL_REGEX = Regex("^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]")
    private val PORT_ENDING_REGEX = Regex(":\\d+$")

    @Suppress("MagicNumber")
    internal fun isMD5(input: String): Boolean = input.length == 32 && MD5_REGEX.matches(input)

    @Suppress("MagicNumber")
    internal fun isSha1(input: String): Boolean = input.length == 40 && SHA1_REGEX.matches(input)

    @Suppress("MagicNumber")
    internal fun isSha256(input: String): Boolean = input.length == 64 && SHA256_REGEX.matches(input)

    internal fun isIp(input: String): Boolean = IP_VALIDATOR.isValid(input)

    internal fun isDomain(input: String): Boolean = DOMAIN_REGEX.matches(input)

    internal fun isLink(input: String): Boolean = URL_REGEX.matches(input)

    private fun domainOrHostname(input: String): String = when (input.filter { it == '.' }.count()) {
        0 -> "hostname"
        else -> "domain"
    }

    private fun ipOrDomainWithPort(input: String): String {
        val ipOrHost = input.substring(0, input.lastIndexOf(":"))
        return when {
            isIp(ipOrHost) -> "ip|port"
            isDomain(ipOrHost) -> "domain|port"
            else -> "unknown"
        }
    }

    fun inferType(input: String?): String = when (input) {
        null -> "unknown"
        else -> when {
            isIp(input) -> "ip-dst"
            isDomain(input) -> domainOrHostname(input)
            isMD5(input) -> "md5"
            isSha1(input) -> "sha1"
            isSha256(input) -> "sha256"
            isLink(input) -> "link"
            PORT_ENDING_REGEX.matches(input) -> ipOrDomainWithPort(input)
            else -> "unknown"
        }
    }
}
