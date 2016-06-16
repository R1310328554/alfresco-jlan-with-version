package org.alfresco.jlan.server.filesys.db.mysql;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;

import org.alfresco.jlan.app.CifsOnlyXMLServerConfiguration;
import org.alfresco.jlan.server.config.InvalidConfigurationException;
import org.alfresco.jlan.server.core.DeviceContextException;
import org.alfresco.jlan.server.filesys.AccessDeniedException;
import org.alfresco.jlan.server.filesys.DiskInterface;
import org.alfresco.jlan.server.filesys.FileExistsException;
import org.alfresco.jlan.server.filesys.FileOpenParams;
import org.alfresco.jlan.server.filesys.db.DBDeviceContext;
import org.alfresco.jlan.server.filesys.db.DBException;
import org.alfresco.jlan.server.filesys.loader.FileSegment;
import org.alfresco.jlan.smb.server.DefaultSrvSessionFactory;
import org.springframework.extensions.config.ConfigElement;

import com.fileSystem.FilesMappingForDB;
import com.util.FileUtil;


public class CopyOfMySQLDBLInterface implements Runnable{

	private static MySQLDBLInterface dbl;

	public static void mainx(String[] args) {
		
		
		System.out.println((93 & 2));
		
	}
	
	public static void main(String[] args) {
		
		
//		CifsOnlyXMLServerConfiguration config = new CifsOnlyXMLServerConfiguration();
//		String fname = FileUtil.getRootPath()+"/WEB-INF/classes/jlanConfig.xml";
//		try {
//			config.loadConfiguration(fname);
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (InvalidConfigurationException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		
//		FilesMappingForDB fm = (FilesMappingForDB) config.getShareMapper();
//		dbl = (MySQLDBLInterface) fm.getDiskInterface();
		
		
		ConfigElement configElement = FilesMappingForDB.getUSConfigElement();
		dbl = new MySQLDBLInterface();
		ConfigElement cparams = new ConfigElement("xx", "yy");
		DBDeviceContext dbCtx = null;
		try {
			dbCtx = new DBDeviceContext(configElement);
		} catch (DeviceContextException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			dbl.initializeDatabase(dbCtx, cparams);
		} catch (InvalidConfigurationException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		//[\资料库\bbbbb\11~664DE.tmp,Create,acc=0x2019f,
		//attr=0x80,alloc=0,share=Exclusive,pid=17168,copt=0x40,seclev=2,
		//secflg=0x3,mode=0644,BatchOpLck,ExOpLck,ExtResp]

		long timeMillis1 = System.currentTimeMillis();
		for (int i = 0; i < 207; i++) {
			CopyOfMySQLDBLInterface c = new CopyOfMySQLDBLInterface();
			Thread t = new Thread(c);
			t.setName("ThreadaCa1" + i);
//			t.setName(29253 + i + "");//30170
			t.start();
		}
		
		long timeMillis2 = System.currentTimeMillis();
		
		//循环10000次 Time eclipsed :  69819
		System.out.println();
		System.out.println();
		System.out.println();
		System.out.println("Total Time Eclipsed :  " + (timeMillis2 - timeMillis1));
	}
	

	private static void createFileTest() {
		
	}
	
	public void run() {
		createFile(dbl);
//		deleteFile(dbl);
	}

	/**
	 * //循环1000次 Time eclipsed :  24931
	 * //循环1001次 Time eclipsed :  25731
	 * @param dbl
	 */
	private static void createFile(MySQLDBLInterface dbl) {
		long timeMillis1 = System.currentTimeMillis();
		int pid = 17168;
		int secFlags = 3;
		int secLevel = 2;
		int rootFID = 0;
		int createOption = 64;//Create
		long allocSize = 0;
		int sharedAccess = 0;
		int attr = 128;
		int accessMode = 131487;
		int openAction = 2;
		String path = "\\资料库\\aaaaaaa\\test\\b_"+Thread.currentThread().getName()+".doc";
		int fileAttr = 0;//0x80
		FileOpenParams params = new FileOpenParams(path, openAction, accessMode, fileAttr, 
				sharedAccess, allocSize, createOption, rootFID, secLevel, secFlags, pid);
		
		String ipAddress = "192.168.4.123";
		String shareName = "资料库";
		String userName = "admin";
		boolean retain = true;
		String fname =  Thread.currentThread().getName()+".doc";
		int dirId = 26196;
		
		
		try {
			dbl.createFileRecord(fname, dirId, params, retain, userName, shareName, ipAddress);
		} catch (FileExistsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (AccessDeniedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (DBException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
			

		long timeMillis2 = System.currentTimeMillis();
		
		//
		// Time eclipsed :  69819
//		System.out.println("Time eclipsed :  " + (timeMillis2 - timeMillis1));
	}
	
	private static void saveFile(MySQLDBLInterface dbl) throws SQLException, IOException {
		long timeMillis1 = System.currentTimeMillis();
		
		String shareName = "资料库";
		String fidStr =  Thread.currentThread().getName();
		int fid = Integer.parseInt(fidStr);
		try {
			FileSegment fileSeg = null;
			File uFile = null;
			String tempDir = null;
			String rootPath_bak = null;
			dbl.saveFileArchive(rootPath_bak, tempDir, fid, uFile, shareName, fileSeg);
		} catch (AccessDeniedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (DBException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		long timeMillis2 = System.currentTimeMillis();
		//
		// Time eclipsed :  69819
		System.out.println("Time eclipsed :  " + (timeMillis2 - timeMillis1));
	}
	
	/**
	 * 900 次
	 * Time eclipsed :  264065
	 * 
	 * @param dbl
	 */
	private static void deleteFile(MySQLDBLInterface dbl) {
		long timeMillis1 = System.currentTimeMillis();
		
		String ipAddress = "192.168.4.123";
		String shareName = "资料库";
		String userName = "admin";
		String fidStr =  Thread.currentThread().getName();
		int dirId = 26196;
		
		boolean markOnly = false;
		int fid = Integer.parseInt(fidStr);
		
		try {
			dbl.deleteFileRecord(dirId, fid, markOnly, userName, shareName, ipAddress);
		} catch (AccessDeniedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (DBException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		long timeMillis2 = System.currentTimeMillis();
		
		//
		// Time eclipsed :  69819
		System.out.println("Time eclipsed :  " + (timeMillis2 - timeMillis1));
	}
	
	private static void renameFile(MySQLDBLInterface dbl) throws FileNotFoundException {
		long timeMillis1 = System.currentTimeMillis();
		
		String ipAddress = "192.168.4.123";
		String shareName = "资料库";
		String userName = "admin";
		String fidStr =  Thread.currentThread().getName();
		int dirId = 26196;
		
		boolean markOnly = false;
		int fid = Integer.parseInt(fidStr);
		
		try {
			int newDir = 0;
			String newName = null;
			dbl.renameFileRecord(dirId, fid, newName, newDir, shareName);
		} catch (DBException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		long timeMillis2 = System.currentTimeMillis();
		
		//
		// Time eclipsed :  69819
		System.out.println("Time eclipsed :  " + (timeMillis2 - timeMillis1));
	}
	
}