/***************************2.1: ACK/NACK
**************************** Feng Hong; 2015-12-09*/

package com.ouc.tcp.test;

import com.ouc.tcp.client.TCP_Sender_ADT;
import com.ouc.tcp.client.UDT_Timer;
import com.ouc.tcp.message.*;

import java.util.*;

public class TCP_Sender extends TCP_Sender_ADT {

	private static final int N = 8;
	private Queue<TCP_PACKET> sentPackets = new LinkedList<>();
	private int base = 1;
	private UDT_Timer timer;
	private TCP_PACKET tcpPack;	//待发送的TCP数据报

	/*构造函数*/
	public TCP_Sender() {
		super();	//调用超类构造函数
		super.initTCP_Sender(this);		//初始化TCP发送端
	}

	@Override
	//可靠发送（应用层调用）：封装应用层数据，产生TCP数据报；需要修改
	public void rdt_send(int dataIndex, int[] appData) {
		while(sentPackets.size() >= N) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
		//生成TCP数据报（设置序号和数据字段/校验和),注意打包的顺序
		tcpH.setTh_seq(dataIndex * appData.length + 1);//包序号设置为字节流号：
		tcpS.setData(appData);
		tcpPack = new TCP_PACKET(tcpH, tcpS, destinAddr);
		//更新带有checksum的TCP 报文头
		tcpH.setTh_sum(CheckSum.computeChkSum(tcpPack));
		tcpPack.setTcpH(tcpH);

		TCP_PACKET packCopy = createPackCopy(tcpPack);
		sentPackets.add(packCopy);
		System.out.println("Packets have been sent in Queue: "+sentPackets.peek().getTcpH().getTh_seq());

		//发送TCP数据报
		udt_send(tcpPack);
		if (sentPackets.size() == 1) startTimer();
		System.out.println("Have sent "+ tcpPack.getTcpH().getTh_seq());
	}

	private TCP_PACKET createPackCopy(TCP_PACKET original) {
		// 复制TCP头
		TCP_HEADER originalHeader = original.getTcpH();
		TCP_HEADER copiedHeader = new TCP_HEADER();
		copiedHeader.setTh_seq(originalHeader.getTh_seq());
		copiedHeader.setTh_ack(originalHeader.getTh_ack());
		copiedHeader.setTh_eflag(originalHeader.getTh_eflag());
		copiedHeader.setTh_sum(originalHeader.getTh_sum());

		// 复制TCP段
		TCP_SEGMENT originalSegment = original.getTcpS();
		TCP_SEGMENT copiedSegment = new TCP_SEGMENT();
		int[] originalData = originalSegment.getData();
		if (originalData != null) {
			int[] copiedData = new int[originalData.length];
			System.arraycopy(originalData, 0, copiedData, 0, originalData.length);
			copiedSegment.setData(copiedData);
		}

		// 创建新的TCP包
		return new TCP_PACKET(copiedHeader, copiedSegment, original.getSourceAddr());
	}

	@Override
	//不可靠发送：将打包好的TCP数据报通过不可靠传输信道发送；仅需修改错误标志
	public void udt_send(TCP_PACKET stcpPack) {
		//设置错误控制标志
		tcpH.setTh_eflag((byte)4);
		//System.out.println("to send: "+stcpPack.getTcpH().getTh_seq());
		//发送数据报
		client.send(stcpPack);
	}

	@Override
	//需要修改
	public void waitACK() {
		//循环检查ackQueue
		//循环检查确认号对列中是否有新收到的ACK
		while(!ackQueue.isEmpty()){
			int currentAck=ackQueue.poll();
			System.out.println("CurrentAck: "+currentAck);
			if (currentAck >= base) {
				for (TCP_PACKET tcpPack : sentPackets) {
					System.out.println("Packets in Queue: "+tcpPack.getTcpH().getTh_seq());
				}
				while (!sentPackets.isEmpty() && sentPackets.peek().getTcpH().getTh_seq() <= currentAck) {
					sentPackets.poll();
					System.out.println("SentPacket: "+sentPackets.size());
				}
				base = currentAck + 100;
				if (sentPackets.isEmpty()) stopTimer();
				else startTimer();
			}else {
				System.out.println("base: "+base);
				System.out.println("Duplicate ACK for "+ currentAck);
			}
		}
	}

	@Override
	//接收到ACK报文：检查校验和，将确认号插入ack队列;NACK的确认号为－1；不需要修改
	public void recv(TCP_PACKET recvPack) {
		System.out.println("Receive ACK Number： "+ recvPack.getTcpH().getTh_ack());
		ackQueue.add(recvPack.getTcpH().getTh_ack());
	    System.out.println();

	    //处理ACK报文
	    waitACK();
	}

	private void startTimer() {
		stopTimer();
		timer = new UDT_Timer();
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				System.out.println("Timeout! Retransmitting packets " );
				for (TCP_PACKET packet : sentPackets) {
					udt_send(packet);
				}
				startTimer();
			}
		};
		timer.schedule(task, 1000, 1000);
		System.out.println("Timer started");
	}

	private void stopTimer() {
		if (timer != null) {
			timer.cancel();
			timer = null;
		}
		System.out.println("Timer cancelled");
	}
}
