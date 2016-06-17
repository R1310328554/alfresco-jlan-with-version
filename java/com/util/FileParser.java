package com.util;


import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.log4j.Logger;
import org.apache.tika.exception.TikaException;

import com.file.TikaParser;

public class FileParser extends TikaParser{
	private Logger logger = Logger.getLogger(this.getClass().getName());
	private String content;
	private static final String regEx = "[^\\d\\w\\s\\p{Punct}\u4E00-\u9FA5\uFE30-\uFFA0]";
	private static final String allExt = ",pdf,asc,txt,doc,docx,ppt,pptx,xps,wps,wpd,wiki,odf,ods,sxc,potm,ppsm,ppsx,mpp,pptm,odt,csv,tsv,odp,svg,et,ett,dps,dpt,xls,xlsx,xlsb,rtf,dotm,dwg,mbox,chm,htm,html,mhtml,msg,rss,xml,sxw,tmp,,";
	private Pattern p = Pattern.compile(regEx);
	    
	public FileParser() {
		logger.debug("into FileParser");
	}
	
	/**
	 * 直接解析文件
	 * @param file
	 * @param fileName
	 */
	public FileParser(File file,String fileName)
	{
		try {
			String ext = DiskUtil.getExt(fileName).toLowerCase();
//			String allExt = DocViewer.getAllSupportExt();
			if(allExt.indexOf(","+ext+",")>-1 && !fileName.startsWith("~$"))
			{
				if(null != file && file.exists() && file.length()>0)
				{
					String content = tika.parseToString(file);
					content = StringUtil.filterChars(content);
					Matcher m = p.matcher(content); 
					content = m.replaceAll("");  
					this.setContent(content);
				}
			}
		}catch(org.apache.poi.EncryptedDocumentException e){
			logger.error("org.apache.poi.EncryptedDocumentException,加密文档无法提取内容："+fileName);
		}catch(org.apache.tika.exception.EncryptedDocumentException e){
			logger.error("org.apache.tika.exception.EncryptedDocumentException,加密文档无法提取内容："+fileName);
		} catch (IOException e) {
			logger.error("提取内容IOException,fileName="+fileName);
		} catch (TikaException e) {
			logger.error("提取内容TikaException,fileName="+fileName);//CB4C0445.tmp
		} catch(Exception e)
		{
			logger.error("提取内容Exception,fileName="+fileName);
		}
	}
	
	/**
	 * 直接解析文件
	 * @param file
	 * @param fileName
	 */
	public FileParser(File file)
	{
		try {
			if(null != file && file.exists() && file.length()>0)
			{
				String content = tika.parseToString(file); 
				this.setContent(content);
			}
		}catch(org.apache.poi.EncryptedDocumentException e){
			logger.error("org.apache.poi.EncryptedDocumentException,加密文档无法提取内容：");
		}catch(org.apache.tika.exception.EncryptedDocumentException e){
			logger.error("org.apache.tika.exception.EncryptedDocumentException,加密文档无法提取内容：");
		} catch (IOException e) {
			logger.error("提取内容IOException"+e.getMessage());
		} catch (TikaException e) {
			logger.error("提取内容TikaException,fileName="+e.getMessage());
		} catch(Exception e)
		{
			logger.error("提取内容Exception,fileName="+e.getMessage());
		}
	}

	/**
	 * 返回文件内容
	 * @return
	 */
	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	/**
	 * 返回描述信息
	 * @return
	 */
	public String getDescription() {
		String desc = "";
		if(null != content)
		{
			desc = StringUtil.filterSpace(content);
			desc = StringUtil.filterSymbols(desc);
			if(null != desc && desc.length()>2500)
			{
				desc = desc.substring(0,2500)+"...";
			}
		}
		return desc;
	}
	
	/**
	 * 返回指定长度的描述信息
	 * @return
	 */
	public String getDescription(int length) {
		String desc = "";
		if(length <= 0) length = 200;
		if(null != content)
		{
			desc = StringUtil.filterSpace(content);
			if(null != desc && desc.length() > length)
			{
				desc = desc.substring(0,length);
			}
		}
		return desc;
	}

	/**
	 * 测试类
	 * @param 
	 */
	public static void main(String[] args) {
//		FileParser fp = new FileParser(new File("d:/我的文档/詹承乾.doc"),"测试文本4M.txt");
		FileParser fp = new FileParser(new File("/F:/日志/黄港同/err_tike/gs-20140904-140003.tmp"),"gs-20140904-140003.tmp");
		
//		System.out.println("描述："+fp.getDescription());
//		System.out.println("\n\n\n-----------------------");
//		System.out.println("内容："+fp.getContent());
		System.out.println("\n\n\n==================================");
		fp = new FileParser(new File("/F:/日志/黄港同/err_tike/gs-20140903-140002.tmp"),"gs-20140903-140002.tmp");
		fp = new FileParser(new File("/F:/日志/黄港同/err_tike/_file_state._gs"),"_file_state._gs");
		fp = new FileParser(new File("/F:/日志/黄港同/err_tike/FolderActions-2014-09.log"),"FolderActions-2014-09.log");
		fp = new FileParser(new File("/F:/日志/黄港同/err_tike/gs-20140903-140002.tmp"),"gs-20140903-140002.tmp");
		fp = new FileParser(new File("/F:/日志/黄港同/err_tike/gs-20140904-140001.tmp"),"gs-20140904-140001.tmp");
		fp = new FileParser(new File("/F:/日志/黄港同/err_tike/gs-20140904-140002.tmp"),"gs-20140904-140002.tmp");
		fp = new FileParser(new File("/F:/日志/黄港同/err_tike/gs-20140904-140003.tmp"),"gs-20140904-140003.tmp");
		fp = new FileParser(new File("/F:/日志/黄港同/err_tike/gs-20140904-140004.tmp"),"gs-20140904-140004.tmp");
		
		System.out.println("描述："+fp.getDescription());
		System.out.println("\n\n\n-----------------------");
		System.out.println("内容："+fp.getContent());
	}
}
