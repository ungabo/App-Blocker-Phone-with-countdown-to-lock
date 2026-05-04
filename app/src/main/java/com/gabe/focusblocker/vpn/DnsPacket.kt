package com.gabe.focusblocker.vpn

data class DnsPacket(
    val sourceAddress: ByteArray,
    val destinationAddress: ByteArray,
    val sourcePort: Int,
    val destinationPort: Int,
    val payload: ByteArray,
    val queryDomain: String?
)

