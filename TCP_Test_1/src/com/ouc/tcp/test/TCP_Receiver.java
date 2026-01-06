/***************************2.1: ACK/NACK*****************/
/***** Feng Hong; 2015-12-09******************************/
package com.ouc.tcp.test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;

import com.ouc.tcp.client.TCP_Receiver_ADT;
import com.ouc.tcp.client.UDT_RetransTask;
import com.ouc.tcp.client.UDT_Timer;
import com.ouc.tcp.message.*;
import com.ouc.tcp.tool.TCP_TOOL;

public class TCP_Receiver extends TCP_Receiver_ADT {

	private TCP_PACKET ackPack;	//回复的ACK报文段
	int expectseq = 1;//用于记录当前待接收的包序号，注意包序号不完全是
	private int nowAcked = 0;
	private UDT_RetransTask task;
	private UDT_Timer timer;
	private HashMap<Integer, TCP_PACKET> savedPackets = new HashMap<>();
	private final int N = 8;
	private int base = 1;
	private HashMap<Integer, Boolean> receivedPackets = new HashMap<>();
		
	/*构造函数*/
	public TCP_Receiver() {
		super();	//调用超类构造函数
		super.initTCP_Receiver(this);	//初始化TCP接收端
	}

	@Override
	//接收到数据报：检查校验和，设置回复的ACK报文段
	public void rdt_recv(TCP_PACKET recvPack) {
		//检查校验码，生成ACK
		int recvSeq = recvPack.getTcpH().getTh_seq();
		System.out.println("Recv Seq: " + recvSeq + " Base: " + base + " Expectseq: " + expectseq);
		if(CheckSum.computeChkSum(recvPack) == recvPack.getTcpH().getTh_sum()) {
			//检查序列号在窗口内
			if (recvSeq >= base && recvSeq <= base + N*100) {
				//检查是否重复接收
				if (receivedPackets.containsKey(recvSeq)) {
					System.out.println("Recv packet received");
					return;
				}
				receivedPackets.put(recvSeq, true);
				//按序
				if (recvSeq == expectseq) {
					dataQueue.add(recvPack.getTcpS().getData());
					expectseq+=100;
					nowAcked = recvSeq;
					checkSave();
					sendACK(recvPack);
					System.out.println("Recieved Packet "+(expectseq-100));
				}
				//乱序
				else if (recvSeq > expectseq) {
					savedPackets.put(recvSeq, recvPack);
				}
				//旧包
				else System.out.println("Recieved old Packet "+ recvSeq);
			}
			//不在窗口内
			else  {
				System.out.println("Recieved packet but not received" + recvSeq);
			}
		} else {
			System.out.println("Recieve Computed: "+CheckSum.computeChkSum(recvPack));
			System.out.println("Recieved Packet"+recvPack.getTcpH().getTh_sum());
			System.out.println("Problem: Packet Number: "+recvPack.getTcpH().getTh_seq()+" + InnerSeq:  "+expectseq);
		}
		//交付数据（每20组数据交付一次）
		if(dataQueue.size() == 20)
			deliver_data();
	}

	@Override
	//交付数据（将数据写入文件）；不需要修改
	public void deliver_data() {
		//检查dataQueue，将数据写入文件
		File fw = new File("recvData.txt");
		BufferedWriter writer;
		
		try {
			writer = new BufferedWriter(new FileWriter(fw, true));
			
			//循环检查data队列中是否有新交付数据
			while(!dataQueue.isEmpty()) {
				int[] data = dataQueue.poll();
				
				//将数据写入文件
				for(int i = 0; i < data.length; i++) {
					writer.write(data[i] + "\n");
				}
				
				writer.flush();		//清空输出缓存
			}
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void sendACK(TCP_PACKET recvPack) {
		//创建ACK报文段
		tcpH.setTh_ack(nowAcked);
		tcpS.setData(new int[0]);
		ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
		tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
		reply(ackPack);
	}

	private void checkSave() {
		while(savedPackets.containsKey(expectseq)) {
			TCP_PACKET savedPack = savedPackets.remove(expectseq);
			dataQueue.add(savedPack.getTcpS().getData());
			expectseq+=100;
			nowAcked = expectseq-100;
			System.out.println("More received Packet "+(expectseq-100));
		}
		savedPackets.keySet().removeIf(key -> key < base);
		receivedPackets.keySet().removeIf(key -> key < base);
	}

	@Override
	//回复ACK报文段
	public void reply(TCP_PACKET replyPack) {

		//设置错误控制标志
		tcpH.setTh_eflag((byte)7);	//eFlag=0，信道无错误
				
		//发送数据报
		if (nowAcked >= base + N*100 || timer == null ) {
			client.send(replyPack);
			base = nowAcked + 100;
			if (timer != null) {
				timer.cancel();
			}
			timer=new UDT_Timer();
			task=new UDT_RetransTask(client, replyPack);
			timer.schedule(task,500,500);
		}
	}
}
