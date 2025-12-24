package com.ouc.tcp.test;

import java.util.zip.CRC32;

import com.ouc.tcp.message.TCP_HEADER;
import com.ouc.tcp.message.TCP_PACKET;

public class CheckSum {
	
	/*计算TCP报文段校验和：只需校验TCP首部中的seq、ack和sum，以及TCP数据字段*/
	public static short computeChkSum(TCP_PACKET tcpPack) {
		CRC32 crc32 = new CRC32();

		int seq = tcpPack.getTcpH().getTh_seq();
		int ack = tcpPack.getTcpH().getTh_ack();

		crc32.update((seq >>> 24) & 0xFF);
		crc32.update((seq >>> 16) & 0xFF);
		crc32.update((seq >>> 8) & 0xFF);
		crc32.update((seq) & 0xFF);
		crc32.update((ack >>> 24) & 0xFF);
		crc32.update((ack >>> 16) & 0xFF);
		crc32.update((ack >>> 8) & 0xFF);
		crc32.update((ack) & 0xFF);

		int[] tcpData = tcpPack.getTcpS().getData();
		if (tcpData != null) {
			for (int dataInt : tcpData) {
				crc32.update((dataInt >>> 24) & 0xFF);
				crc32.update((dataInt >>> 16) & 0xFF);
				crc32.update((dataInt >>> 8) & 0xFF);
				crc32.update(ack & 0xFF);
			}
		}

		long crcValue = crc32.getValue();

		short checkSum = (short) ((crcValue ^ (crcValue >>> 16)) & 0xFFFF);

		return (short) checkSum;
	}
	
}
