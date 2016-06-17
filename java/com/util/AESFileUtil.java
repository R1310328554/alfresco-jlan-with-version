package com.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.Key;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

/**
 * 对文件加密/解密和压缩/解压缩对象类
 * AES压缩加密/解压缩解密，网上一般用base64来对byte[]编码,其实不需要，指定AES/CBC/PKCS5Padding
 * 来指定加密解密时候位数不对的情况下，用pkcs5padding来附加位数，不过这个时候读解密的文件的时候，要多读16位的验证位就不会报异常
 * @author Leixiqing
 *
 */
public class AESFileUtil {
	
	private static Logger logger = Logger.getRootLogger();
	
	public static final int eSize = 1048576;//1024*1024，1M
	public static final int dSize = 1048592;//1024*1024+16
	public static Key privateKey;
	public static Key headerKey = new SecretKeySpec("fe1b9a223ba095ff".getBytes(), "AES");
	
	/**
	 * 初始化
	 */
	static
	{
//		if(null == headerKey)
//		{
//			String strPass1 =  MD5Util.hash("LYECrypt");
//			String strPass16 = strPass1.substring(0, 8);
//			String strKey = MD5Util.hash(strPass1);
//			String strKey16 = strKey.substring(24,32);
//			strKey16 += strPass16;
//			System.out.println(strKey16);
//			headerKey = new SecretKeySpec(strKey16.getBytes(), "AES");
//		}
	}
	
	/**
	 * 加密字节
	 * @param b
	 * @param pos
	 * @param length
	 * @return
	 * @throws Exception
	 */
	public static byte[] encryptByte(byte[] b,int pos,int length,Key privateKey) throws Exception
	{
		Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, privateKey);
		int len = b.length;
		if(length>0)
		{
			len = length;
			//加密不够补位
			int nTail =length%16;
			int nAppend =0;
			if (nTail > 0)
	        {
	            nAppend = 16 - nTail;
	            len = len+nAppend;
	            for(int i=0;i<nAppend;i++)
	            {
	            	b[len+i] = 0;
	            }
	        }
		}
		return cipher.doFinal(b, pos, len);		
	}
	
	/**
	 * 解密字节
	 * @param b
	 * @param pos
	 * @param length
	 * @return
	 * @throws Exception
	 */
	public static byte[] decryptByte(byte[] b,int pos,int length,Key privateKey) throws Exception
	{
		Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
		cipher.init(Cipher.DECRYPT_MODE, privateKey);
		int len = b.length;
		if(length>0)
		{
			len = length;
		}		
		return cipher.doFinal(b, pos, len);
	}
	
	/**
	 * 把文件inFile加密后存储为outFile
	 * 
	 * @param srcFile
	 * @param destFile
	 */
	public static boolean encryptFile(File inFile, File outFile,String key) throws Exception {
		boolean flag = false;
		try
		{
			privateKey = new SecretKeySpec(key.getBytes(), "AES");
		}
		catch(Exception e)
		{
			logger.error("软件密钥错误！不能加密文件");
			return false;
		}
		FileInputStream fis = null;
		FileOutputStream fos = null;
		try
		{
			if(inFile.exists())
			{
				if (!outFile.exists()) outFile.getParentFile().mkdirs();//建立目录
				
				fis = new FileInputStream(inFile);
				fos = new FileOutputStream(outFile);
				byte[] b = new byte[eSize];
				int len;
				while ((len = fis.read(b)) != -1) {
					fos.write(encryptByte(b,0,len,privateKey));
				}
				fos.close();
				fis.close();
				
				flag = true;//成功
			}
			else
			{
				logger.error("encryptFile ERROR; "+inFile.getAbsolutePath()+" not exists");
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			if(null != fis)
			{
				fis.close();
			}
			if(null != fos)
			{
				fos.close();
			}
		}
		return flag;
	}

	/**
	 * 把文件inFile解密后存储为outFile
	 * 
	 * @param srcFile
	 * @param destFile
	 * @param privateKey
	 * @throws Exception
	 */
	public static boolean decryptFile(File inFile, File outFile,String key) throws Exception {
		boolean flag = false;
		try
		{
			privateKey = new SecretKeySpec(key.getBytes(), "AES");
		}
		catch(Exception e)
		{
			logger.error("软件密钥错误！不能解密文件");
			return false;
		}
		FileInputStream fis = null;
		FileOutputStream fos = null;
		try
		{
			if(inFile.exists())
			{
				if (!outFile.exists()) outFile.getParentFile().mkdirs();//建立目录
				
				fis = new FileInputStream(inFile);
				fos = new FileOutputStream(outFile);
				byte[] b = new byte[dSize];
				int len;
				while ((len = fis.read(b)) != -1) {
					fos.write(decryptByte(b,0,len,privateKey));
				}
				fos.close();
				fis.close();
				
				flag = true;
			}
			else
			{
				logger.error("decryptFile ERROR; "+inFile.getAbsolutePath()+" not exists");
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			if(null != fis)
			{
				fis.close();
			}
			if(null != fos)
			{
				fos.close();
			}
		}
		return flag;
	}
	
	/**
	 * 把文件inFile加密后存储为outFile
	 * @param inFile 原文件
	 * @param outFile 加密文件
	 * @param fileName 文件名
	 * @param KeyId 密钥id
	 * @param ver 客户端版本
	 * @param verName 客户端版本名称
	 * @param level 加密级别
	 * @param key 密钥value
	 * @return
	 * @throws Exception
	 */
	public static boolean encryptLYEFile(File inFile, File outFile,boolean isDecrypt,String fileName,long fileSize,long KeyId,int ver,String verName,int level,String pwd,String key) throws Exception {
		boolean flag = false;
		try
		{
//			if(null != pwd)
//			{
//				key = MD5Util.hash(MD5Util.hash(pwd)).substring(16);
//			}
			privateKey = new SecretKeySpec(key.getBytes(), "AES");
			if(StringUtils.isEmpty(pwd))
			{
				pwd = key;
			}
		}
		catch(Exception e)
		{
			logger.error("软件密钥错误！不能加密文件");
			return false;
		}
		FileInputStream fis = null;
		FileOutputStream fos = null;
		try
		{
			if(inFile.exists())
			{
				if (!outFile.exists()) outFile.getParentFile().mkdirs();//建立目录
				
				fis = new FileInputStream(inFile);
				
				fos = new FileOutputStream(outFile);
				
				String sTag = padRight("LYECrypt",2);
				String sInfo = "<LYEINFO name=\""+fileName+"\" size=\""+fileSize+"\" keyID=\""+KeyId+"\" nVer=\""+ver+"\" szVer=\""+verName+"\" nLeve=\""+level+"\" key1=\""+MD5Util.hash(pwd)+"\" key2=\""+MD5Util.hash(MD5Util.hash(pwd))+"\"/>";
//				//加密不够补位
				int nTail =sInfo.getBytes("UTF-8").length%16;
				int nAppend =0;
				if (nTail > 0)
		        {
		            nAppend = 16 - nTail;
		            sInfo = padRight(sInfo,nAppend);
		        }
				byte[] bInfo = encryptByte(sInfo.getBytes("UTF-8"),0,sInfo.getBytes("UTF-8").length,headerKey);//加密后的info信息
				String sInfoLen = ""+bInfo.length;
				sInfoLen = padRight(sInfoLen,20-sInfoLen.length());
				fos.write(sTag.getBytes());
				fos.write(sInfoLen.getBytes());
				fos.write(bInfo);		
				if(isDecrypt)
				{
					byte[] b = new byte[eSize];
					byte[] db = new byte[AESUtil.dSize];
					int dlen;
					while ((dlen = fis.read(db)) != -1) {						
						byte eb[] = AESUtil.decryptByte(db, 0, dlen);
						if(eb.length%16==0)
						{
							fos.write(encryptByte(eb,0,eb.length,privateKey));
						}
						else
						{
							for(int i=0;i<eb.length;i++)
							{
								b[i] = eb[i];
							}
							fos.write(encryptByte(b,0,eb.length,privateKey));
						}
					}
				}
				else
				{
					byte[] b = new byte[eSize];
					int len;
					while ((len = fis.read(b)) != -1) {
						fos.write(encryptByte(b,0,len,privateKey));
					}
				}
				
				fos.close();
				fis.close();
				
				flag = true;//成功
			}
			else
			{
				logger.error("encryptFile ERROR; "+inFile.getAbsolutePath()+" not exists");
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			if(null != fis)
			{
				fis.close();
			}
			if(null != fos)
			{
				fos.close();
			}
		}
		return flag;
	}
	
	/**
	 * 把文件inFile解密后存储为outFile
	 * 
	 * @param srcFile
	 * @param destFile
	 * @param privateKey
	 * @throws Exception
	 */
	public static boolean decryptLYEFile(File inFile, File outFile,String key) throws Exception {
		boolean flag = false;
		try
		{
			privateKey = new SecretKeySpec(key.getBytes(), "AES");
		}
		catch(Exception e)
		{
			logger.error("软件密钥错误！不能解密文件");
			return false;
		}
		FileInputStream fis = null;
		FileOutputStream fos = null;
		try
		{
			if(inFile.exists())
			{
				if (!outFile.exists()) outFile.getParentFile().mkdirs();//建立目录
				
				fis = new FileInputStream(inFile);
				fos = new FileOutputStream(outFile);
				byte[] bTag = new byte[10];
				byte[] bInfoLen = new byte[20];
				fis.read(bTag);
				fis.read(bInfoLen);
				String fTag = new String(bTag).trim();
				String sInfoLen = new String(bInfoLen).trim();
				int infoLen = Integer.parseInt(sInfoLen.trim());
				
				byte[] bInfo = new byte[infoLen];
				fis.read(bInfo);
				
				byte[] decInfo = decryptByte(bInfo,0,bInfo.length,headerKey);
				String sInfo = new String(decInfo,"UTF-8");
				long fileSize = 0;
				long keyId = 0;
				if (null != sInfo) {
					Pattern pattern;
					Matcher matcher;
					pattern = Pattern.compile("size=\"(.*?)\"");
					matcher = pattern.matcher(sInfo);
					if (matcher.find()) {
						fileSize = Long.parseLong(matcher.group(1).trim());
					}
					pattern = Pattern.compile("keyID=\"(.*?)\"");
					matcher = pattern.matcher(sInfo);
					if (matcher.find()) {
						keyId = Long.parseLong(matcher.group(1).trim());
					}
				}
				
				byte[] b = new byte[dSize];
				int len;
				while ((len = fis.read(b)) != -1) {
					fos.write(decryptByte(b,0,len,privateKey));
				}
				fos.close();
				fis.close();
				
				flag = true;
			}
			else
			{
				logger.error("decryptFile ERROR; "+inFile.getAbsolutePath()+" not exists");
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			if(null != fis)
			{
				fis.close();
			}
			if(null != fos)
			{
				fos.close();
			}
		}
		return flag;
	}
	
	/**
	  * 字符右补位
	  * @param str  原字符串
	  * @param len  补位长度
	  * @param alexin  补位字符
	  * @return  目标字符串
	  */
	 public static String padRight(String str,int len){
		 char alexin = 0;
		for(int i=0;i<len;i++){
			 str = str+alexin;
		 }
		return str;
	 }
	 
	/**
	 * 获得文件KeyId
	 * @param inFile
	 * @return
	 * @throws Exception
	 */
	public static long getLYEFileKeyId(File inFile,boolean isDecrypt) throws Exception {
		long keyId = 0;
		FileInputStream fis = null;
		try
		{
			if(inFile.exists() && inFile.length()>0)
			{
				if(isDecrypt)
				{
					byte[] decryptByte = AESUtil.decryptFileHeaderByte(inFile);
					byte[] bTag = new byte[10];
					for(int i=0;i<10;i++)
					{
						bTag[i]=decryptByte[i];
					}
					byte[] bInfoLen = new byte[20];
					for(int i=0;i<20;i++)
					{
						bInfoLen[i]=decryptByte[10+i];
					}
					String sInfoLen = new String(bInfoLen).trim();
					int infoLen = Integer.parseInt(sInfoLen.trim());					
					byte[] bInfo = new byte[infoLen];
					for(int i=0;i<infoLen;i++)
					{
						bInfo[i] = decryptByte[30+i];
					}
					byte[] decInfo = decryptByte(bInfo,0,bInfo.length,headerKey);
					String sInfo = new String(decInfo,"UTF-8");
					if (null != sInfo) {
						Pattern pattern;
						Matcher matcher;
						pattern = Pattern.compile("keyID=\"(.*?)\"");
						matcher = pattern.matcher(sInfo);
						if (matcher.find()) {
							keyId = Long.parseLong(matcher.group(1).trim());
						}
					}
					
				}
				else
				{
					fis = new FileInputStream(inFile);
					byte[] bTag = new byte[10];
					byte[] bInfoLen = new byte[20];
					fis.read(bTag);
					fis.read(bInfoLen);
					String fTag = new String(bTag).trim();
					String sInfoLen = new String(bInfoLen).trim();
					int infoLen = Integer.parseInt(sInfoLen.trim());
					
					byte[] bInfo = new byte[infoLen];
					fis.read(bInfo);
					
					byte[] decInfo = decryptByte(bInfo,0,bInfo.length,headerKey);
					String sInfo = new String(decInfo,"UTF-8");
					if (null != sInfo) {
						Pattern pattern;
						Matcher matcher;
						pattern = Pattern.compile("keyID=\"(.*?)\"");
						matcher = pattern.matcher(sInfo);
						if (matcher.find()) {
							keyId = Long.parseLong(matcher.group(1).trim());
						}
					}
				}
			}
			else
			{
				logger.error("decryptFile ERROR; "+inFile.getAbsolutePath()+" not exists");
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			if(null != fis)
			{
				fis.close();
			}
		}
		return keyId;
	}
	 
	 
	public static void main(String args[]) throws Exception {
//		String pwd = "123";
//		String keyValue = MD5Util.hash(MD5Util.hash(pwd)).substring(16);
//		
////		long a = System.currentTimeMillis();
//		String f = "/c:/lye/text.txt";
////		//加密
////		AESFileUtil.encryptFile(new File(f), new File(f+".lye"));
////		//解密
////		AESFileUtil.decryptFile(new File(f+".lye"), new File(f+".lye."+DiskUtil.getExt(f)));
//		
////		System.out.println(System.currentTimeMillis() - a+" ms");
//		
//		//加密
//		f = "/c:/lye/134.png";
//		String fileName = "134.png";
//		long keyId = 56;
//		int ver = 1;
//		String verName = "LINK_BOX_3.5.1";
//		int level = 1;
//		AESFileUtil.encryptLYEFile(new File(f), new File(f+".lye"),false,fileName,new File(f).length(),keyId,ver,verName,level,pwd,keyValue);
//		//解密
//		f = "/c:/lye/ttt.lye";
//		System.out.println("keyValue = "+keyValue);
//		AESFileUtil.decryptLYEFile(new File(f), new File(f+".txt"),keyValue);
		String f = "/e:/usr/linkapp/data/linkapp//archive/db/2012/11/06/1448070302_7df093fa7f0707231266deb9a212fd6a.LFS";
		long keyId = AESFileUtil.getLYEFileKeyId(new File(f),true);
		System.out.println("keyId="+keyId);
	}
}
