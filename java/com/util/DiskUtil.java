package com.util;

/*
 * Copyright (c) LinkApp Team All rights reserved.
 * 版权归LinkApp研发团队所有
 * 任何的侵权、盗版行为均将追究其法律责任
 * 
 * The LinkApp Project
 * http://www.linkapp.cn
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import com.google.gson.JsonArray;

/**
 * 网盘工具类
 * @author Leixiqing
 *
 */
public class DiskUtil {
	
	private static Logger logger = Logger.getRootLogger();
	public JsonArray fileArray =  new JsonArray();
	private static final String pathRegex = "[/\\\\]+";
	private static final Pattern pathPattern = Pattern.compile(pathRegex);
	
	/**
	 * 初始化
	 */

	/**
	 * 归档路径(获得用户的归档路径)
	 * @param userId
	 * @return
	 */
	public static String getArchiveFilePath(String md5)
	{
		String path = "";
		try
		{
			if(null == md5 || "".equals(md5)||"md5".equals(md5))
			{
				Random rd1 = new Random(100);
				md5 = "md5#"+rd1.nextInt(10);
			}
			java.util.Calendar c = java.util.Calendar.getInstance();
			java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy/MM/dd/HHmmssSSSS");		
			if(AESUtil.encrypt)
			{
				path = "/archive/db/"+f.format(c.getTime())+"_"+md5+".LFS";//加密存储
			}
			else
			{
				path = "/archive/db/"+f.format(c.getTime())+"_"+md5+".LFN";//非加密存储
			}
			path = DiskUtil.replacePath(path);
		}catch (Exception e) {
			e.printStackTrace();
		}
		return path;
	}
	
	/**
	 * 外链打包文件路径
	 * @param md5
	 * @return
	 */
	public static String getOutLinkZip(String md5)
	{
		String path = "";
		try
		{
			if(null == md5 || "".equals(md5))
			{
				Random rd1 = new Random(100);
				md5 = "#"+rd1.nextInt(10);
			}
			java.util.Calendar c = java.util.Calendar.getInstance();
			java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy/MM/dd/HHmmssSSSS");		
			path = "/archive/zips/"+f.format(c.getTime())+"_"+md5+".LFS";
			path = DiskUtil.replacePath(path);
		}catch (Exception e) {
			e.printStackTrace();
		}
		return path;
	}
	
	/**
	 * 根据路径创建一系列的目录
	 * 
	 * @param path
	 */
	public static boolean mkDirectory(String path,int userId) {
		File file = null;
		try {
			file = new File(path);
			if (!file.exists()) {
				boolean flag = file.mkdirs();
				return flag;
			}
		} catch (RuntimeException e) {
			e.printStackTrace();
		} finally {
			file = null;
		}
		return false;
	}
	
	/**
	 * 获得文件后缀
	 * @param fileName
	 * @return
	 */
	public static String getExt(String fileName) {
		try {
			if (fileName != null && fileName.length() > 0) {
				int lastDotAt = fileName.lastIndexOf(".");
				if (lastDotAt != -1) {
					fileName = fileName.substring(lastDotAt + 1, fileName.length());
				} else {
					fileName = "";
				}
				fileName = fileName.toLowerCase();
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
		return fileName;
	}
	/**
	 * 修改文件后缀
	 * @param fileName
	 * @return
	 */
	public static String reExt(String fileName, String newExt) {
		try {
			if (fileName != null && fileName.length() > 0) {
				int lastDotAt = fileName.lastIndexOf(".");
				if (lastDotAt != -1) {
					fileName = fileName.substring(0,lastDotAt)+"."+newExt;
				} else {
					fileName = "";
				}
				fileName.toLowerCase();
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
		return fileName;
	}
	/**
	 * 拥有的子目录数量
	 * @param folder
	 * @param onlyDirectory true：只获得文件夹,flase:获得所有信息
	 * @return
	 */
	public static int hasSubFiles(File folder,boolean onlyDirectory) {
		int count = 0;		
		try {
			if (folder != null && folder.isDirectory()) {
				File fileArray[] = folder.listFiles();
				for (int i = 0; i < fileArray.length; i++) {
					File file = fileArray[i];
					if (onlyDirectory && !file.isDirectory()) {
						continue;
					}
					count ++;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return count;
	}
	
	/**
	 * 复制单个文件
	 * @param resFile
	 * @param objFile
	 * @throws IOException
	 */
	public static void copySingeFile(File resFile, File objFile) throws IOException {
        if (!resFile.exists()) return;
        if (!objFile.getParentFile().exists()) objFile.getParentFile().mkdirs();//创建目录
        if(resFile.getAbsolutePath().equalsIgnoreCase(objFile.getAbsolutePath()))
		{
			//文件相同，不复制
			return;
		}
        else if (resFile.isFile()) {
        		if(objFile.exists())
        		{
//        			Date nowDate = new Date();
//        			File bakFile = new File(objFile.getAbsolutePath()+"_"+nowDate.getTime()+".LFS");
//        			DiskUtil.copy(objFile, bakFile);
//        			logger.error("普通错误:存在同名"+objFile.getPath());
//        			System.out.println("出同这种情况说明程序有错误：存在同名，但内容不同的文件，覆盖原文件"+objFile.getPath()+"\n已经备份文件："+bakFile.getPath());
        			objFile.delete();
        		}
        		InputStream ins = null; 
        		FileOutputStream outs = null;
        		try
        		{
        			 //复制文件到目标地
                   ins = new FileInputStream(resFile);
                   outs = new FileOutputStream(objFile);
                   byte[] buffer = new byte[1024 * 512];
                   int length;
                   while ((length = ins.read(buffer)) != -1) {
                            outs.write(buffer, 0, length);
                    }
                    ins.close();
                    outs.flush();
                    outs.close();
        		}
        		catch(Exception e)
        		{
        			e.printStackTrace();
        		}finally {
        			try {
        				if(null != ins)
        					ins.close();
        				if(null != outs)
        					outs.close();
        			} catch (Exception e) {
        				logger.error(e.getMessage());
        			}
        		}
         } else {
                return ;
         }
}
	
	/**
	   * 复制文件（夹）到一个目标文件夹
	   *
	   * @param resFile             源文件（夹）
	   * @param objFolderFile 目标文件夹
	   * @throws IOException 异常时抛出
	   */
	public static void copy(File resFile, File objFolderFile) throws IOException {
	          if (!resFile.exists()) return;
//	          if (!objFolderFile.exists()) objFolderFile.mkdirs();
	          if (resFile.isFile()) {
	                   File objFile = new File(objFolderFile.getPath() + File.separator + resFile.getName());
	                   InputStream ins = null;
	                   FileOutputStream outs = null;
	                   try
	                   {
	                	 //复制文件到目标地
		                  ins = new FileInputStream(resFile);
		                  outs = new FileOutputStream(objFile);
		                  byte[] buffer = new byte[1024 * 512];
		                  int length;
		                  while ((length = ins.read(buffer)) != -1) {
		                           outs.write(buffer, 0, length);
		                   }
		                   ins.close();
		                   outs.flush();
		                   outs.close();
	                   }
	                   catch(Exception e)
	                   {
	                	   e.printStackTrace();
	                   }finally {
	           			try {
	        				if(null != ins)
	        					ins.close();
	        				if(null != outs)
	        					outs.close();
	        			} catch (Exception e) {
	        				logger.error(e.getMessage());
	        			}
	        		}	                  
	           } else {
	                   String objFolder = objFolderFile.getPath() + File.separator + resFile.getName();
	                   File _objFolderFile = new File(objFolder);
	                   _objFolderFile.mkdirs();
	                  for (File sf : resFile.listFiles()) {
	                           copy(sf, new File(objFolder));
	                   }
	           }
	 }
	
	/**
	   * 删除文件（夹）[真正删除]
	   *
	   * @param file 文件（夹）
	   */
	public static void delete(File file) {
		if (null ==file || !file.exists()) return;
		if (file.isFile()) {
				file.delete();
		} else {
			if(null != file.listFiles() && file.listFiles().length>0)
			{
				for (File f : file.listFiles()) {
					delete(f);
				}
			}
			file.delete();
		}
	}
	
	
	/**
	 * 判断文件名是否已经存在，如果存在则在后面家(n)的形式返回新的文件名，否则返回原始文件名 例如：已经存在文件名 log4j.htm
	 * 则返回log4j(1).htm
	 * 
	 * @param fileName
	 *            文件名
	 * @param dir
	 *            判断的文件路径
	 * @return 判断后的文件名
	 */
	public static String checkFileName(String fileName, String dir) {
		boolean isDirectory = new File(dir + fileName).isDirectory();
		if (isFileExist(fileName, dir)) {
			int index = fileName.lastIndexOf(".");
			StringBuffer newFileName = new StringBuffer();
			String name = isDirectory ? fileName : fileName.substring(0, index);
			String extendName = isDirectory ? "" : fileName.substring(index);
			int nameNum = 1;
			while (true) {
				newFileName.append(name).append("(").append(nameNum).append(")");
				if (!isDirectory) {
					newFileName.append(extendName);
				}
				if (isFileExist(newFileName.toString(), dir)) {
					nameNum++;
					newFileName = new StringBuffer();
					continue;
				}
				return newFileName.toString();
			}
		}
		return fileName;
	}
	
	/**
	 * 判断文件是否存在
	 * 
	 * @param fileName
	 * @param dir
	 * @return
	 */
	public static boolean isFileExist(String fileName, String dir) {
		File files = new File(dir + fileName);
		return (files.exists()) ? true : false;
	}
	
	/**
	 * 替换路径(替换成绝对路径)
	 * @param path
	 * @return
	 */
	public static String replacePath(String path)
	{
		try
		{
			if(StringUtils.isNotEmpty(path))
			{
				path = path.trim();
				Matcher m = pathPattern.matcher(path);
				// 处理uri,将多/替换成单/ "/m328/index.htm"类型链接
				path = m.replaceAll("/");
				if(!path.startsWith("/"))
				{
					path = "/"+path;
				}
				if(path.length()>1 && path.endsWith("/"))//除根目录外，其它目录。后面不留“/”
				{
					path = path.substring(0, path.length()-1);
				}
			}
			else
			{
				path = "/";
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		return path;
	}
	
	/**
	 * 替换卷路径(替换成绝对路径)
	 * @param path
	 * @return
	 */
	public static String replaceRollPath(String path)
	{
		try
		{
			if(StringUtils.isNotEmpty(path))
			{
				path = path.trim();
				Matcher m = pathPattern.matcher(path);
				// 处理uri,将多/替换成单/ "/m328/index.htm"类型链接
				path = m.replaceAll("/");
				if(!path.startsWith("/"))
				{
					path = "/"+path;
				}
				if(path.length()>1 && !path.endsWith("/"))//除根目录外，其它目录。后面不留“/”
				{
					path = path+"/";
				}
			}
			else
			{
				logger.error("卷路径为空:path="+path);
				path = "/";
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		return path;
	}
	
	/**
	 * 转换成相对路径(替换成绝对路径，不以/开头)
	 * @param path
	 * @return
	 */
	public static String relativelyPath(String path)
	{
		try
		{
			if(StringUtils.isNotEmpty(path))
			{
				path = path.trim();
				Matcher m = pathPattern.matcher(path);
				// 处理uri,将多/替换成单/ "/m328/index.htm"类型链接
				path = m.replaceAll("/");
				if(path.startsWith("/"))
				{
					path = path.substring(1);
				}
				if(path.length()>1 && path.endsWith("/"))//除根目录外，其它目录。后面不留“/”
				{
					path = path.substring(0, path.length()-1);
				}
			}
			else
			{
				path = ".";
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		return path;
	}
	
	/**
	 * 替换路径
	 * @param path
	 * @return
	 */
	public static String replaceName(String name)
	{
		try
		{
			Matcher m = pathPattern.matcher(name);
			// 处理uri,将多/替换成单/ "/m328/index.htm"类型链接
			name = m.replaceAll("/");
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		return name;
	}
	
	public static String findFristPath(String path)
	{
		String fristPath = "";
		path = replacePath(path);
//		System.out.println("11path:"+path);
		int pos = path.indexOf("/");
//		System.out.println("22path:"+path);
		if(pos>-1)
		{
			path = path.substring(1);
			int posend = path.indexOf("/");
			if(posend>-1)
			{
				fristPath = path.substring(0, posend);
			}
			else
			{
				fristPath = path;
			}
		}
		else
		{
			fristPath = path;
		}
//		System.out.println("fristPath:"+fristPath+"\n");
		return fristPath;
	}
	
	
	/**
	 * 获得归档文件占用空间
	 * @return
	 */
//	public static long getArchiveUsedspace()
//	{
//		long usedSpace = 0;
//		String archivePath = getRootPath()+"/archive/";//归档目录
//		Map<String,Long> space = FileUtil.getDirSpace(new File(archivePath));
//		usedSpace = Long.parseLong(space.get("totalSize").toString());
//		return usedSpace;
//	}
	
	/**
	 * 获得文件内容（提取前2500字）
	 * @param fileName
	 * @param filePath
	 * @return
	 */
	public static String getFileContent(String fileName,String filePath)
	{
		String content = "";
		FileParser fp = new FileParser(new File(filePath),fileName);//提取内容
		content = fp.getDescription();
		return content;
	}
	
	/**
	 * 获得文件内容（可指定返回的内容长度）
	 * @param fileName
	 * @param filePath
	 * @param length
	 * @return
	 */
	public static String getFileContent(String fileName,String filePath,int length)
	{
		String content = "";
		FileParser fp = new FileParser(new File(filePath),fileName);//提取内容
		content = fp.getDescription(length);
		return content;
	}
	
	/**
	 * 获得文档类型
	 * @param suffix
	 * @return
	 */
	public static String getDocType(String suffix) {
		//PowerPoint.Presentation   PowerPoint.Show
		return (suffix.contains("doc") || suffix.contains("wps")) ? "Word.Document" : (suffix.contains("xls") || suffix.contains("et")) ? "Excel.Sheet" : "PowerPoint.Show";
	}
	
	/**
	 * 检查当前md5文件是否存在  如不存在 重新创建
	 * @param path
	 * @return
	 */
	public static boolean checkFileExist(String path ){
		boolean isExist = true ;
		File file = new File(path);
		if(file.exists()){
			return isExist;
		}
		return false;
	}
	
	/**
	 * 本类测试
	 * @param arg
	 */
	public static void main(String arg[])
	{
//		String diskPath = getDiskStoreDir(1);
//		System.out.println("diskPath :"+diskPath );
		try {
//			ListFiles lFiles = new ListFiles("E:/files/disk0/3/",0);
//			ArrayList<File> al = lFiles.getFilelist();
//			for(File f:al)
//			{
//				System.out.println("rs :"+f.getAbsolutePath());
//				System.out.println("hs :"+f.hashCode());
//			}	
//			
//			String rootPath = getSession().getServletContext().getRealPath("/");
			
////		fosm = new FileOutputStream(rootPath + getPath() + "\\" + zipName);
////		zosm = new ZipOutputStream(fosm);
////		for (String path : paths) {
////			file = new File(rootPath + path);
////			MyUtils.compressionFiles(zosm, file, getPath());
////			file = null;
////		}
////		return MyUtils.getZipInputStrean(zosm, file, getPath());
//			String ext = getExt("1.txt");
//			String allExt = DocViewer.getAllSupportExt();
//			System.out.println(allExt);
//			if(allExt.indexOf(","+ext+",")>-1)
//			{
//				System.out.println("indexOf");
			
			findFristPath("\\\\\\我的文件//ss");
			findFristPath("\\\\我的文件\\sss\\sss");
			findFristPath("\\\\\\我的文件\\s");
			findFristPath("\\\\\\我的文件/ww");
			findFristPath("////我的文件/ww/www");
			findFristPath("\\\\\\我的文件/w/w/w");
			findFristPath("\\\\\\我的文件\\w\\w\\w\\w\\w\\w");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
