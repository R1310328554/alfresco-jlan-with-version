package com.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Date;
import java.util.Properties;

import org.apache.log4j.Logger;

public class DBUtil {  
	private static Logger log4j = Logger.getRootLogger();
	
	public static String driver = null;
	public static String url = null;
	public static String username = null;
	public static String password = null;
	public static boolean ldapAuth = false;
//	private static DBUtil instance = null;
	public static final String NETDISK_USERFILE_STOREDIR_DEFAULT = "netdisk.userfile.storedir.default";//用户网盘默认存储池
	public static final String NETDISK_COMMFILE_STOREDIR_DEFAULT = "netdisk.commfile.storedir.default";//公共资料库默认存储池ID
	public static String SHARENAME_USERFILE = "我的文件";
	public static String SHARENAME_COMMFILE = "资料库";	
	public static String SHARENAME_COMMFILE_ALIAS = "公共资料库";//别名
	public static String SHARENAME_RECIVEFILE = "我收到的共享文件";
	public static String ROLE_VALUE_FOR_ADMIN = "brltwpdgucem";//[b浏览,r阅读,l关联,t版本创建,w写入,p打印,d下载,g更改权限,u更改所有者,c审核,e删除]
	public static String SUPPORT_EXT = "pdf,html,odt,sxw,doc,docx,rtf,wpd,wiki,ods,sxc,xls,xlsx,csv,tsv,odp,sxi,ppt,pptx,odg,svg,wps,wpt,et,ett,dps,dpt,tif,tiff,txt";
	public static String CAD_EXT= "dwg";//,dwl,dwl2
	public static String PHOTO_EXT="jpg,jpeg,gif,bmp,png,psd";
	public static String  pre_Move   = "mp4,AVI,3GP,RMVB,GIF,WMV,MKV,MPG,VOB,MOV,FLV,SWF,MP4,avi,3gp,rmvb,gif,wmv,mkv,mpg,vob,mov,flv,swf";
	public static String SPECIAL_CHAR="@";//"@"
	public static final String WAS_COMMONFILE="was_commonfile";
	public static final String WAS_SHAREFILE ="was_sharefile";
	public static final String WAS_USERMYFILE="was_usermyfile";
	public static String userSharesQuery_all_sql = "SELECT id as id,username as name,DATE_FORMAT(lastvisit,'%Y%m%d %H:%i:%s') AS createtime,status FROM jweb_users WHERE status>=0 ORDER BY id limit 0,200";
	public static final String CLOUDURL="文件云.url";
	
	public final static String SHARENAME_WAS = "was";
	public final static String SHARENAME_WAS_USERFILE = "userfile";
	public final static String SHARENAME_WAS_COMMFILE = "commonfile";
	public final static String SHARENAME_WAS_RECIVEFILE = "sharefile";
	public static String WAS_LOCALUSER_PREFIX="linkapp_";//云编辑服务器的，本机用户的前缀，增加这个是为了当为云编辑服务器访问NAS时，方便让认证通过
	
	/**
	 * 初始化(启动应用时初始一次即可，减少重复)
	 */
	static
	{
		FileInputStream in = null;
		try{
			in = new FileInputStream(FileUtil.getRootPath()+"/WEB-INF/classes/db_config.properties");
			Properties prop = new Properties();
			prop.load(in);
			driver = prop.getProperty("database.driver").trim();
			url = prop.getProperty("database.url").trim();
			username = prop.getProperty("database.username").trim();
			password = prop.getProperty("database.password").trim();
			//默认不采用ldap验证
			if(prop.getProperty("ldap.enabled").equalsIgnoreCase("true")){
				ldapAuth = true;
			}
			System.out.println("初始化数据库配置完成 ,数据库 url:"+url);
			//cifs nas config
			if(null != prop.getProperty("nas.SPECIAL_CHAR"))
			{
				SPECIAL_CHAR =  prop.getProperty("nas.SPECIAL_CHAR").trim();
				System.out.println("初始化nas.SPECIAL_CHAR:"+SPECIAL_CHAR);
			}
			if(null != prop.getProperty("nas.ROLE_VALUE_FOR_ADMIN"))
			{
				ROLE_VALUE_FOR_ADMIN =  prop.getProperty("nas.ROLE_VALUE_FOR_ADMIN").trim();
				System.out.println("初始化nas.ROLE_VALUE_FOR_ADMIN:"+ROLE_VALUE_FOR_ADMIN);
			}
			if(null != prop.getProperty("nas.SUPPORT_EXT"))
			{
				SUPPORT_EXT =  prop.getProperty("nas.SUPPORT_EXT").trim();
				System.out.println("初始化nas.SUPPORT_EXT:"+SUPPORT_EXT);
			}
			if(null != prop.getProperty("nas.SHARENAME_USERFILE"))
			{
				SHARENAME_USERFILE =  prop.getProperty("nas.SHARENAME_USERFILE").trim();
				System.out.println("初始化nas.SHARENAME_USERFILE:"+SHARENAME_USERFILE);
			}
			if(null != prop.getProperty("nas.SHARENAME_COMMFILE"))
			{
				SHARENAME_COMMFILE =  prop.getProperty("nas.SHARENAME_COMMFILE").trim();
				System.out.println("初始化nas.SHARENAME_COMMFILE:"+SHARENAME_COMMFILE);
			}
			if(null != prop.getProperty("nas.SHARENAME_RECIVEFILE"))
			{
				SHARENAME_RECIVEFILE =  prop.getProperty("nas.SHARENAME_RECIVEFILE").trim();
				System.out.println("初始化nas.SHARENAME_RECIVEFILE:"+SHARENAME_RECIVEFILE);
			}
			if(null != prop.getProperty("sql.userSharesQueryAll"))
			{
				userSharesQuery_all_sql = prop.getProperty("sql.userSharesQueryAll");
			}
			if(null != prop.getProperty("was.localuser.prefix"))
			{
				WAS_LOCALUSER_PREFIX = prop.getProperty("was.localuser.prefix").toLowerCase();
			}
			System.out.println("**********初始化完成********");
		}catch(FileNotFoundException e1)
		{
			log4j.error("数据库配置文件找不到：/WEB-INF/classes/db_config.properties");
		} catch (IOException e) {
			log4j.error("获取数据库相关配置报IO错误：\n"+e.getMessage());
		}
		finally 
		{
			try{
			if(null != in) in.close();
			}catch(Exception e)
			{
				log4j.error(e.getMessage());
			}
		}
	}   
	
	public static Connection getConnection(){
		Connection conn = null;
		try {
			Class.forName(driver);
			conn = DriverManager.getConnection(url, username, password);
		} catch (ClassNotFoundException e) {
			log4j.error("获得数据库连接：ClassNotFoundException：\n"+e.getMessage());
		} catch (SQLException e) {
			log4j.error("获得数据库连接：SQLException：\n"+e.getMessage());
		}
		return conn;
	}
	
	public static String getURLMYFILE_TXT(String baseUrl,int userid)
	{
		Date now = new Date();
		String s = MD5Util.hash(userid+""+now.getTime()).substring(0,16);
		if(!baseUrl.endsWith("/"))
			baseUrl+="/";
		String url = baseUrl+"api.action?acmod=myfile&ac=CIFS&n="+userid+"&sid="+AESUtil.encryptStrToHexStr(userid+"", s)+"&s="+s;
		String txt =  "[InternetShortcut]\n"+"URL="+url+"\n";
		return txt;
	}
	
	public static String getURLCOMMFILE_TXT(String baseUrl,int userid)
	{
		Date now = new Date();		
		String s = MD5Util.hash(userid+""+now.getTime()).substring(0,16);
		if(!baseUrl.endsWith("/"))
			baseUrl+="/";
		String url = baseUrl+"api.action?acmod=commonfile&ac=CIFS&n="+userid+"&sid="+AESUtil.encryptStrToHexStr(userid+"", s)+"&s="+s;
		String txt =  "[InternetShortcut]\n"+"URL="+url+"\n";
		return txt;
	}
	
}     
