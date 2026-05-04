package com.gabe.focusblocker.vpn

import java.nio.ByteBuffer
import java.nio.ByteOrder

object DnsPacketCodec {
    fun parseIpv4UdpDns(packet: ByteArray, length: Int): DnsPacket? {
        if (length < 28) return null
        val version = packet[0].toInt().ushr(4) and 0x0F
        if (version != 4) return null

        val headerLength = (packet[0].toInt() and 0x0F) * 4
        if (length < headerLength + 8) return null
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return null

        val destinationPort = readShort(packet, headerLength + 2)
        if (destinationPort != 53) return null

        val udpLength = readShort(packet, headerLength + 4)
        val payloadOffset = headerLength + 8
        val payloadLength = (udpLength - 8).coerceAtMost(length - payloadOffset)
        if (payloadLength < 12) return null

        val sourceAddress = packet.copyOfRange(12, 16)
        val destinationAddress = packet.copyOfRange(16, 20)
        val sourcePort = readShort(packet, headerLength)
        val payload = packet.copyOfRange(payloadOffset, payloadOffset + payloadLength)

        return DnsPacket(
            sourceAddress = sourceAddress,
            destinationAddress = destinationAddress,
            sourcePort = sourcePort,
            destinationPort = destinationPort,
            payload = payload,
            queryDomain = parseQueryDomain(payload)
        )
    }

    fun blockedResponse(original: DnsPacket): ByteArray {
        val dnsResponse = original.payload.copyOf()
        dnsResponse[2] = 0x81.toByte()
        dnsResponse[3] = 0x83.toByte()
        dnsResponse[6] = 0
        dnsResponse[7] = 0
        dnsResponse[8] = 0
        dnsResponse[9] = 0
        dnsResponse[10] = 0
        dnsResponse[11] = 0
        return wrapDnsResponse(original, dnsResponse)
    }

    fun allowedResponse(original: DnsPacket, dnsResponse: ByteArray): ByteArray {
        return wrapDnsResponse(original, dnsResponse)
    }

    private fun wrapDnsResponse(original: DnsPacket, dnsPayload: ByteArray): ByteArray {
        val totalLength = 20 + 8 + dnsPayload.size
        val packet = ByteArray(totalLength)
        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)

        buffer.put(0x45.toByte())
        buffer.put(0)
        buffer.putShort(totalLength.toShort())
        buffer.putShort(0)
        buffer.putShort(0)
        buffer.put(64.toByte())
        buffer.put(17.toByte())
        buffer.putShort(0)
        buffer.put(original.destinationAddress)
        buffer.put(original.sourceAddress)

        val checksum = ipv4Checksum(packet, 0, 20)
        packet[10] = (checksum ushr 8).toByte()
        packet[11] = checksum.toByte()

        buffer.position(20)
        buffer.putShort(original.destinationPort.toShort())
        buffer.putShort(original.sourcePort.toShort())
        buffer.putShort((8 + dnsPayload.size).toShort())
        buffer.putShort(0)
        buffer.put(dnsPayload)

        return packet
    }

    private fun parseQueryDomain(payload: ByteArray): String? {
        var offset = 12
        val labels = mutableListOf<String>()
        while (offset < payload.size) {
            val length = payload[offset].toInt() and 0xFF
            if (length == 0) break
            if (length and 0xC0 != 0) return null
            offset += 1
            if (offset + length > payload.size) return null
            labels += payload.copyOfRange(offset, offset + length).toString(Charsets.UTF_8)
            offset += length
        }
        return labels.takeIf { it.isNotEmpty() }?.joinToString(".")
    }

    private fun readShort(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
    }

    private fun ipv4Checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length) {
            sum += readShort(data, i)
            while (sum > 0xFFFF) {
                sum = (sum and 0xFFFF) + (sum ushr 16)
            }
            i += 2
        }
        return sum.inv() and 0xFFFF
    }
}

