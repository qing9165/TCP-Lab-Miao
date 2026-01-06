/***************************2.1: ACK/NACK*****************/
/***** Feng Hong; 2015-12-09******************************/
package com.ouc.tcp.test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import com.ouc.tcp.client.TCP_Receiver_ADT;
import com.ouc.tcp.client.UDT_RetransTask;
import com.ouc.tcp.client.UDT_Timer;
import com.ouc.tcp.message.*;
import com.ouc.tcp.tool.TCP_TOOL;

public class TCP_Receiver extends TCP_Receiver_ADT {
	
	private TCP_PACKET ackPack;	//回复的ACK报文段
	int expectseq = 1;//用于记录当前待接收的包序号，注意包序号不完全是
	private UDT_RetransTask task;
	private UDT_Timer timer;
	private long lastAckTime = 0;
		
	/*构造函数*/
	public TCP_Receiver() {
		super();	//调用超类构造函数
		super.initTCP_Receiver(this);	//初始化TCP接收端
	}

	@Override
	//接收到数据报：检查校验和，设置回复的ACK报文段
	public void rdt_recv(TCP_PACKET recvPack) {
		//更新最后一次收到ACK的时间
		lastAckTime = System.currentTimeMillis();
		//检查校验码，生成ACK
		if(CheckSum.computeChkSum(recvPack) == recvPack.getTcpH().getTh_sum()) {
			//检查序列号
			int recvSeq = recvPack.getTcpH().getTh_seq();
			if (recvSeq == expectseq) {
				dataQueue.add(recvPack.getTcpS().getData());
				expectseq+=100;
				tcpH.setTh_ack(expectseq);
				ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
				tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
				reply(ackPack);
				System.out.println("Recieved Packet "+(expectseq-100));
			}
			else  {
				System.out.println("Recieved "+recvSeq+" but expected "+expectseq);
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

	@Override
	//回复ACK报文段
	public void reply(TCP_PACKET replyPack) {

		//设置错误控制标志
		tcpH.setTh_eflag((byte)4);	//eFlag=0，信道无错误
				
		//发送数据报
		if (System.currentTimeMillis() - lastAckTime > 5000) {
			if (!dataQueue.isEmpty()) {
				deliver_data();
			}
			System.exit(0);
		}
		if (timer != null) {
			timer.cancel();
		}
		timer=new UDT_Timer();
		task=new UDT_RetransTask(client, replyPack);
		timer.schedule(task,500,500);
	}
	
}
