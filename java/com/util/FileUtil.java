
package com.util;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class FileUtil 
{
    public static String getExtention(String fileName)
    {
        int pos = fileName.lastIndexOf(".");
        if(pos > 0)
        {
        	return fileName.substring(pos).toLowerCase();
        }
        return "";
   }
    
    public static String getRandName()
    {
    	Random rd1 = new Random(100);
    	String randName = ""+System.currentTimeMillis()+rd1.nextInt(10);
    	return randName;
    }
    
    /**
     * 上传保存文件
     * @param srcFile 源文件
     * @param storePath 存储文件路径
     */
    public static void upload(File srcFile,String storePath)
    {
    	InputStream in = null ;
        OutputStream out = null ;
    	try
    	{
    		in = new BufferedInputStream( new FileInputStream(srcFile));
    		String folderPath = storePath.substring(0, storePath.lastIndexOf("/"));
    		File folder = new File(folderPath);
    		if(!folder.exists())
    		{
    			folder.mkdir();
    		}
    		out = new BufferedOutputStream( new FileOutputStream(storePath));
    		byte [] buffer = new byte [in.available()];
    		while (in.read(buffer) > 0 ) 
    		{
    			 out.write(buffer);
    		}
    		out.flush();
    	}
    	catch(Exception e)
    	{
    		e.printStackTrace();
    	}
    	finally  
        { 
    		try{
    		if(null != in) in.close();
    		if(null != out) out.close();
    		}catch(Exception e){
    			e.printStackTrace();
      	   	}
        }
    }
    /**
     * 获得classes路径
     * @return
     */
    public static String getClassesPath() {
    	URL url = FileUtil.class.getResource("/");
    	return url.getPath();
	}
    /**
     * 获得Root路径
     * @return
     */
    public static String getRootPath()
    {
    	URL url = FileUtil.class.getResource("/");
    	String root = new File(url.getPath()).getParentFile().getParent();
    	return root;
    } 
    
    public static Map<String,Long> getDirSpace(File file)
	 {
		 long totalSize = 0;
		 long totalFile = 0;
		 long totalFolder = 0;
		//断定文件是否存在
		 if(file.exists())
		 {
			 if(file.isDirectory())
			 {
				//若是是目次则递归策画其内容的总大小
				 File[] children = file.listFiles();			 
				 for(File f:children)
				 {
					 Map<String,Long> sub = getDirSpace(f);
					 long subSize = Long.parseLong(sub.get("totalSize").toString());
					 long subFiles  = Integer.parseInt(sub.get("totalFile").toString());
					 long subFolders = Integer.parseInt(sub.get("totalFolder").toString());
					 totalSize +=subSize;
					 totalFile +=subFiles;
					 totalFolder +=subFolders;
				 }
				 totalFolder +=1;
			 }
			 else
			 {
				 totalSize = file.length();
				 totalFile = 1;
			 }
		 }
		 Map<String,Long> map = new HashMap<String,Long>();
		 map.put("totalSize", totalSize);
		 map.put("totalFile", totalFile);
		 map.put("totalFolder", totalFolder);
		 return map;
	 }
    
    
    
    /**
     * 取得文件夹大小
     * @param f
     * @return
     * @throws Exception
     */
    public static long getFileSize(File f)
    {
        long size = 0;
        if(null != f){
        	File flist[] = f.listFiles();
        	if(null != flist && flist.length>0){
        		for (int i = 0; i < flist.length; i++)
        		{
        			if (flist[i].isDirectory())
        			{
        				size = size + getFileSize(flist[i]);
        			} else
        			{
        				size = size + flist[i].length();
        			}
        		}
        	}
        }
        return size;
    }
    
    
    
    /**
     * 获取单个文件大小
     * @param path
     * @return
     */
    public static long getFileSize(String path){
    	long fsize = 0L;
    	if(null != path && !"".equals(path))
    	{
    		File file = new File(path);
    		if(null != file && file.exists()){
    			fsize = file.length();
    		}
    	}
    	return fsize;
    }
    
    
    
    /**
     * 读取查询记录结果文件
     * @param resultPath 查找结果文件路径
     * @return 
     */
    public static List<String> getFindResults(String resultPath){
    	List<String> pathList = new ArrayList<String>();
    	if(!"".equals(resultPath)){
    		InputStream is;
			try {
				is = new FileInputStream(resultPath);
				BufferedReader br = new BufferedReader(new InputStreamReader(is));  
				String str = "";  
				while ((str = br.readLine()) != null) {  
					String stro = str;
					String [] sp = str.replaceAll("[\\s]+",",").split(",");
					if(null != sp && sp.length==9){
						str = sp[8];
						str = stro.substring(stro.indexOf(str), stro.length());
						if(null != str && !"".equals(str)){
							pathList.add(str);
//							System.out.println(str);
						}
					}else if(null != sp && sp.length==1){
						str = sp[0];
						str = stro.substring(stro.indexOf(str), stro.length());
						if(null != str && !"".equals(str)){
							pathList.add(str);
//							System.out.println(str);
						}
					}
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
    	}
    	return pathList;
    }
    
    
    /**
     * 创建一个文件
     * @param filepath
     * @param fileinfo
     * @return
     */
    public static boolean createFile(String filepath,Map<String,String> options){
    	boolean falgs = false;
    	File file = new File(filepath);
    	BufferedWriter output;
    	try {
			File fdir = new File(file.getParent());
			if(!fdir.exists()){
				fdir.mkdirs();
			}
			output = new BufferedWriter(new FileWriter(file));
			if(null != options){
				Set<Map.Entry<String, String>> entrySet = options.entrySet();
				output.write(".SPARSE.LFS====================\n");
				for(Map.Entry<String, String> entry : entrySet){
					output.write(entry.getKey()+"="+entry.getValue()+"&");
				}
			}
			output.close();
			falgs = true;
		}catch (IOException e) {
			e.printStackTrace();
			falgs = false;
		}
    	return falgs;
    }
    
    
    /**
     * 读取文件的从be开始的count个字节
     * @param filepath 文件路径
     * @param be 从开始
     * @param count 多少个
     */
    public static String readBytes(String filepath, int be, int count){
    	String str = "";
    	try {
    		File file = new File(filepath);
    		if(file.exists()){
    			RandomAccessFile af = new RandomAccessFile(file,"r");
    			af.seek(be);
    			byte b[] = new byte[count];
    			af.read(b,be,count);
    			str = new String(b,be,count);
    		}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return str;
    }
    
    /**
     * 读取文件的某行
     * @param filepath
     * @param row
     * @return
     */
    public static String readByRow(String filepath, int row){
    	String str = "";
    	File file = new File(filepath);
    	try {
    		if(file.exists()){
				BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
    	        int i = 0;  
    	        while ((str = br.readLine()) != null) {  
    	          i++; 
	        	  if(i == row){
	        		  break;
	        	  }
    	       }
    		}
		}catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
    	return str;
    }
    
    public static String converPath(String path){
    	String newpath = path;
    	if(null != path && !"".equals(path)){
    		newpath = newpath.replaceAll("\\\\", "/");
    		newpath = newpath.replaceAll("//", "/");
    		if(!newpath.startsWith("/")){
    			newpath = "/"+newpath;
    		}
    	}
    	return newpath;
    }
    
    public static boolean checkPath(String path){
    	boolean falgs = false;
    	if(null != path && !"".equals(path)){
    		if(path.contains("/.") || path.endsWith(".ini")){
    			falgs = false;
    		}else{
    			falgs = true;
    		}
    	}
    	return falgs;
    }
    public static boolean createFile2(String filepath,String str){
    	boolean falgs = false;
    	File file = new File(filepath);
    	BufferedWriter output;
    	try {
			File fdir = new File(file.getParent());
			if(!fdir.exists()){
				fdir.mkdirs();
			}
			output = new BufferedWriter(new FileWriter(file));
//				output.write("<?xml version=\"1.0\" standalone=\"no\"?> <!-- <!DOCTYPE jlanserver SYSTEM \"jlanserver.dtd\"> --> <jlanserver><servers><SMB/><noFTP/><noNFS/></servers><SMB><host name=\"JLANSRV\" domain=\"ALFRESCO\"><broadcast>192.168.4.255</broadcast><smbdialects>LanMan,NT</smbdialects><comment>Alfresco JLAN Server</comment><Win32NetBIOS/><Win32Announce interval=\"5\"/><!-- Requires running the server using the root account --><netBIOSSMB platforms=\"linux,macosx,solaris\"/><tcpipSMB platforms=\"linux,macosx,solaris\"/><!-- To run the server using a non-root account on linux, Mac OS X, Solaris --><!--<netBIOSSMB sessionPort=\"1139\" namingPort=\"1137\" datagramPort=\"1138\" platforms=\"linux,macosx,solaris\"/><tcpipSMB port=\"1445\" platforms=\"linux,macosx,solaris\"/>--><hostAnnounce interval=\"5\"/></host><sessionDebug flags=\"Negotiate,Socket,Tree\"/><!-- <netbiosDebug/> --><!-- <announceDebug/> --><authenticator type=\"enterprise\"><mode>USER</mode><NOallowGuest/><Debug/></authenticator></SMB><FTP><port>21</port><allowAnonymous/><debug flags=\"File,Search,Error,DataPort,Directory\"/><!-- Enable FTPS --><!--	  <keyStore>PATH-TO-KEYSTORE-FILE</keyStore><keyStoreType>JKS</keyStoreType><keyStorePassphrase>KEYSTORE-PASSWORD</keyStorePassphrase><trustStore>PATH-TO-TRUSTSTORE-FILE</trustStore><trustStoreType>JKS</trustStoreType><trustStorePassphrase>TRUSTSTORE-PASSWORD</trustStorePassphrase><NOrequireSecureSession/><NOsslEngineDebug/>--></FTP><NFS><enablePortMapper/><debug flags=\"File,FileIO\"/></NFS><debug><output><class>org.alfresco.jlan.debug.ConsoleDebug</class><logFile>jlansrv.log</logFile><append/></output></debug><shares><diskshare name=\"JLAN\" comment=\"Test share\"><driver><class>org.alfresco.jlan.smb.server.disk.JavaFileDiskDriver</class><LocalPath>.</LocalPath></driver></diskshare></shares>		<security><JCEProvider>cryptix.jce.provider.CryptixCrypto</JCEProvider><authenticator><class>org.alfresco.jlan.server.auth.LocalAuthenticator</class><mode>USER</mode><allowGuest/></authenticator><users><user name=\"jlansrv\"><password>jlan</password><comment>System administrator</comment><administrator/></user><user name=\"normal\"><password>normal</password></user></users></security></jlanserver>");
//			System.out.println("<?xml version=\"1.0\" standalone=\"no\"?><!DOCTYPE jlanserver SYSTEM \"config/jlanserver.dtd\"><jlanserver><servers><SMB /><CIFS/><noFTP /><noNFS /></servers><SMB><host name=\"CifsShare_local\" domain=\"jlanserv\"><broadcast>192.168.4.255</broadcast><!-- <bindto>192.168.4.245</bindto><broadcast>10.0.0.255</broadcast>  --><smbdialects>Core,LanMan,NT</smbdialects><netBIOSSMB /><!--  WINS><primary>192.168.4.245</primary></WINS --><comment>JLAN Server</comment><!--<Win32NetBIOS lana=\"6\"/>--><Win32NetBIOS /><Win32Announce interval=\"5\" /><!--  root 用户 设置  --><netBIOSSMB platforms=\"linux,macosx,solaris\" /><tcpipSMB platforms=\"linux,macosx,solaris\" /><!--  非 root 用户    设置   on linux, Mac OS X, Solaris --><!--<netBIOSSMB sessionPort=\"1139\" namePort=\"1137\" datagramPort=\"1138\"platforms=\"linux,macosx,solaris\"/> <tcpipSMB port=\"1445\"platforms=\"linux,macosx,solaris\"/>--><hostAnnounce interval=\"5\" /></host><sessionDebug flags=\"Negotiate,Socket,Tree\" /><!--  netbiosDebug / --><announceDebug /><authenticator type=\"enterprise\"><mode>USER</mode><NOallowGuest/><Debug /></authenticator></SMB><FTP><port>21</port><allowAnonymous/><rootDirectory>/tmp/jlantmp/ldr0</rootDirectory><debug flags=\"File,Search,Error,DataPort,Directory\" /></FTP><NFS><enablePortMapper /><debug flags=\"File,FileIO\" /></NFS><debug><output><class>org.alfresco.jlan.debug.ConsoleDebug</class><logFile>jlansrv.log</logFile><append /></output></debug> <shares>  "+str+" <diskshare name=\"我收到的共享文件\" comment=\"JDBC virtual filesystem using BLOB\"><driver><class>org.alfresco.jlan.server.filesys.db.DBDiskDriver</class><CacheTime>300</CacheTime><Debug/><DatabaseInterface><class>org.alfresco.jlan.server.filesys.db.mysql.MySQLDBInterface</class><DSN>jdbc:mysql://localhost/data_linkapp_lss2</DSN><Username>root</Username><Password>mysql2012</Password><ConnectionPool>10:20</ConnectionPool><FileSystemTable>jweb_sharefilecache</FileSystemTable><StreamsTable>jweb_sharefilerevision</StreamsTable><Debug/><SQLDebug/></DatabaseInterface><FileLoader><class>org.alfresco.jlan.server.filesys.db.DBFileLoader</class><ThreadPoolSize>6:2</ThreadPoolSize><TempDirectory>/usr</TempDirectory><MaximumFilesPerDirectory>1000</MaximumFilesPerDirectory><SmallFileSize>100K</SmallFileSize><FilesPerJar>500</FilesPerJar><SizePerJar>1000K</SizePerJar><JarCompressionLevel>9</JarCompressionLevel><Debug/><noThreadDebug/></FileLoader></driver></diskshare></shares><security><model>enterprise</model><JCEProvider>cryptix.jce.provider.CryptixCrypto</JCEProvider><authenticator><class>org.alfresco.jlan.server.auth.passthru.PassthruAuthenticator </class><mode>USER</mode><allowGuest/></authenticator></security><shareMapper><class>org.alfresco.jlan.sample.HomeShareMapper</class><debug/></shareMapper></jlanserver> ");
			output.write("<?xml version=\"1.0\" standalone=\"no\"?> <!-- <!DOCTYPE jlanserver SYSTEM \"config/jlanserver.dtd\"> --> <jlanserver><servers><SMB /><CIFS/><noFTP /><noNFS /></servers><SMB><host name=\"CifsShare_local\" domain=\"jlanserv\"><broadcast>192.168.4.255</broadcast><!-- <bindto>192.168.4.245</bindto><broadcast>10.0.0.255</broadcast>  --><smbdialects>Core,LanMan,NT</smbdialects><netBIOSSMB /><!--  WINS><primary>192.168.4.245</primary></WINS --><comment>JLAN Server</comment><!--<Win32NetBIOS lana=\"6\"/>--><Win32NetBIOS /><Win32Announce interval=\"5\" /><!--  root 用户 设置  --><netBIOSSMB platforms=\"linux,macosx,solaris\" /><tcpipSMB platforms=\"linux,macosx,solaris\" /><!--  非 root 用户    设置   on linux, Mac OS X, Solaris --><!--<netBIOSSMB sessionPort=\"1139\" namePort=\"1137\" datagramPort=\"1138\"platforms=\"linux,macosx,solaris\"/> <tcpipSMB port=\"1445\"platforms=\"linux,macosx,solaris\"/>--><hostAnnounce interval=\"5\" /></host><sessionDebug flags=\"Negotiate,Socket,Tree\" /><!--  netbiosDebug / --><announceDebug /><authenticator type=\"enterprise\"><mode>USER</mode><NOallowGuest/><Debug /></authenticator></SMB><FTP><port>21</port><allowAnonymous/><rootDirectory>/tmp/jlantmp/ldr0</rootDirectory><debug flags=\"File,Search,Error,DataPort,Directory\" /></FTP><NFS><enablePortMapper /><debug flags=\"File,FileIO\" /></NFS><debug><output><class>org.alfresco.jlan.debug.ConsoleDebug</class><logFile>jlansrv.log</logFile><append /></output></debug> <shares>  "+str+" <diskshare name=\"我收到的共享文件\" comment=\"JDBC virtual filesystem using BLOB\"><driver><class>org.alfresco.jlan.server.filesys.db.DBDiskDriver</class><CacheTime>300</CacheTime><Debug/><DatabaseInterface><class>org.alfresco.jlan.server.filesys.db.mysql.MySQLDBInterface</class><DSN>jdbc:mysql://localhost/data_linkapp_lss2</DSN><Username>root</Username><Password>mysql2012</Password><ConnectionPool>10:20</ConnectionPool><FileSystemTable>jweb_sharefilecache</FileSystemTable><StreamsTable>jweb_sharefilerevision</StreamsTable><Debug/><SQLDebug/></DatabaseInterface><FileLoader><class>org.alfresco.jlan.server.filesys.db.DBFileLoader</class><ThreadPoolSize>6:2</ThreadPoolSize><TempDirectory>/usr</TempDirectory><MaximumFilesPerDirectory>1000</MaximumFilesPerDirectory><SmallFileSize>100K</SmallFileSize><FilesPerJar>500</FilesPerJar><SizePerJar>1000K</SizePerJar><JarCompressionLevel>9</JarCompressionLevel><Debug/><noThreadDebug/></FileLoader></driver></diskshare></shares><security><model>enterprise</model><JCEProvider>cryptix.jce.provider.CryptixCrypto</JCEProvider><authenticator><class>org.alfresco.jlan.server.auth.passthru.PassthruAuthenticator </class><mode>USER</mode><allowGuest/></authenticator></security><shareMapper><class>org.alfresco.jlan.sample.HomeShareMapper</class><debug/></shareMapper></jlanserver> ");
//<?xml version="1.0" standalone="no"?><!DOCTYPE jlanserver SYSTEM "config/jlanserver.dtd"><jlanserver><servers><SMB /><CIFS/><noFTP /><noNFS /></servers><SMB><host name="CifsShare_local" domain="jlanserv"><broadcast>192.168.4.255</broadcast><!-- <bindto>192.168.4.245</bindto><broadcast>10.0.0.255</broadcast>  --><smbdialects>Core,LanMan,NT</smbdialects><netBIOSSMB /><!--  WINS><primary>192.168.4.245</primary></WINS --><comment>JLAN Server</comment><!--<Win32NetBIOS lana="6"/>--><Win32NetBIOS /><Win32Announce interval="5" /><!--  root 用户 设置  --><netBIOSSMB platforms="linux,macosx,solaris" /><tcpipSMB platforms="linux,macosx,solaris" /><!--  非 root 用户    设置   on linux, Mac OS X, Solaris --><!--<netBIOSSMB sessionPort="1139" namePort="1137" datagramPort="1138"platforms="linux,macosx,solaris"/> <tcpipSMB port="1445"platforms="linux,macosx,solaris"/>--><hostAnnounce interval="5" /></host><sessionDebug flags="Negotiate,Socket,Tree" /><!--  netbiosDebug / --><announceDebug /><authenticator type="enterprise"><mode>USER</mode><NOallowGuest/><Debug /></authenticator></SMB><FTP><port>21</port><allowAnonymous/><rootDirectory>/tmp/jlantmp/ldr0</rootDirectory><debug flags="File,Search,Error,DataPort,Directory" /></FTP><NFS><enablePortMapper /><debug flags="File,FileIO" /></NFS><debug><output><class>org.alfresco.jlan.debug.ConsoleDebug</class><logFile>jlansrv.log</logFile><append /></output></debug> <shares><diskshare name="MyFile" comment="JDBC virtual filesystem using BLOB"><driver><class>org.alfresco.jlan.server.filesys.db.DBDiskDriver</class><CacheTime>300</CacheTime><Debug/><DatabaseInterface><class>org.alfresco.jlan.server.filesys.db.mysql.MySQLDBInterface</class><DSN>jdbc:mysql://localhost/data_linkapp_lss2</DSN><Username>root</Username><Password>mysql2012</Password><ConnectionPool>10:20</ConnectionPool><FileSystemTable>jweb_filecache</FileSystemTable><StreamsTable>jweb_filerevision</StreamsTable><Debug/><SQLDebug/></DatabaseInterface><FileLoader><class>org.alfresco.jlan.server.filesys.db.DBFileLoader</class><ThreadPoolSize>6:2</ThreadPoolSize><TempDirectory>/usr</TempDirectory><MaximumFilesPerDirectory>1000</MaximumFilesPerDirectory><SmallFileSize>100K</SmallFileSize><FilesPerJar>500</FilesPerJar><SizePerJar>1000K</SizePerJar><JarCompressionLevel>9</JarCompressionLevel><Debug/><noThreadDebug/></FileLoader></driver></diskshare><diskshare name="ShareFile" comment="JDBC virtual filesystem using BLOB"><driver><class>org.alfresco.jlan.server.filesys.db.DBDiskDriver</class><CacheTime>300</CacheTime><Debug/><DatabaseInterface><class>org.alfresco.jlan.server.filesys.db.mysql.MySQLDBInterface</class><DSN>jdbc:mysql://localhost/data_linkapp_lss2</DSN><Username>root</Username><Password>mysql2012</Password><ConnectionPool>10:20</ConnectionPool><FileSystemTable>jweb_sharefilecache</FileSystemTable><StreamsTable>jweb_sharefilerevision</StreamsTable><Debug/><SQLDebug/></DatabaseInterface><FileLoader><class>org.alfresco.jlan.server.filesys.db.DBFileLoader</class><ThreadPoolSize>6:2</ThreadPoolSize><TempDirectory>/usr</TempDirectory><MaximumFilesPerDirectory>1000</MaximumFilesPerDirectory><SmallFileSize>100K</SmallFileSize><FilesPerJar>500</FilesPerJar><SizePerJar>1000K</SizePerJar><JarCompressionLevel>9</JarCompressionLevel><Debug/><noThreadDebug/></FileLoader></driver></diskshare></shares><security><model>enterprise</model><JCEProvider>cryptix.jce.provider.CryptixCrypto</JCEProvider><authenticator><class>org.alfresco.jlan.server.auth.passthru.PassthruAuthenticator </class><mode>USER</mode><allowGuest/></authenticator></security><shareMapper><class>org.alfresco.jlan.sample.HomeShareMapper</class><debug/></shareMapper></jlanserver>
			output.close();
			falgs = true;
		}catch (IOException e) {
			e.printStackTrace();
			falgs = false;
		}
    	return falgs;
  }
    
    /**
     * 创建一个文件
     * @param filepath
     * @param fileinfo
     * @return
     */
    public static boolean createURLFile(String baseUrl,String saveFilePath,int userId){
    	boolean falgs = false;
    	File file = new File(saveFilePath);
    	BufferedWriter output;
    	try {
			File fdir = new File(file.getParent());
			if(!fdir.exists()){
				fdir.mkdirs();
			}
			output = new BufferedWriter(new FileWriter(file));
			String outtxt = DBUtil.getURLMYFILE_TXT(baseUrl,userId);
			output.write(outtxt);
			output.close();
			falgs = true;
		}catch (IOException e) {
			e.printStackTrace();
			falgs = false;
		}
    	return falgs;
    }
    
    public static void main(String [] args){
  
//    	Map<String,Object> fmap = getFindResults2("e:/logr2.txt",2,"/usr",false);
//    	System.out.println(fmap.get("totalSize"));
//    	System.out.println(fmap.get("totalCount"));
//    	System.out.println(((List)fmap.get("pathList")).size());
    	
//    	System.out.println(StringUtil.modv(StringUtil.div(50, 1250)));
//    	
//    	System.out.println(StringUtil.div(50, 1250));
//    	
//    	System.out.println(StringUtil.div(20, 100));
//    	createDelFile("G:/aa/bb.dat","删除了","G:/aa/cc/bb.dat",new Date());
    	
//    	Map<String,String> map = new HashMap<String,String>();
//    	map.put("targetPath", "C:/aa/cc/test1.dat");
//    	map.put("targetType", "1");
//    	map.put("targetPoolId", "28");
//    	createFile("C:/aa/test1.dat", map);
//    	String str = readByRow("C:/aa/test1.dat",2); 
//    	String[] s = str.split("&");
//    	Map<String, Object> map = new HashMap<String,Object>();
//    	map.get("targetPath");
//    	System.out.println(str);
//    	String str7 = "targetPath=/usr/linkapp/data/L00002L5/as.txt";
//    	String str8 = "sourcePoolId=2&targetPath=/usr/linkapp/data/L00003L5/2/1375845720081/amazon-dynamo-sosp2007.pdf&sourcePath=/amazon-dynamo-sosp2007.pdf&moveRecordId=157&sourceRollId=2&targetPoolId=160&targetType=1&";
//    	String[] str1 =str8.split("&");
//		String sourcePoolId = str1[0].split("=")[1];
//		String targetPath   = str1[1].split("=")[1];
//		String sourcePath   = str1[2].split("=")[1];
//		String moveRecordId = str1[3].split("=")[1];
//		String sourceRollId = str1[4].split("=")[1];
//		String targetPoolId = str1[5].split("=")[1];
//		String targetType   = str1[6].split("=")[1];
//		System.out.print(sourcePoolId+"\n"+targetPath+"\n"+sourcePath+"\n"+moveRecordId+"\n"+sourceRollId+"\n"+targetPoolId+"\n"+targetType);
//    	String map = "/usr/linkapp/data/L00002L5/as.txt";
//    	map = map.replaceAll(map, map+"   sdfdsfdsfdsfsd");
//    	System.out.println(map);
    
//    	
//    	String str2 = readByRow("G:/aa/test1.dat",2);
//    	System.out.println(str2);
    	
//    	String s ="";
    	
//    	String str = converPath("/usr//linkapp\\aa\\cc\\aa.txt");
//    	System.out.println(str);
    	
    	boolean bn = checkPath("/usr/.SVN/aab.in");
    	System.out.println(bn);
    }
}