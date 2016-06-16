package com.disk;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.text.SimpleDateFormat;

import org.alfresco.jlan.debug.Debug;
import org.alfresco.jlan.server.SrvSession;
import org.alfresco.jlan.server.core.DeviceContext;
import org.alfresco.jlan.server.core.DeviceContextException;
import org.alfresco.jlan.server.filesys.AccessDeniedException;
import org.alfresco.jlan.server.filesys.DiskDeviceContext;
import org.alfresco.jlan.server.filesys.DiskFullException;
import org.alfresco.jlan.server.filesys.DiskInterface;
import org.alfresco.jlan.server.filesys.FileAttribute;
import org.alfresco.jlan.server.filesys.FileExistsException;
import org.alfresco.jlan.server.filesys.FileInfo;
import org.alfresco.jlan.server.filesys.FileName;
import org.alfresco.jlan.server.filesys.FileOpenParams;
import org.alfresco.jlan.server.filesys.FileStatus;
import org.alfresco.jlan.server.filesys.FileSystem;
import org.alfresco.jlan.server.filesys.NetworkFile;
import org.alfresco.jlan.server.filesys.PathNotFoundException;
import org.alfresco.jlan.server.filesys.SearchContext;
import org.alfresco.jlan.server.filesys.TreeConnection;
import org.alfresco.jlan.smb.server.SMBSrvSession;
import org.alfresco.jlan.smb.server.disk.JavaNetworkFile;
import org.apache.log4j.Logger;
import org.springframework.extensions.config.ConfigElement;

import com.dao.BaseDao;
import com.fileSystem.FileSystemDAO;
import com.listener.FileWatch;
import com.util.FileUtil;
import com.util.SpringUtil;

/**
 * Disk interface implementation that uses the java.io.File class.
 * 
 * @author gkspencer
 */
public class JavaFileDiskDriver2 implements DiskInterface {

	private FileSystemDAO fileSysDao;
	
	private ExecutorService pool;

	private Logger log4j = Logger.getLogger(this.getClass());
	// DOS file seperator character

	private static final String DOS_SEPERATOR = "\\";
	protected SMBSrvSession m_sess;
	
	// SMB date used as the creation date/time for all files
	
	protected static long _globalCreateDate = System.currentTimeMillis();
	public static boolean moveBack = false;
	SimpleDateFormat sf = new SimpleDateFormat("yyyy-MM-d HH:mm:ss");

	/**
	 * Class constructor
	 */
	public JavaFileDiskDriver2() {
		super();
	}

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
			//默认不采用ldap验证
			if(prop.getProperty("Move_back").equalsIgnoreCase("true")){
				moveBack = true;
			}
		}catch(FileNotFoundException e1)
		{
			Debug.print("数据库配置文件找不到：/WEB-INF/classes/db_config.properties");
		} catch (IOException e) {
			Debug.print("获取数据库相关配置报IO错误：\n"+e.getMessage());
		}
		finally 
		{
			try{
			if(null != in) in.close();
			}catch(Exception e)
			{
				Debug.print(e.getMessage());
			}
		}
	}   
	/**
	 * modify the file information for the specified file, if it
	 * exists.
	 * 
	 * @param path
	 *            String
	 * @param relPath
	 *            String
	 * @return FileInfo
	 */
	public void modify(String path,String relPath) throws IOException{
		String[] pathStr = FileName.splitPath(path, java.io.File.separatorChar);
		if (pathStr[1] != null) {

			// Create a file object
			File file = new File(pathStr[0], pathStr[1]);
			if (file.exists() == true && file.isFile()) {

				// Fill in a file information object for this file/directory
				log4j.debug(" Fill in a file information object for this file/directory");
				long flen = file.length();
				long alloc = (flen + 512L) & 0xFFFFFFFFFFFFFE00L;
				int fattr = 0;

				if (file.isDirectory())
					fattr = FileAttribute.Directory;

				if (file.canWrite() == false)
					fattr += FileAttribute.ReadOnly;
				//hide the file 
				int exitValue = -1;
				if(file.getName().contains("LinkApp.label.xml"))
				{
					int jie = file.getPath().lastIndexOf("LinkApp.label.xml");
					String path1 = file.getPath().substring(0,jie);
					try {
						String[] cmd =new String[]{"/bin/sh", "-c", "mv "+path1+"/LinkApp.label.xml"+" "+path1+"/.LinkApp.label.xml"};
						ProcessBuilder builder = new ProcessBuilder();  
						builder.command(cmd); 
						Process process = builder.start();
						
							exitValue = process.waitFor();
							log4j.debug("SUCCESS：" + exitValue);
//							Runtime.getRuntime().exec(cmd);
						} catch (Exception e) {
							log4j.error("modify ERROR: path:"+path+",relPath="+relPath,e);
						}  
				  }
				if(file.getName().endsWith(".SPARSE.LFS"))
				{
					int jie = file.getPath().lastIndexOf(".SPARSE.LFS");
					String path1 = file.getPath().substring(0,jie);
					File file1 = new File(path1);
					file.renameTo(file1);
					JavaNetworkFile netFile = new JavaNetworkFile(file, path1);
					netFile.flushFile();
				 }			
			}
		}	
	}
	/**
	 * Build the file information for the specified file/directory, if it
	 * exists.
	 * 
	 * @param path
	 *            String
	 * @param relPath
	 *            String
	 * @return FileInfo
	 */
	protected FileInfo buildFileInformation(String path, String relPath) throws IOException {
		log4j.debug("buildFileInformation-- path=" + path + " relPath="+ relPath);
		String[] pathStr = FileName.splitPath(path, java.io.File.separatorChar);
		// Create a Java file to get the file/directory information
		if (pathStr[1] != null) {

			// Create a file object
			File file = new File(pathStr[0], pathStr[1]);
			if (file.exists() == true && file.isFile()) {

				// Fill in a file information object for this file/directory
				log4j.debug(" Fill in a file information object for this file/directory");
				long flen = file.length();
				long alloc = (flen + 512L) & 0xFFFFFFFFFFFFFE00L;
				int fattr = 0;
				
				if (file.isDirectory())
					fattr = FileAttribute.Directory;

				if (file.canWrite() == false)
					fattr += FileAttribute.ReadOnly;
				
				if (pathStr[1].equalsIgnoreCase("Desktop.ini")|| pathStr[1].equalsIgnoreCase("Thumbs.db")|| pathStr[1].charAt(0) == '.')
					fattr += FileAttribute.Hidden;
				
				// Create the file information

				FileInfo finfo = new FileInfo(pathStr[1], flen, fattr);
				long fdate = file.lastModified();
				finfo.setModifyDateTime(fdate);
				finfo.setAllocationSize(alloc);
				finfo.setFileId(relPath.hashCode());

				finfo.setCreationDateTime(getGlobalCreateDateTime() > fdate ? fdate: getGlobalCreateDateTime());
				finfo.setChangeDateTime(fdate);
			
				return finfo;
			} else {
				log4j.debug(" Rebuild the path, looks like it is a directory ,pathStr[0]="+ pathStr[0] + ", pathStr[1]=" + pathStr[1]);
				// Rebuild the path, looks like it is a directory

				File dir = new File(FileName.buildPath(pathStr[0], pathStr[1],null, java.io.File.separatorChar));
				if (dir.exists() == true) {
					log4j.debug(" dir exists  dir = " + dir.getAbsolutePath()+ ",pathStr[0]=" + pathStr[0] + ", pathStr[1]="+ pathStr[1]);
					// Fill in a file information object for this directory
					int fattr = 0;
					if (dir.isDirectory())
						fattr = FileAttribute.Directory;
					FileInfo finfo = new FileInfo(pathStr[1] != null ? pathStr[1] : "", 0, fattr);
					long fdate = file.lastModified();
					finfo.setModifyDateTime(fdate);
					finfo.setFileId(relPath.hashCode());

					finfo.setCreationDateTime(getGlobalCreateDateTime() > fdate ? fdate: getGlobalCreateDateTime());
					finfo.setChangeDateTime(fdate);
					log4j.debug(" FileInfo= " + finfo + " ; finfo.FileId="+ finfo.getFileId() + " ,finfo.name = "+ finfo.getFileName() + ", shareName="+ finfo.getShortName() + " ,finfo.path"+ finfo.getPath() + "");
					return finfo;
				} else {
//					log4j.error("dir not exists  dir=" + dir.getAbsolutePath());
				}
			}
		} else {

			// Get file information for a directory
			log4j.debug(" Get file information for a directory ,pathStr[0]="+ pathStr[0] + ", pathStr[1]=" + pathStr[1]);
			File dir = new File(pathStr[0]);
			if (dir.exists() == true) {
				log4j.debug(" dir exists  dir = " + dir.getAbsolutePath());
				// Fill in a file information object for this directory

				int fattr = 0;
				if (dir.isDirectory())
					fattr = FileAttribute.Directory;

				FileInfo finfo = new FileInfo(pathStr[1] != null ? pathStr[1]: "", 0, fattr);
				long fdate = dir.lastModified();
				finfo.setModifyDateTime(fdate);
				finfo.setFileId(relPath.hashCode());

				finfo.setCreationDateTime(getGlobalCreateDateTime() > fdate ? fdate: getGlobalCreateDateTime());
				finfo.setChangeDateTime(fdate);
				log4j.debug(" FileInfo= " + finfo + " ; finfo.FileId="+ finfo.getFileId() + " ,finfo.name = "+ finfo.getFileName() + ", shareName="+ finfo.getShortName() + " ,finfo.path"+ finfo.getPath() + "");
				return finfo;
			} else {
//				log4j.error("dir not exists  dir=" + dir.getAbsolutePath());
			}
		}

		// Bad path
		
		return null;
	}

	/**
	 * Close the specified file
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param file
	 *            Network file details
	 * @exception IOException
	 */
	public void closeFile(SrvSession sess, TreeConnection tree, NetworkFile file)throws java.io.IOException {
		log4j.debug("closeFile--  sess=" + sess + " ; TreeConnection=" + tree+ " NetworkFile = " + file);

		// Close the file
		file.closeFile();
		// Check if the file/directory is marked for delete
		if (file.hasDeleteOnClose()) {
			// Check for a file or directory
			if (file.isDirectory())
				deleteDirectory(sess, tree, file.getFullName());
			else
				deleteFile(sess, tree, file.getFullName());
		}
	}
	/**
	 * Create a new directory
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param params
	 *            Directory parameters
	 * @exception IOException
	 */
	public void createDirectory(SrvSession sess, TreeConnection tree,
			FileOpenParams params) throws java.io.IOException {
		log4j.debug("createDirectory-- TreeConnection=" + tree+ " ,FileOpenParams=" + params);
		// Get the full path for the new directory

		String dirname = FileName.buildPath(tree.getContext().getDeviceName(),params.getPath(), null, java.io.File.separatorChar);

		log4j.debug("params.getPath()=" + params.getPath());
		// 存储池中强制不允许创建目录
		if (isPool(params.getPath()))
		{
			throw new AccessDeniedException(dirname);
		} else {

			// Create the new directory
			File newDir = new File(dirname);
			if (newDir.mkdir() == false)
				throw new AccessDeniedException(dirname);
		}
	}

	/**
	 * Create a new file
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param params
	 *            File open parameters
	 * @return NetworkFile
	 * @exception IOException
	 * @throws  
	 * @throws SMBException 
	 */
//TODO
	public NetworkFile createFile(SrvSession sess, TreeConnection tree,FileOpenParams params) throws java.io.IOException{
		log4j.debug("createFile--  FileOpenParams=" + params);
		// Get the full path for the new file
		DeviceContext ctx = tree.getContext();
		String fname = FileName.buildPath(ctx.getDeviceName(),params.getPath(), null, java.io.File.separatorChar);
	    log4j.debug("params.getPath()=" + params.getPath());
	    int jie =0;
	    if(params.getPath().indexOf("\\",2)<0){
	    	throw new AccessDeniedException("这里不能存放文件");
	    }else{
	    	jie = params.getPath().indexOf("\\",2);
	    }
		
		String name = params.getPath().substring(1, jie);
		int qa = fname.indexOf(name);
		String path = fname.substring(0, qa - 1);
	

		if (isPool(params.getPath()))// 存储池中强制不允许创建文件
		{
			throw new AccessDeniedException();
		}
		File file = new File(fname);
		if (file.exists())
			throw new FileExistsException();
		BaseDao baseDao = (BaseDao) SpringUtil.getBean("baseDao");
		StringBuffer qStr2 = new StringBuffer();
		StringBuffer qStr3 = new StringBuffer();
		// 在数据库中比较total 和 has 的大小 然后根据返回的值来进行阻止
		qStr2.append("select totalSpace-hasSpace from jweb_backupmediaroll where name="+ "'" + name + "'").append("and  pool_id =").append(
				"(select pool_id from jweb_pool_sharing where name=" + "'"+ ctx.getShareName() + "'" + ")");
		qStr3.append("select totalSpace from jweb_backupmediaroll where name=" + "'"+ name + "'").append("and  pool_id =").append(
				"(select pool_id from jweb_pool_sharing where name=" + "'"+ ctx.getShareName() + "'" + ")");
		long difSpace = baseDao.querlong(qStr2.toString());
		long totalSpace = baseDao.querlong(qStr3.toString());
		log4j.debug("difSpace---" + difSpace);
		if (difSpace < totalSpace * 0.1) {
			// 数据已经不能上传扫数据库
//			 String delPath = path+"/"+name;
//			File delPath2 = new File(delPath);
//			System.out.println("查看是否执行到这里 1");
//		    String[] filelist = delPath2.list();  
//			 for (int i = 0; i < filelist.length; i++) {  
//			delPath = delPath+ "/" + filelist[i]+"-"+ctx.getShareName()+"-"+name;
//			System.out.println("查看是否执行到这里 2");
//			try {
//				deletefile2(delPath);
//			} catch (Exception e) {
//				e.printStackTrace();
//			}
//			    }
			log4j.debug("抛异常了" + difSpace);
			throw new DiskFullException();
		}
		// Check if the file already exists
		// Create the new file
		FileWriter newFile = new FileWriter(fname, false);
		newFile.close();

		// Create a Java network file
		JavaNetworkFile netFile = new JavaNetworkFile(file, params.getPath());
		netFile.setGrantedAccess(NetworkFile.READWRITE);
		netFile.setFullName(params.getPath());
		// Return the network file
	   
		return netFile;
	}

	/**
	 * Delete a directory
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param dir
	 *            Path of directory to delete
	 * @exception IOException
	 */
	public void deleteDirectory(SrvSession sess, TreeConnection tree, String dir)throws java.io.IOException {
		log4j.debug("deleteDirectory-- TreeConnection=" + tree + ", dir="+ dir);
		// Get the full path for the directory

		DeviceContext ctx = tree.getContext();
		String dirname = FileName.buildPath(ctx.getDeviceName(), dir, null,java.io.File.separatorChar);

		log4j.debug("dir=" + dir);
		if (isPool(dir))// 存储池中强制不允许创建文件
		{
			throw new AccessDeniedException();
//			throw new org.springframework.security.access.AccessDeniedException("此卷不能删除");
		}
		// Check if the directory exists, and it is a directory
//		String fileRollPath = dir.substring(1,dir.indexOf("\\",2));
//		dirname = dirname+"#"+ctx.getShareName()+"#"+fileRollPath;
		try {
			deletefile(dirname);
		} catch (Exception e) {
			log4j.error("deleteDirectory ERROR: dir:"+dir,e);
		}
		File delDir = new File(dirname);
		if (delDir.exists() && delDir.isDirectory()) {

			// Check if the directory contains any files
			
			String[] fileList = delDir.list();
			if (fileList != null && fileList.length > 0)
				throw new AccessDeniedException("Directory not empty");

			// Delete the directory

			delDir.delete();
		}

		// If the path does not exist then try and map it to a real path, there
		// may be case differences

		else if (delDir.exists() == false) {

			// Map the path to a real path

			String mappedPath = mapPath(ctx.getDeviceName(), dir);
			if (mappedPath != null) {

				// Check if the path is a directory

				delDir = new File(mappedPath);
				if (delDir.isDirectory()) {

					// Check if the directory contains any files

					String[] fileList = delDir.list();
					if (fileList != null && fileList.length > 0)
						throw new AccessDeniedException("Directory not empty");

					// Delete the directory

					delDir.delete();
				}
			}
		}
	}

	/**
	 * Delete a file
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param name
	 *            Name of file to delete
	 * @exception IOException
	 */
	
	//TODO
	public void deleteFile(SrvSession sess, TreeConnection tree, String name)throws java.io.IOException {
		log4j.debug("deleteFile-- TreeConnection=" + tree + ", name=" + name);
		// Get the full path for the file
		if(isPool(name)){
			throw new AccessDeniedException();
		}
		DeviceContext ctx = tree.getContext();
		String fullname = FileName.buildPath(ctx.getDeviceName(), name, null,java.io.File.separatorChar);
		String name1 =null;
		if(name.length()>10){
			int jie = name.indexOf("\\",2);
			  name1 = name.substring(1, jie);
		}
		
		log4j.debug("deleteFile-- TreeConnection=" + ctx.getDeviceName()+ ", name=" + name);
		BaseDao baseDao = (BaseDao) SpringUtil.getBean("baseDao");
		StringBuffer qStr = new StringBuffer();
		// Check if the file exists, and it is a file
		File delFile = new File(fullname);
		long deFileSize = 0L;
		if (delFile.exists() && delFile.isFile()) {
			// If the path does not exist then try and map it to a real path,there may be case differences
			deFileSize = delFile.length();
			StringBuffer qStr1 = new StringBuffer();
			qStr1.append("select hasSpace from jweb_backupmediaroll r where r.name="+ "'" + name1 + "'")
			.append("and r.pool_id ="+ "("+"select pool_id from jweb_pool_sharing where name="+ "'" + ctx.getShareName() + "'" + ")");
			long totalSpace = baseDao.querlong(qStr1.toString());
			if (totalSpace != 0 && totalSpace > deFileSize) {
				qStr.append("update jweb_backupmediaroll r set r.hasSpace=r.hasSpace-"+ deFileSize + " where r.name=" + "'"+ name1 + "'")
					.append("and r.pool_id ="+ "("+ "select pool_id from jweb_pool_sharing where name="+ "'" + ctx.getShareName() + "'" + ")");
				log4j.debug(qStr.toString());
				baseDao.update(qStr.toString());
				delFile.delete();
			} else {
				delFile.delete();
			}
		} else if (delFile.exists() == false) {

			// Map the path to a real path

			String mappedPath = mapPath(ctx.getDeviceName(), name);
			if(isPool(mappedPath)){
				throw new AccessDeniedException();
			}else{

			if (mappedPath != null) {

				// Check if the path is a file and exists
//				 delFile = checkFile(mappedPath,sess);
				 
				if (delFile.exists() && delFile.isFile()){
									delFile.delete();
					}
				else{
					throw new AccessDeniedException("");
				}
				}
			}
		}
	}

	/**
	 * Check if the specified file exists, and it is a file.
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param name
	 *            File name
	 * @return int
	 */
	public int fileExists(SrvSession sess, TreeConnection tree, String name) {
		log4j.debug("fileExists-- TreeConnection=" + tree + ", name=" + name);
		// Get the full path for the file

		DeviceContext ctx = tree.getContext();
		String filename = FileName.buildPath(ctx.getDeviceName(), name, null,java.io.File.separatorChar);

		// Check if the file exists, and it is a file

		File chkFile = new File(filename);
		if (chkFile.exists()) {

			// Check if the path is a file or directory

			if (chkFile.isFile())
				return FileStatus.FileExists;
			else
				return FileStatus.DirectoryExists;
		}

		// If the path does not exist then try and map it to a real path, there
		// may be case differences

		if (chkFile.exists() == false) {

			// Map the path to a real path

			try {
				String mappedPath = mapPath(ctx.getDeviceName(), name);
				if (mappedPath != null) {

					// Check if the path is a file

					chkFile = new File(mappedPath);
					if (chkFile.exists()) {
						if (chkFile.isFile())
							return FileStatus.FileExists;
						else
							return FileStatus.DirectoryExists;
					}
				}
			} catch (FileNotFoundException ex) {
				log4j.error("FileNotFoundException name=" + name);
			} catch (PathNotFoundException ex) {
				log4j.error("PathNotFoundException name=" + name);
			}
		}

		// Path does not exist or is not a file

		return FileStatus.NotExist;
	}

	/**
	 * Flush buffered data for the specified file
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param file
	 *            Network file
	 * @exception IOException
	 */
	public void flushFile(SrvSession sess, TreeConnection tree, NetworkFile file) throws java.io.IOException {
		
		// Flush the file

		file.flushFile();
	}

	/**
	 * Return file information about the specified file
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param name
	 *            File name
	 * @return SMBFileInfo
	 * @exception IOException
	 */
	public FileInfo getFileInformation(SrvSession sess, TreeConnection tree, String name) throws java.io.IOException {

		log4j.debug("getFileInformation-- TreeConnection=" + tree + ", name=" + name);
		// Get the full path for the file/directory
		DeviceContext ctx = tree.getContext();
		String path = FileName.buildPath(ctx.getDeviceName(), name, null,java.io.File.separatorChar);
		// Build the file information for the file/directory
		FileInfo  info = buildFileInformation(path, name);
		if (info != null)
			return info;
		// Try and map the path to a real path

		String mappedPath = mapPath(ctx.getDeviceName(), name);
//		if(mappedPath.endsWith(".SPARSE.LFS"))
//		{
//			File file = checkFile(mappedPath,sess);
//			mappedPath = file.getPath();
//		}
		if (mappedPath != null)
			return buildFileInformation(mappedPath, name);

		// Looks like a bad path

		return null;
	}

	/**
	 * Determine if the disk device is read-only.
	 * 
	 * @param sess
	 *            Session details
	 * @param ctx
	 *            Device context
	 * @return true if the device is read-only, else false
	 * @exception IOException
	 *                If an error occurs.
	 */
	public boolean isReadOnly(SrvSession sess, DeviceContext ctx) throws java.io.IOException {

		log4j.debug("isReadOnly-- DeviceContext=" + ctx);
		// Check if the directory exists, and it is a directory

		File rootDir = new File(ctx.getDeviceName());
		if (rootDir.exists() == false || rootDir.isDirectory() == false)
			throw new FileNotFoundException(ctx.getDeviceName());

		// Create a temporary file in the root directory, this will test if we
		// have write access
		// to the shared directory.

		boolean readOnly = true;

		try {

			// Create a temporary file

			File tempFile = null;
			boolean fileOK = false;

			while (fileOK == false) {

				// Create a temporary file name

				tempFile = new File(rootDir, "_JSRV"+ (System.currentTimeMillis() & 0x0FFF) + ".TMP");
				if (tempFile.exists() == false)
					fileOK = true;
			}

			// Create a temporary file

			FileWriter outFile = new FileWriter(tempFile);
			outFile.close();

			// Delete the temporary file

			tempFile.delete();

			// Shared directory appears to be writeable by the JVM

			readOnly = false;
		} catch (IllegalArgumentException ex) {
		} catch (IOException ex) {
		}

		// Return the shared directory read-onyl status

		return readOnly;
	}

	/**
	 * Map the input path to a real path, this may require changing the case of
	 * various parts of the path.
	 * 
	 * @param path
	 *            Share relative path
	 * @return Real path on the local filesystem
	 */
	protected final String mapPath(String path)throws java.io.FileNotFoundException, PathNotFoundException {
		return mapPath("", path);
	}

	/**
	 * Map the input path to a real path, this may require changing the case of
	 * various parts of the path. The base path is not checked, it is assumed to
	 * exist.
	 * 
	 * @param base
	 *            java.lang.String
	 * @param path
	 *            java.lang.String
	 * @return java.lang.String
	 * @exception java.io.FileNotFoundException
	 *                The path could not be mapped to a real path.
	 * @exception PathNotFoundException
	 *                Part of the path is not valid
	 */
	protected final String mapPath(String base, String path)throws java.io.FileNotFoundException, PathNotFoundException {
		// Split the path string into seperate directory components
		log4j.debug("mapPath-- base=" + base + " , path=" + path);
		
		if(!"".equals(base)){
//			if(path.contains("离线文件--")){
//				path = path.replaceAll("离线文件--", "");
//			}
			String filterPath = FileUtil.converPath(base+path);
			boolean falgs = FileUtil.checkPath(filterPath);
			if(falgs){
				File file = new File(filterPath+".SPARSE.LFS");
				if(file.exists() && file.isFile()){
					path = path+".SPARSE.LFS";
				}
			}
		}
		
		
		String pathCopy = path;
		if (pathCopy.length() > 0 && pathCopy.startsWith(DOS_SEPERATOR))
			pathCopy = pathCopy.substring(1);

		StringTokenizer token = new StringTokenizer(pathCopy, "\\/");
		int tokCnt = token.countTokens();

		// The mapped path string, if it can be mapped

		String mappedPath = null;

		if (tokCnt > 0) {

			// Allocate an array to hold the directory names

			String[] dirs = new String[token.countTokens()];

			// Get the directory names

			int idx = 0;
			while (token.hasMoreTokens())
				dirs[idx++] = token.nextToken();

			// Check if the path ends with a directory or file name, ie. has a
			// trailing '\' or not

			int maxDir = dirs.length;

			if (path.endsWith(DOS_SEPERATOR) == false) {

				// Ignore the last token as it is a file name

				maxDir--;
				
			}

			// Build up the path string and validate that the path exists at
			// each stage.

			StringBuffer pathStr = new StringBuffer(base);
			if (base.endsWith(java.io.File.separator) == false)
				pathStr.append(java.io.File.separator);

			int lastPos = pathStr.length();
			idx = 0;
			File lastDir = null;
			if (base != null && base.length() > 0)
				lastDir = new File(base);
			File curDir = null;

			while (idx < maxDir) {

				// Append the current directory to the path

				pathStr.append(dirs[idx]);
				pathStr.append(java.io.File.separator);

				// Check if the current path exists

				curDir = new File(pathStr.toString());

				if (curDir.exists() == false) {

					// Check if there is a previous directory to search

					if (lastDir == null)
						throw new PathNotFoundException();

					// Search the current path for a matching directory, the
					// case may be different

					String[] fileList = lastDir.list();
					if (fileList == null || fileList.length == 0)
						throw new PathNotFoundException();

					int fidx = 0;
					boolean foundPath = false;

					while (fidx < fileList.length && foundPath == false) {

						// Check if the current file name matches the required
						// directory name
						if (fileList[fidx].equalsIgnoreCase(dirs[idx])) {

							// Use the current directory name

							pathStr.setLength(lastPos);
							pathStr.append(fileList[fidx]);
							pathStr.append(java.io.File.separator);

							// Check if the path is valid

							curDir = new File(pathStr.toString());
							if (curDir.exists()) {
								foundPath = true;
								break;
							}
						}

						// Update the file name index

						fidx++;
					}

					// Check if we found the required directory

					if (foundPath == false)
						throw new PathNotFoundException();
				}

				// Set the last valid directory file

				lastDir = curDir;

				// Update the end of valid path location

				lastPos = pathStr.length();

				// Update the current directory index

				idx++;
			}

			// Check if there is a file name to be added to the mapped path
			if (path.endsWith(DOS_SEPERATOR) == false) {

				// Map the file name

				String[] fileList = lastDir.list();
				String fileName = dirs[dirs.length - 1];

				// Check if the file list is valid, if not then the path is not
				// valid

				if (fileList == null)
					throw new FileNotFoundException(path);

				// Search for the required file

				idx = 0;
				boolean foundFile = false;

				while (idx < fileList.length && foundFile == false) {
					if (fileList[idx].compareTo(fileName) == 0)
						foundFile = true;
					else
						idx++;
				}

				// Check if we found the file name, if not then do a case
				// insensitive search

				if (foundFile == false) {

					// Search again using a case insensitive search

					idx = 0;

					while (idx < fileList.length && foundFile == false) {
						if (fileList[idx].equalsIgnoreCase(fileName)) {
							foundFile = true;
							fileName = fileList[idx];
						} else
							idx++;
					}
				}

				// Append the file name

				pathStr.append(fileName);
			}

			// Set the new path string

			mappedPath = pathStr.toString();

			// Check for a Netware style path and remove the leading slash

			if (File.separator.equals(DOS_SEPERATOR)&& mappedPath.startsWith(DOS_SEPERATOR)&& mappedPath.indexOf(':') > 1)
				mappedPath = mappedPath.substring(1);
		}

		// Return the mapped path string, if successful.
		
		return mappedPath;
	}

	/**
	 * Open a file
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param params
	 *            File open parameters
	 * @return NetworkFile
	 * @exception IOException
	 */
	public NetworkFile openFile(SrvSession sess, TreeConnection tree,FileOpenParams params) throws java.io.IOException {

		log4j.debug("openFile-- TreeConnection=" + tree + " , FileOpenParams="+ params);
		// Create a Java network file

		DeviceContext ctx = tree.getContext();
		String fname = FileName.buildPath(ctx.getDeviceName(),params.getPath(), null, java.io.File.separatorChar);
		File file = new File(fname);
		//checkFile(fname,sess);
		
		if (file.exists() == false) {

			// Try and map the file name string to a local path

			String mappedPath = mapPath(ctx.getDeviceName(), params.getPath());
			if (mappedPath == null || mappedPath.endsWith(".SPARSE.LFS"))
			{
				file = new File(mappedPath);
			}
			else
			{
				file = new File(params.getPath());
				if (file.exists() == false)
				throw new FileNotFoundException(fname);
			}
//				throw new AccessDeniedException(fname);
			// Create the file object for the mapped file and check if the file  exists

			}

		if (file.canWrite() == false&& (params.isReadWriteAccess() || params.isWriteOnlyAccess()))
			throw new AccessDeniedException("File " + fname + " is read-only");

		// Create the network file object for the opened file/folder
		 NetworkFile netFile = null;
		 File file2 = new File(params.getPath());
		if(file2.exists()==false && file.getName().endsWith(".SPARSE.LFS"))
		{
			 file=checkFile(fname,sess,params);
			 netFile = new JavaNetworkFile(file, params.getPath());
				throw new java.io.IOException ("文件正在回迁队伍中；请稍等");
		}
		else
		{
			 netFile = new JavaNetworkFile(file, params.getPath());
		}
		if (params.isReadOnlyAccess())
			netFile.setGrantedAccess(NetworkFile.READONLY);
		else
			netFile.setGrantedAccess(NetworkFile.READWRITE);

		netFile.setFullName(params.getPath());

		// Check if the file is actually a directory

		if (file.isDirectory() || file.list() != null)
			netFile.setAttributes(FileAttribute.Directory);
		// Return the network file	
		return netFile;
	}
	/**
	 * 离线文件检查
	 * @param fname
	 * @return
	 * @throws IOException
	 */
	public File checkFile(String fname,SrvSession sess,FileOpenParams params) throws IOException{
		BaseDao baseDao = (BaseDao) SpringUtil.getBean("baseDao");
		String dName = fname;
		File file = new File(fname);
//		if(fname.contains("离线文件--")){
//			fname = fname.replaceAll("离线文件--", "");
//		}
		boolean falgs = FileUtil.checkPath(fname);
		if(falgs){
			if(fname.endsWith(".SPARSE.LFS")){
				file = new File (fname);
			}else{
				file = new File(fname+".SPARSE.LFS");
			}
			
			log4j.debug("openFile-- 离线--file.getPath() =" + file.getPath() );
			if(file.exists() && file.isFile())
			{
				if(fname.endsWith(".SPARSE.LFS"))
				{
					fname = fname;
				}
				else
				{
					
					fname = fname+".SPARSE.LFS";
				}
				String str = FileUtil.readBytes(fname, 0,30);
				if(str.startsWith(".SPARSE.LFS"))
				{
					String str2 = FileUtil.readByRow(fname, 2);
					if(!"".equals(str2))
					{
						String[] str1=str2.split("&");
						if(str1.length>=6)
						{
							String sourcePoolId = str1[0].split("=")[1];
							String targetPath   = str1[1].split("=")[1];
							String sourcePath   = str1[2].split("=")[1];
							String moveRecordId = str1[3].split("=")[1];
							String sourceRollId = str1[4].split("=")[1];
							String targetPoolId = str1[5].split("=")[1];
							String targetType   = str1[6].split("=")[1];
							String moveRecorde = str1.length>7?str1[7].split("=")[1]:"";
							Map<String,String> map = new HashMap<String,String>();
							if(moveRecorde.equals("")==false)
							{
								
							}
							map.put("sourcePoolId", sourcePoolId);
							map.put("targetPath", targetPath);
							map.put("sourcePath",sourcePath);
							map.put("moveRecordId", moveRecordId);
							map.put("sourceRollId", sourceRollId);
							map.put("targetPoolId", targetPoolId);
							map.put("targetType", targetType);
							StringBuffer qStr = new StringBuffer();
							
							if(!"".equals(targetPath) && !"".equals(targetPath))
							{
								int    targetType1 = Integer.parseInt(targetType);
								if(targetType1 == 1||targetType1 == 2)
								{	
									File targetFile = new File(targetPath);
									if(targetFile.isFile() && targetFile.exists())
									{
										file = new File(targetPath);
									}
									else
									{
										if(targetType1 == 2)
										{
											long etdotime = returnTime(map,dName,sess,params);
											throw new AccessDeniedException();
//											throw new java.io.IOException ("文件正在回迁队伍中；请稍等"+etdotime+"秒");
										}
										else
										{
											throw new AccessDeniedException();
//											throw new java.io.IOException ("文件不存在............");	
										}
									}
								}
								else if(Integer.parseInt(targetType) == 3||Integer.parseInt(targetType) == 6||Integer.parseInt(targetType) == 7||Integer.parseInt(targetType) == 8)
								{
									long etdotime = returnTime(map,dName,sess,params);
									throw new AccessDeniedException();
//									throw new java.io.IOException ("文件正在回迁队伍中；请稍等"+etdotime+"秒");
								}
							}
						}
					}
				}
			}
			else
			{
				file = new File(fname);
			}
		}
		return file;
	}
	/**
	 * Read a block of data from a file
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param file
	 *            Network file
	 * @param buf
	 *            Buffer to return data to
	 * @param bufPos
	 *            Starting position in the return buffer
	 * @param siz
	 *            Maximum size of data to return
	 * @param filePos
	 *            File offset to read data
	 * @return Number of bytes read
	 * @exception IOException
	 */
	public int readFile(SrvSession sess, TreeConnection tree, NetworkFile file,
			byte[] buf, int bufPos, int siz, long filePos)
			throws java.io.IOException {

		log4j.debug("openFile-- TreeConnection=" + tree + " , NetworkFile="+ file + " , bufPos=" + bufPos + ", size=" + siz+ " , filePos=" + filePos);

		// Check if the file is a directory

		if (file.isDirectory())
			throw new AccessDeniedException();

		// Read the file

		int rdlen = file.readFile(buf, siz, bufPos, filePos);

		// If we have reached end of file return a zero length read

		if (rdlen == -1)
			rdlen = 0;

		// Return the actual read length

		return rdlen;
	}

	/**
	 * Rename a file
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param oldName
	 *            Existing file name
	 * @param newName
	 *            New file name
	 * @exception IOException
	 */
	public void renameFile(SrvSession sess, TreeConnection tree,String oldName, String newName) throws java.io.IOException {

		log4j.debug("renameFile-- oldName=" + oldName + ", newName=" + newName);
		// Get the full path for the existing file and the new file name
		if (isPool(oldName))// 存储池中强制不允许创建文件
		{
			throw new AccessDeniedException();
		}
//		if(oldName.contains("离线文件--")){
//			throw new AccessDeniedException();
//		}
		DeviceContext ctx = tree.getContext();
		String oldPath = FileName.buildPath(ctx.getDeviceName(), oldName, null,java.io.File.separatorChar);
		String newPath = FileName.buildPath(ctx.getDeviceName(), newName, null,java.io.File.separatorChar);
		// Check if the current file/directory exists

		if (fileExists(sess, tree, oldName) == FileStatus.NotExist)
			throw new FileNotFoundException("Rename file, does not exist "+ oldName);

		// Check if the new file/directory exists

		if (fileExists(sess, tree, newName) != FileStatus.NotExist)
			throw new FileExistsException("Rename file, path exists " + newName);
		
		// Rename the file

		File oldFile = new File(oldPath);
		File newFile = new File(newPath);

		if (oldFile.renameTo(newFile) == false)
			throw new IOException("Rename " + oldPath + " to " + newPath+ " failed");
	}

	/**
	 * Seek to the specified point within a file
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param file
	 *            Network file
	 * @param pos
	 *            New file position
	 * @param typ
	 *            Seek type
	 * @return New file position
	 * @exception IOException
	 */
	public long seekFile(SrvSession sess, TreeConnection tree,NetworkFile file, long pos, int typ) throws java.io.IOException {

		// Check that the network file is our type

		return file.seekFile(pos, typ);
	}
	/**
	 * 离线文件迁移回来工具 
	 */
	@SuppressWarnings("unchecked")
	public long returnTime(Map map,String dName,SrvSession sess,FileOpenParams params) throws IOException
	{
			long etdotime = 0l;
			String sourcePoolId = (String) map.get("sourcePoolId");
			String targetPath   = (String) map.get("targetPath");
			String sourcePath   = (String) map.get("sourcePath");
			String moveRecordId = (String) map.get("moveRecordId");
			String sourceRollId = (String) map.get("sourceRollId");
			String targetPoolId = (String) map.get("targetPoolId");
			String targetType   = (String) map.get("targetType");
			StringBuffer qStr = new StringBuffer();
			int etEquipType = 0;
			if(!"".equals(targetPath) && moveBack==true){
				etEquipType = Integer.parseInt(targetType);
				int targetPolId = Integer.parseInt(targetPoolId);
				int sourceRolId = Integer.parseInt(sourceRollId);
				int moveRecodId = Integer.parseInt(moveRecordId);
				int sourcePolId = Integer.parseInt(sourcePoolId);
				BaseDao baseDao = (BaseDao) SpringUtil.getBean("baseDao");
				
				
				String sql3="select count(*) from jweb_comeback_record where path='"+sourcePath+"'";
				long id =baseDao.querlong(sql3);
				long newId = 0l;
				if(0==id){
					qStr.append(" insert into jweb_archive_log (userName,ip,create_date,agent_client,operation,target_Roll_Path,file_Name,status)");
					qStr.append(" values ('"+sess.getClientInformation().getUserName()+"','"+sess.getClientInformation().getClientAddress()+"','"+sf.format(new Date())+"','CIFS','r','"+targetPath+"','"+params.getPath()+"',"+0+")");
					baseDao.update(qStr.toString());
					
					String sql= "insert into jweb_comeback_record (pool_id,roll_id,path,move_record_id,record_date,status,back_num ) values("+sourcePolId+","+sourceRolId+","+"'"+sourcePath+"'"+","+moveRecodId+","+" ' "+sf.format(new Date())+" ' "+","+1+","+0+")";
					log4j.debug(sql.toString());
					baseDao.update(sql);
					newId = baseDao.querlong("select id from jweb_comeback_record where path="+"'"+sourcePath+"'"+"and status= 1");
					System.out.println(newId);
					
					String jobName = "comebackJobName"+newId;
					String triggerName = "comebackTrigger"+newId;
					String groupName = "comebackGroup"+newId;
					String sql2 = "update jweb_comeback_record set job_name= "+"'"+jobName+"'"+" , trigger_name="+"'"+triggerName+"'"+" , group_name="+"'"+groupName+"'"+" where id="+newId;
					log4j.debug(sql2);
					baseDao.update(sql2);
					StringBuffer qStr1 = new StringBuffer();
					Date date = new Date();
					date = new Date(date.getTime()+16000);
					qStr1.append(" insert into jweb_archive_log (userName,ip,create_date,agent_client,operation,target_Roll_Path,file_Name,status)");
					qStr1.append(" values ('"+sess.getClientInformation().getUserName()+"','"+sess.getClientInformation().getClientAddress()+"','"+sf.format(date)+"','CIFS','r','"+targetPath+"','"+params.getPath()+"',"+1+")");
					baseDao.update(qStr1.toString());
					
				}
				else if(1==id){
					String sql4 = "select status from jweb_comeback_record where path='"+sourcePath+"'";
					long statusId = baseDao.querlong(sql4);
					if(statusId==0){
						String sql5="update jweb_comeback_record set status = 1,back_num = 0 ,record_date="+" ' "+sf.format(new Date())+" ' "+" where path='"+sourcePath+"'";
						baseDao.update(sql5);
					}
					if(statusId == 1){
						String sql6 ="update jweb_comeback_record set  back_num = 0  where path='"+sourcePath+"'";
						baseDao.update(sql6);
						
					}
				}
			long stdnum = baseDao.querlong("select count(*)  from jweb_comeback_record where status=1");
			if(etEquipType == 2)
			{
				etdotime = stdnum * 180;
			}
			else if(etEquipType == 3 || etEquipType == 8)//传统磁带库，虚拟磁带库
			{
				etdotime = stdnum * 300;
			}
			else if(etEquipType == 4 || etEquipType == 5)//CD.DVD，热插拔
			{
				etdotime = stdnum * 180;
			}
			else if(etEquipType == 6)//线性磁带库
			{
				etdotime = stdnum * 360;
			}
			else if(etEquipType == 7)//传统磁带机
			{
				etdotime = stdnum * 300;
			}
		}
		return etdotime;
	}

	/**
	 * Set file information
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param name
	 *            File name
	 * @param info
	 *            File information to be set
	 * @exception IOException
	 */
	public void setFileInformation(SrvSession sess, TreeConnection tree,String name, FileInfo info) throws java.io.IOException {
		// Check if the modify date/time should be updated

		if (info.hasSetFlag(FileInfo.SetModifyDate)) {

			// Build the path to the file

			DeviceContext ctx = tree.getContext();
			String fname = FileName.buildPath(ctx.getDeviceName(), name, null,java.io.File.separatorChar);

			// Update the file/folder modify date/time

			File file = new File(fname);
			file.setLastModified(info.getModifyDateTime());
		}
	}

	/**
	 * Start a file search
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param searchPath
	 *            Search path, may include wildcards
	 * @param attrib
	 *            Search attributes
	 * @return SearchContext
	 * @exception FileNotFoundException
	 */
	public SearchContext startSearch(SrvSession sess, TreeConnection tree,String searchPath, int attrib) throws java.io.FileNotFoundException {

		log4j.debug("startSearch-- TreeConnection=" + tree + ", searchPath="+ searchPath + " , attrib=" + attrib);
		// Create a context for the new search

		// JavaFileSearchContext srch = new JavaFileSearchContext();
		JavaFileSearchContext2 srch = new JavaFileSearchContext2();

		// Create the full search path string
		String shareName = tree.getContext().getShareName();
		String path1 = tree.getContext().getDeviceName();

		String path = FileName.buildPath(tree.getContext().getDeviceName(),null, searchPath, java.io.File.separatorChar);

		try {

			// Map the path, this may require changing the case on some or all
			// path components
			path = mapPath(path);
			// Split the search path to get the share relative path

			String[] paths = FileName.splitPath(searchPath);
			// DEBUG

			if (Debug.EnableInfo && sess != null&& sess.hasDebug(SMBSrvSession.DBG_SEARCH))
				
				sess.debugPrintln("  Start search path=" + path + ", relPath="+ paths[0]);

			// Initialize the search
			if (paths[0].equalsIgnoreCase(DOS_SEPERATOR)|| paths[0].equalsIgnoreCase("/")|| paths[0].equalsIgnoreCase("")) {
				
				// 新增加的结构
				
				log4j.debug("\n获得存储池下的卷");
				try {
					String[] pathList = this.fileSysDao.findPathListByShareName(shareName);
					log4j.debug("**********************开始监听"+shareName);
					srch.initPoolSearch(path, attrib, pathList);
					//开始监听共享文件的增加
			}catch(FileNotFoundException e) 
				{
					log4j.error("目录不存在异常,path:"+path+"; "+e.getMessage());
				}
				catch (Exception e) {
					log4j.error("异常",e);
				}
			} else {
				srch.initSearch(path, attrib);// 原结构
			}
			srch.setRelativePath(paths[0]);
			return srch;
		} catch (PathNotFoundException ex) {
			throw new FileNotFoundException();
		}
	}

	/**
	 * Truncate a file to the specified size
	 * 
	 * @param sess
	 *            Server session
	 * @param tree
	 *            Tree connection
	 * @param file
	 *            Network file details
	 * @param siz
	 *            New file length
	 * @exception java.io.IOException
	 *                The exception description.
	 */
	public void truncateFile(SrvSession sess, TreeConnection tree,NetworkFile file, long siz) throws IOException {

		// Truncate or extend the file

		file.truncateFile(siz);
		file.flushFile();
	}

	/**
	 * Write a block of data to a file
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param file
	 *            Network file
	 * @param buf
	 *            Data to be written
	 * @param bufoff
	 *            Offset of data within the buffer
	 * @param siz
	 *            Number of bytes to be written
	 * @param fileoff
	 *            Offset within the file to start writing the data
	 */
	public int writeFile(SrvSession sess, TreeConnection tree,NetworkFile file, byte[] buf, int bufoff, int siz, long fileoff)throws java.io.IOException {

		log4j.debug("writeFile-- TreeConnection=" + tree + ", NetworkFile="+ file);
		// Check if the file is a directory

		if (file.isDirectory())
			throw new AccessDeniedException();
		// Write the data to the file

		file.writeFile(buf, siz, bufoff, fileoff);
		// Return the actual write length

		return siz;
	}

	/**
	 * Parse and validate the parameter string and create a device context for
	 * this share
	 * 
	 * @param shareName
	 *            String
	 * @param args
	 *            ConfigElement
	 * @return DeviceContext
	 * @exception DeviceContextException
	 */
	public DeviceContext createContext(String shareName, ConfigElement args)throws DeviceContextException {

		// Get the device name argument
		log4j.debug("createContext-- shareName=" + shareName+ " , ConfigElement=" + args);
		ConfigElement path = args.getChild("LocalPath");
		DiskDeviceContext ctx = null;

		if (path != null) {

			// Validate the path and convert to an absolute path

			File rootDir = new File(path.getValue());

			// Create a device context using the absolute path

			ctx = new DiskDeviceContext(rootDir.getAbsolutePath());

			// Set filesystem flags

			ctx.setFilesystemAttributes(FileSystem.CasePreservedNames+ FileSystem.UnicodeOnDisk);

			// If the path is not valid then set the filesystem as unavailable

			if (rootDir.exists() == false || rootDir.isDirectory() == false|| rootDir.list() == null) {

				// Mark the filesystem as unavailable

				ctx.setAvailable(false);
			}

			// Return the context

			return ctx;
		}

		// Required parameters not specified

		throw new DeviceContextException("LocalPath parameter not specified");
	}

	/**
	 * Connection opened to this disk device
	 * 
	 * @param sess
	 *            Server session
	 * @param tree
	 *            Tree connection
	 */
	public void treeOpened(SrvSession sess, TreeConnection tree) {
	}

	/**
	 * Connection closed to this device
	 * 
	 * @param sess
	 *            Server session
	 * @param tree
	 *            Tree connection
	 */
	public void treeClosed(SrvSession sess, TreeConnection tree) {
	}

	/**
	 * Return the global file creation date/time
	 * 
	 * @return long
	 */
	public final static long getGlobalCreateDateTime() {
		return _globalCreateDate;
	}

	public boolean isPool(String path) throws java.io.IOException {
		// Check if the directory exists, and it is a directory

		if (path.lastIndexOf("\\") > 2) {
			return false;
		} else {
			log4j.debug("存储池中，不允许[创建卷、重命名卷、删除卷]等操作;path=" + path);
			return true;
		}
	}
	
	public FileSystemDAO getFileSysDao() {
		return fileSysDao;
	}
	public void setFileSysDao(FileSystemDAO fileSysDao) {
		this.fileSysDao = fileSysDao;
	}
	public static boolean deletefile(String delpath) throws FileNotFoundException, 
	IOException { 
	try { 

	File file = new File(delpath); 
	if (!file.isDirectory()) { 
	file.delete(); 
	} 
	else if (file.isDirectory()) { 
	String[] filelist = file.list(); 
	for (int i = 0; i < filelist.length; i++) { 
	File delfile = new File(delpath + "/" + filelist[i]); 
	if (!delfile.isDirectory()) { 
	delfile.delete(); 
	System.out.println("删除文件成功"); 
	} 
	else if (delfile.isDirectory()) { 
	deletefile(delpath + "/" + filelist[i]); 
	} 
	} 
	file.delete(); 

	} 

	} 
	catch (FileNotFoundException e) { 
	System.out.println("deletefile() Exception:" + e.getMessage()); 
	} 
	return true; 
	} 
 /*	public  boolean deletefile(String delpath) throws Exception {  
		String[] path = delpath.split("#");
		String   filePath  = path[0];
		String 	 shareName = path[1];
		String fileRollPath = path[2] ;
		BaseDao baseDao = (BaseDao) SpringUtil.getBean("baseDao");
//		if(filePath.length()>10){
//			int jie = filePath.indexOf("\\",2);
//			fileRollPath = filePath.substring(1, jie);
//		}
		  try {  
			  
		   File file = new File(filePath);
		   StringBuffer qStr = new StringBuffer();
		   long deFileSize = 0L;
		   // 当且仅当此抽象路径名表示的文件存在且 是一个目录时，返回 true  
		   if (!file.isDirectory()) {
					// If the path does not exist then try and map it to a real path,there may be case differences
					deFileSize = file.length();
					StringBuffer qStr1 = new StringBuffer();
					qStr1.append("select hasSpace from jweb_backupmediaroll r where r.name="+ "'" + fileRollPath + "'")
					.append("and r.pool_id ="+ "("+"select pool_id from jweb_pool_sharing where name="+ "'" + shareName + "'" + ")");
					long totalSpace = baseDao.querlong(qStr1.toString());
					if (totalSpace != 0 && totalSpace > deFileSize) {
						qStr.append("update jweb_backupmediaroll r set r.hasSpace=r.hasSpace-"+ deFileSize + " where r.name=" + "'"+ fileRollPath + "'")
							.append("and r.pool_id ="+ "("+ "select pool_id from jweb_pool_sharing where name="+ "'" + shareName + "'" + ")");
						log4j.debug(qStr.toString());
						baseDao.update(qStr.toString());
						}
		    file.delete();  
		   } 
		   else if (file.isDirectory()) {  
		    String[] filelist = file.list();  
		    for (int i = 0; i < filelist.length; i++) {  
		     File delfile = new File(filePath + "/" + filelist[i]);  
		     if (!delfile.isDirectory()) {  
		    	 deFileSize = delfile.length();
					StringBuffer qStr1 = new StringBuffer();
					qStr1.append("select hasSpace from jweb_backupmediaroll r where r.name="+ "'" + fileRollPath + "'")
					.append("and r.pool_id ="+ "("+"select pool_id from jweb_pool_sharing where name="+ "'" + shareName + "'" + ")");
					long totalSpace = baseDao.querlong(qStr1.toString());
					if (totalSpace != 0 && totalSpace > deFileSize) {
						qStr.append("update jweb_backupmediaroll r set r.hasSpace=r.hasSpace-"+ deFileSize + " where r.name=" + "'"+ fileRollPath + "'")
							.append("and r.pool_id ="+ "("+ "select pool_id from jweb_pool_sharing where name="+ "'" + shareName + "'" + ")");
						log4j.debug(qStr.toString());
						baseDao.update(qStr.toString());
						}
		      delfile.delete();  
		      System.out .println(delfile.getAbsolutePath() + "删除文件成功");  
		     }
		    
		     else if (delfile.isDirectory()) {  
		      deletefile(filePath + "/" + filelist[i]);  
		     }  
		    }  
		    System.out.println(file.getAbsolutePath()+"删除成功");  
		    file.delete();  
		   }  
		  
		  } catch (FileNotFoundException e) {  
		   System.out.println("deletefile() Exception:" + e.getMessage());  
		  }  
		  return true;  
		 }  */
	
	public int OfffileExists(SrvSession sess, TreeConnection tree, String name,int createOptn) {
		log4j.debug("fileExists-- TreeConnection=" + tree + ", name=" + name);
		// Get the full path for the file

		DeviceContext ctx = tree.getContext();
		String filename = FileName.buildPath(ctx.getDeviceName(), name, null,java.io.File.separatorChar);

		// Check if the file exists, and it is a file

		File chkFile = new File(filename);
		if (chkFile.exists()) {

			// Check if the path is a file or directory

			if (chkFile.isFile())
				return FileStatus.FileExists;
			else
				return FileStatus.DirectoryExists;
		}


		// If the path does not exist then try and map it to a real path, there
		// may be case differences

		if (chkFile.exists() == false) {

			// Map the path to a real path

			try {
				String mappedPath = mapPath(ctx.getDeviceName(), name);
				if (mappedPath != null) {

					// Check if the path is a file

					chkFile = new File(mappedPath);
					File newFile = new File(filename);
					if (chkFile.exists()) {
						if((chkFile.exists()&&chkFile.getName().endsWith(".SPARSE.LFS")&&newFile.exists()==false)&&chkFile.getName().contains(newFile.getName())&&createOptn==68)
						{
							chkFile.delete();
							return FileStatus.NotExist;
						}
						else
						{
							if (chkFile.isFile())
								return FileStatus.FileExists;
							else
								return FileStatus.DirectoryExists;
						}
					}
				}
			} catch (FileNotFoundException ex) {
				log4j.error("FileNotFoundException name=" + name);
			} catch (PathNotFoundException ex) {
				log4j.error("PathNotFoundException name=" + name);
			}
		}

		// Path does not exist or is not a file

		return FileStatus.NotExist;
	}
}
