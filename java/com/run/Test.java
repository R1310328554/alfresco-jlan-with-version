package com.run;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import org.alfresco.jlan.smb.PacketType;
import org.alfresco.jlan.smb.SMBStatus;
import org.alfresco.jlan.smb.server.DefaultSrvSessionFactory;
import org.alfresco.jlan.smb.server.SMBSrvPacket;
import org.alfresco.jlan.smb.server.SMBSrvSession;

public class Test {

	/**
	 * @param args
	 * @throws IOException 
	 */
	/**-verbose:gc -XX:+PrintGCDetails -XX:-PrintGCTimeStamps -Xloggc:/usr/linkapp/bin/tomcat-nas/gclog/gc.log
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub

		DefaultSrvSessionFactory sessFactory = new DefaultSrvSessionFactory();
//		sessFactory.createSession(handler, server, sessId);
		
		SMBSrvPacket smbPkt = new SMBSrvPacket();
		smbPkt.setAndXCommand(PacketType.SessionSetupAndX);
		smbPkt.setAndXParameterCount(0, 1234);
//		sp.
		
		Socket so = new Socket("192.168.4.23",445);
		OutputStream outputStream = so.getOutputStream();
		InputStream inputStream = so.getInputStream();
		outputStream.write(smbPkt.getBuffer());
		
		int b = 0;
		while((b=inputStream.read())!=-1) {
			System.out.println((char)b);
		}
		
		
		//打开文件
		
		smbPkt.setParameterCount(12);
		smbPkt.setParameter(0, 0xFF); // No chained response
		smbPkt.setParameter(1, 0); // Offset to chained response

		smbPkt.setParameter(2, SMBSrvSession.DefaultBufferSize);
		smbPkt.setParameter(3, SMBSrvSession.NTMaxMultiplexed);
//		smbPkt.setParameter(4, vcNum); // virtual circuit number
//		smbPkt.setParameterLong(5, 0); // session key
//		smbPkt.setParameter(7, respLen);
//		// security blob length
//		smbPkt.setParameterLong(8, 0); // reserved
//		smbPkt.setParameterLong(10, getServerCapabilities());

		// Extract the filename string

		String fileName = smbPkt.unpackString(smbPkt.isUnicode());
		if ( fileName == null) {
			//m_sess.sendErrorResponseSMB( smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
			//return;
		}
		
	}
	
	private void responseSMB() {
		// TODO Auto-generated method stub

		SMBSrvPacket smbPkt = new SMBSrvPacket();
		smbPkt.setAndXCommand(PacketType.SessionSetupAndX);
		smbPkt.setAndXParameterCount(0, 1234);

		int flags = smbPkt.getParameter(2);
		int access = smbPkt.getParameter(3);
		int srchAttr = smbPkt.getParameter(4);
		int fileAttr = smbPkt.getParameter(5);
		int crTime = smbPkt.getParameter(6);
		int crDate = smbPkt.getParameter(7);
		int openFunc = smbPkt.getParameter(8);
		int allocSiz = smbPkt.getParameterLong(9);
	}

}
