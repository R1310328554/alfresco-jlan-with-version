package com.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.log4j.Logger;

/**
 * 对文件加密/解密和压缩/解压缩对象类
 * AES压缩加密/解压缩解密，网上一般用base64来对byte[]编码,其实不需要，指定AES/CBC/PKCS5Padding
 * 来指定加密解密时候位数不对的情况下，用pkcs5padding来附加位数，不过这个时候读解密的文件的时候，要多读16位的验证位就不会报异常
 * @author Leixiqing
 *
 */
public class AESUtil {
	
	private static Logger logger = Logger.getRootLogger();
	
	public static final int eSize = 1048576;//1024*1024，1M	
	public static final int dSize = 1048592;//1024*1024+16
//	public static final int eSize = 1024;//1024 //测试用
//	public static final int dSize = 1040;//1024+16 //测试用
	private static final long bigFileSize = 10485760;//1024*1024*10,//大于10M的文件加密前后1M内容。可提高速度
	private static final String aesFileTag = "LYESecret";//大文件标识
	public static Key privateKey;
	private static boolean flag = false;
	private static Key headerKey = new SecretKeySpec("fe1b9a223ba095ff".getBytes(), "AES");
	private static String publicKeyValue = "";
	private static String privateKeyValue = "";
	
	/**
	 * 初始化
	 */
	static
	{
		if(null ==privateKey)
		{
			FileInputStream fis  = null;
			try{
			    File publicKeyFile = new File(FileUtil.getRootPath()+"/WEB-INF/conf/public.key");  
			    File privateKeyFile = new File(FileUtil.getRootPath()+"/WEB-INF/conf/linkapp.key");
			    
			   
		        byte[] byteKey = new byte[(int) publicKeyFile.length()];  
		        fis = new FileInputStream(publicKeyFile);
		        fis.read(byteKey);
		        publicKeyValue = new String(byteKey);
		        
				String md5KeyValue = MD5Util.getFileMD5(privateKeyFile);
				md5KeyValue = MD5Util.hash(md5KeyValue);
				if(md5KeyValue.equals(publicKeyValue))
				{
					byte[] key = new byte[(int) privateKeyFile.length()];  
			        fis = new FileInputStream(privateKeyFile);
			        fis.read(key);
			        privateKeyValue = new String(key);
			        SecretKeySpec sKeySpec = new SecretKeySpec(key, "AES");  
			        privateKey = sKeySpec;
				}
				else
				{
					logger.error("软件密钥有误！请检查密钥");
				}
			}
			catch(FileNotFoundException e)
			{
				logger.error("软件密钥找不到",e);
			}
			catch(IOException e)
			{
				logger.error("软件密钥读取失败！请检查密钥",e);
			}
			catch(Exception e)
			{
				logger.error("软件密钥有误！请检查密钥",e);
			}finally {
				try{
				if(null != fis) fis.close();
				}catch(Exception e)
				{
					logger.error(e.getMessage());
				}
			}
		}
	}
	
	/**
	 * 获得16进度的密钥
	 * @return
	 */
	public static String getHexStringPrivateKey()
	{
		String hexString = "";
		FileInputStream fis  = null;
		try{
			File privateKeyFile = new File(FileUtil.getRootPath()+"/WEB-INF/conf/linkapp.key");
			byte[] key = new byte[(int) privateKeyFile.length()];  
	        fis = new FileInputStream(privateKeyFile);
	        fis.read(key);
	        
	        hexString = bytesToHexString(key);
		}
		catch(FileNotFoundException e)
		{
			logger.error("软件密钥找不到",e);
		}
		catch(IOException e)
		{
			logger.error("软件密钥读取失败！请检查密钥",e);
		}
		catch(Exception e)
		{
			logger.error("软件密钥有误！请检查密钥",e);
		}finally {
				try{
				if(null != fis) fis.close();
				}catch(Exception e)
				{
					logger.error(e.getMessage());
				}
			}
		return hexString;
	}
	
	/**
	 * 加密字节
	 * @param b
	 * @param pos
	 * @param length
	 * @return
	 * @throws Exception
	 */
	private static byte[] encryptByte(byte[] b,int pos,int length) throws Exception
	{
		SecureRandom sr = new SecureRandom();
		Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
		IvParameterSpec spec = new IvParameterSpec(privateKey.getEncoded());
		cipher.init(Cipher.ENCRYPT_MODE, privateKey, spec, sr);
		int len = b.length;
		if(length>0)
		{
			len = length;
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
	public static byte[] decryptByte(byte[] b,int pos,int length) throws Exception
	{
		SecureRandom sr = new SecureRandom();
		Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
		IvParameterSpec spec = new IvParameterSpec(privateKey.getEncoded());
		cipher.init(Cipher.DECRYPT_MODE, privateKey, spec, sr);
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
	public static boolean encryptFile(File inFile, File outFile) throws Exception {
		if(null == privateKey)
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
				if(inFile.length()>bigFileSize)//大文件
				{
					//用大文件加密方式局部加密
					return encryptBigFile(inFile,outFile);
				}
				if (!outFile.exists()) outFile.getParentFile().mkdirs();//建立目录
				
				fis = new FileInputStream(inFile);
				fos = new FileOutputStream(outFile);
				byte[] eByte = new byte[eSize];
				int len;
				while ((len = fis.read(eByte)) != -1) {
					fos.write(encryptByte(eByte,0,len));
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
			logger.error("加密文件出错",e);
			throw e;
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
	 * 
	 * @param srcFile
	 * @param destFile
	 */
	private static boolean encryptBigFile(File inFile, File outFile) throws Exception {
		if(null == privateKey)
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
				if(inFile.length()<=bigFileSize)//小文件
				{
					//直接用普通加密方式
					return encryptFile(inFile,outFile);
				}
				if (!outFile.exists()) outFile.getParentFile().mkdirs();//建立目录
				
				fis = new FileInputStream(inFile);
				fos = new FileOutputStream(outFile);
				//header 
				String sTag = padRight(aesFileTag,1);
				String nLeve = "S";//F全部,S开始,E结束
				String sInfo = "<LYSINFO size=\""+inFile.length()+"\" eSize=\""+eSize+"\" bit=\"128\" nLeve=\""+nLeve+"\" key1=\""+publicKeyValue+"\" key2=\""+privateKeyValue+"\"/>";
//				//加密不够补位
				int nTail =sInfo.getBytes("UTF-8").length%16;
				int nAppend =0;
				if (nTail > 0)
		        {
		            nAppend = 16 - nTail;
		            sInfo = padRight(sInfo,nAppend);
		        }
				byte[] bInfo = AESFileUtil.encryptByte(sInfo.getBytes("UTF-8"),0,sInfo.getBytes("UTF-8").length,headerKey);//加密后的info信息
				String sInfoLen = ""+bInfo.length;
				sInfoLen = padRight(sInfoLen,20-sInfoLen.length());
				fos.write(sTag.getBytes());
				fos.write(sInfoLen.getBytes());
				fos.write(bInfo);		
				
				byte[] eByte = new byte[eSize];
				int len;
				int idx = 0;//块数
				while ((len = fis.read(eByte)) != -1) {
					//前面1M,加密前面字节
					if(idx==0)
					{
						fos.write(encryptByte(eByte,0,len));
					}
					else
					{
						fos.write(eByte,0,len);
					}
					idx ++;//递增
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
			logger.error("加密文件出错",e);
			throw e;
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
	 * 把文件流加密后存储为outFile
	 * 
	 * @param srcFile
	 * @param destFile
	 */
	public static boolean encryptInputStreamToFile(FileInputStream fis, File outFile) throws Exception {
		if(null == privateKey)
		{
			logger.error("软件密钥错误！不能加密文件");
			return false;
		}
		FileOutputStream fos = null;
		try
		{
			if(null != fis)
			{
				if (!outFile.exists()) outFile.getParentFile().mkdirs();//建立目录
				
				fos = new FileOutputStream(outFile);
				byte[] b = new byte[eSize];
				int len;
				while ((len = fis.read(b)) != -1) {
					fos.write(encryptByte(b,0,len));
				}
				fos.close();
				fis.close();
				
				flag = true;//成功
			}
			else
			{
				logger.error("encryptFile ERROR;  InputStream is not null");
			}
		}
		catch(Exception e)
		{
			logger.error("加密文件流出错",e);
			throw e;
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
	public static boolean decryptFile(File inFile, File outFile) throws Exception {
		if(null == privateKey)
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
				byte[] bTag = new byte[10];
				fis.read(bTag);
				String fTag = new String(bTag).trim();
				if(aesFileTag.equalsIgnoreCase(fTag))
				{
					logger.debug(fTag+" , f:"+fTag.equalsIgnoreCase(aesFileTag));
					return decryptBigFile(inFile,outFile);//采用大文件方式
				}
				//采用普通加密方式()
				fis = new FileInputStream(inFile);
				fos = new FileOutputStream(outFile);
				byte[] dByte = new byte[dSize];
				int len;
				while ((len = fis.read(dByte)) != -1) {
					fos.write(decryptByte(dByte,0,len));
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
			logger.error("解密文件出错",e);
			throw e;
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
	
	public static AESTag getAESTag(File inFile,long p,long pend,long fvSize) throws Exception
	{
		AESTag tag = new AESTag();
		tag.setSecret(false);
		FileInputStream fis = null;
		long encryptPos = 0;
		long encryptPosEnd = 0;
		try
		{
			if(inFile.exists())
			{
				fis = new FileInputStream(inFile);
				byte[] bTag = new byte[10];
				byte[] bInfoLen = new byte[20];
				fis.read(bTag);
				fis.read(bInfoLen);
				String fTag = new String(bTag).trim();
				if(aesFileTag.equalsIgnoreCase(fTag))
				{
					tag.setSecret(true);//
					String sInfoLen = new String(bInfoLen).trim();
					int infoLen = Integer.parseInt(sInfoLen.trim());
					tag.setInfoLen(infoLen);//
					
					byte[] bInfo = new byte[infoLen];
					fis.read(bInfo);
					
					byte[] decInfo = AESFileUtil.decryptByte(bInfo,0,bInfo.length,headerKey);
					String sInfo = new String(decInfo,"UTF-8");
//					long fileSize = 0;
//					long eSize = 0;
					String nLeve = "S";
					if (null != sInfo) {
						Pattern pattern;
						Matcher matcher;
//						pattern = Pattern.compile("size=\"(.*?)\"");
//						matcher = pattern.matcher(sInfo);
//						if (matcher.find()) {
//							fileSize = Long.parseLong(matcher.group(1).trim());
//						}
//						pattern = Pattern.compile("eSize=\"(.*?)\"");
//						matcher = pattern.matcher(sInfo);
//						if (matcher.find()) {
//							eSize = Long.parseLong(matcher.group(1).trim());
//						}
						pattern = Pattern.compile("nLeve=\"(.*?)\"");
						matcher = pattern.matcher(sInfo);
						if (matcher.find()) {
							nLeve = matcher.group(1).trim();
						}
					}
					tag.setnLeve(nLeve);
//					logger.debug("fileSize:"+fileSize+" ,eSize:"+eSize+" ,nLeve:"+nLeve+" ,infoLen:"+infoLen);
				}
			}
			if(tag.isSecret()&&"S".equalsIgnoreCase(tag.getnLeve()))
			{
				//使用的是局部加密
				if(p!=0)
				{
					p = (p/AESUtil.eSize)*AESUtil.eSize;//原文位置										
					encryptPos = (p/AESUtil.eSize)*AESUtil.dSize;//密文位置
					long blockSize = p/AESUtil.eSize;
					if(blockSize>=1)
					{
						encryptPos = AESUtil.dSize+(blockSize-1)*AESUtil.eSize;//密文位置
					}
				}
				if(pend!=0)
				{
					Double pceil = Math.ceil((pend+1)/1.0/AESUtil.eSize);
					pend = pceil.longValue()*AESUtil.eSize-1;//原文位置
					if(pend>p && pend <fvSize)
					{
						//注pceil.longValue(),始终会大于0
						encryptPosEnd = AESUtil.dSize+(pceil.longValue()-1)*AESUtil.eSize;//密文位置
					}
					else
					{
						pend = fvSize-1;
					}
				}
				else
				{
					pend = fvSize-1;
				}
			}
			else
			{
				if(p!=0)
				{
					p = (p/AESUtil.eSize)*AESUtil.eSize;//原文位置
					encryptPos = (p/AESUtil.eSize)*AESUtil.dSize;//密文位置
				}
				if(pend!=0)
				{
					Double pceil = Math.ceil((pend+1)/1.0/AESUtil.eSize);
					pend = pceil.longValue()*AESUtil.eSize-1;//原文位置
					if(pend>p && pend <fvSize)
					{
						encryptPosEnd = pceil.longValue()*AESUtil.dSize;//密文位置
					}
					else
					{
						pend = fvSize-1;
					}
				}
				else
				{
					pend =fvSize-1;
				}
			}
			if(tag.isSecret())//是加了头文件的
			{
				encryptPos += tag.getHeaderLen();//增加头文件的长度
			}	
			tag.setP(p);
			tag.setPend(pend);
			tag.setEncryptPos(encryptPos);
			tag.setEncryptPosEnd(encryptPosEnd);
		}catch(Exception e)
		{
			logger.error("getAESTag出错",e);
		}
		finally
		{
			if(null != fis)
			{
				fis.close();
			}
		}
		return tag;
	}
	
	/**
	 * 把文件inFile解密后存储为outFile
	 * 
	 * @param srcFile
	 * @param destFile
	 * @param privateKey
	 * @throws Exception
	 */
	private static boolean decryptBigFile(File inFile, File outFile) throws Exception {
		if(null == privateKey)
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
				
				byte[] bTag = new byte[10];
				byte[] bInfoLen = new byte[20];
				fis.read(bTag);
				fis.read(bInfoLen);
				String fTag = new String(bTag).trim();
				if(!aesFileTag.equalsIgnoreCase(fTag))
				{
					return decryptFile(inFile,outFile);//采用普通加密方式
				}
				String sInfoLen = new String(bInfoLen).trim();
				int infoLen = Integer.parseInt(sInfoLen.trim());
				
				byte[] bInfo = new byte[infoLen];
				fis.read(bInfo);
				
				byte[] decInfo = AESFileUtil.decryptByte(bInfo,0,bInfo.length,headerKey);
				String sInfo = new String(decInfo,"UTF-8");
				long fileSize = 0;
				long eSize = 0;
				String nLeve = "S";
				if (null != sInfo) {
					Pattern pattern;
					Matcher matcher;
					pattern = Pattern.compile("size=\"(.*?)\"");
					matcher = pattern.matcher(sInfo);
					if (matcher.find()) {
						fileSize = Long.parseLong(matcher.group(1).trim());
					}
					pattern = Pattern.compile("eSize=\"(.*?)\"");
					matcher = pattern.matcher(sInfo);
					if (matcher.find()) {
						eSize = Long.parseLong(matcher.group(1).trim());
					}
					pattern = Pattern.compile("nLeve=\"(.*?)\"");
					matcher = pattern.matcher(sInfo);
					if (matcher.find()) {
						nLeve = matcher.group(1).trim();
					}
				}
				logger.debug("fileSize:"+fileSize+" ,eSize:"+eSize+" ,nLeve:"+nLeve+" ,infoLen:"+infoLen);
				
				fos = new FileOutputStream(outFile);
				byte[] dByte = new byte[dSize];
				int len;
				int idx = 0;//块数
				while ((len = fis.read(dByte)) != -1) {
					if("S".equalsIgnoreCase(nLeve))
					{
						if(idx == 0)
						{
							fos.write(decryptByte(dByte,0,len));
						}
						else
						{
							fos.write(dByte,0,len);
						}
					}
					else
					{
						fos.write(decryptByte(dByte,0,len));
					}
					idx ++;//递增
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
			logger.error("解密文件出错",e);
			throw e;
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
	public static byte[] decryptFileHeaderByte(File inFile) throws Exception {
		if(null == privateKey)
		{
			logger.error("软件密钥错误！不能解密文件");
			return null;
		}
		byte[] decryptByte = null;
		FileInputStream fis = null;
		try
		{
			if(inFile.exists())
			{
				fis = new FileInputStream(inFile);
				byte[] b = new byte[dSize];
				int len;
//				while ((len = fis.read(b)) != -1) {
//					fos.write(decryptByte(b,0,len));
//				}
				//最多取一M
				len =fis.read(b);
				decryptByte = decryptByte(b,0,len);
				fis.close();
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
		return decryptByte;
	}
	
	/**
	 * 字符串加密成byte
	 * @param str
	 * @return
	 * @throws Exception 
	 */
	public static byte[] encryptStrToByte(String str,String key) throws Exception
	{
		Key aesKey;
		try
		{
			aesKey = new SecretKeySpec(key.getBytes(), "AES");
			
			int nTail =str.length()%16;
			int nAppend =0;
			if (nTail > 0)
	        {
	            nAppend = 16 - nTail;
	            str = padRight(str,nAppend);
	        }	
			Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, aesKey);
			
			byte[] encrypted = cipher.doFinal(str.getBytes());
			
			return encrypted;
		}
		catch(Exception e)
		{
			logger.error("软件密钥错误！不能加密文件",e);
			throw e;
		}
	}
	
	/**
	 * byte 解密成字符
	 * @param str
	 * @return
	 * @throws Exception 
	 */
	public static String decryptByteToStr(byte[] src,String key) throws Exception
	{
		Key aesKey;
		try
		{
			aesKey = new SecretKeySpec(key.getBytes(), "AES");
			Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, aesKey);
			
			byte[] encrypted = cipher.doFinal(src);
			
			return byteToString(encrypted);
		}
		catch(Exception e)
		{
			logger.error("软件密钥错误！不能加密文件",e);
			throw e;
		}
	}
	
	/**
	 * byte加密成十六进制
	 * @return
	 */
	public static String encryptStrToHexStr(String str,String key)
	{
		if(null == str && !"".equals(str))
		{
			return null;
		}
		String hexStr = "";
		byte[] encrypted;
		try {
			encrypted = encryptStrToByte(str,key);
			hexStr = bytesToHexString(encrypted);
		} catch (Exception e) {
			logger.error("加密异常：str="+str+";key="+key);
		}
		return hexStr;
	}
	
	/**
	 * 十六进制解密成字符
	 * @return
	 */
	public static String decryptHexStrToStr(String hexStr,String key)
	{
		if(null == hexStr && !"".equals(hexStr))
		{
			return null;
		}
		String str = "";
		try {
			byte[] dByte =  hexStringToBytes(hexStr);
			str = decryptByteToStr(dByte,key);
			if(null != str)
			{
				str = str.trim();
			}
		} catch (Exception e) {
			logger.error("解密异常：hexStr="+hexStr+";key="+key);
		}
		return str;
	}
	
	 /**
	 * Convert byte[] to hex string.这里我们可以将byte转换成int，然后利用Integer.toHexString(int)来转换成16进制字符串。  
	 * @param src byte[] data  
	 * @return hex string  
	 */     
	public static String bytesToHexString(byte[] src){  
	    StringBuilder stringBuilder = new StringBuilder("");  
	    if (src == null || src.length <= 0) {  
	        return null;  
	    }  
	    for (int i = 0; i < src.length; i++) {  
	        int v = src[i] & 0xFF;  
	        String hv = Integer.toHexString(v);  
	        if (hv.length() < 2) {  
	            stringBuilder.append(0);  
	        }  
	        stringBuilder.append(hv);  
	    }  
	    return stringBuilder.toString();
	}  
	 
	 /** 
	  * Convert hex string to byte[] 
	  * @param hexString the hex string 
	  * @return byte[] 
	  */  
	 public static byte[] hexStringToBytes(String hexString) {  
	     if (hexString == null || hexString.equals("")) {  
	         return null;  
	     }  
	     hexString = hexString.toUpperCase();  
	     int length = hexString.length() / 2;  
	     char[] hexChars = hexString.toCharArray();  
	     byte[] d = new byte[length];  
	     for (int i = 0; i < length; i++) {  
	         int pos = i * 2;  
	         d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));  
	     }  
	     return d;  
	 }  
	 
	 /** 
	  * Convert char to byte 
	  * @param c char 
	  * @return byte 
	  */  
	  private static byte charToByte(char c) {  
	     return (byte) "0123456789ABCDEF".indexOf(c);  
	 }  
	 
	 /**
	  * byteToString
	  * @return
	  */
	 public static String byteToString(byte[] b)
	 {
		 if(1==1)
		 {
			 return new String(b);
		 }
		 StringBuffer res = new StringBuffer();
		 if(null !=b && b.length!=0)
		 {
			 byte[] c = new byte[b.length];
			 for(int i=0;i<b.length;i++)
			 {
				 if(b[i]==0)
//					 break;
				 c [i] = b[i];
				 res.append(b[i]);
			 }
			 String str = new String(c);
			 return str.trim();
//			 return res.toString().trim();
		 }
		 else
		 {
			 return null;
		 }
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
	 
	
	public static void main(String args[]) throws Exception {
		long a = System.currentTimeMillis();
		System.out.println("开始执行...");
//		String f = "/c:/aes/tp1.jpg";
//		//加密
//		AESUtil.encryptFile(new File("D:/temp/logsfy.rar"), new File("D:/temp/logsfy.rar.e1"));
//		AESUtil.encryptFile(new File("D:/temp/logsfy.rar"), new File("D:/temp/logsfy.rar.e2"));
//		AESUtil.encryptFile(new File("D:/temp/logsfy.rar"), new File("D:/temp/logsfy.rar.e3"));
//		AESUtil.encryptFile(new File("D:/DNGS_Ghost_Win7_SP1_Ultimate_x86_201203.iso"), new File("D:/temp/ttt.txt.e1"));
//		System.out.println(System.currentTimeMillis() - a+" ms");
//		AESUtil.encryptFile(new File("D:/Test.rar"), new File("D:/temp/ttt.txt.e1"));
//		System.out.println(System.currentTimeMillis() - a+" ms");
//		a = System.currentTimeMillis();
		
		AESUtil.encryptBigFile(new File("D:/Test.rar"), new File("D:/temp/Test.rar.e1"));
		System.out.println(System.currentTimeMillis() - a+" ms");
		a = System.currentTimeMillis();
		
		AESUtil.decryptBigFile( new File("D:/temp/Test.rar.e1"),new File("D:/Test_des.rar"));
		System.out.println(System.currentTimeMillis() - a+" ms");
		a = System.currentTimeMillis();
		
//		AESUtil.encryptBigFile(new File("D:/Test.txt"), new File("D:/temp/Test.rar.e1"));
//		System.out.println(System.currentTimeMillis() - a+" ms");
//		a = System.currentTimeMillis();
//		
//		AESUtil.decryptBigFile( new File("D:/temp/Test.rar.e1"),new File("D:/Test_des.txt"));
//		System.out.println(System.currentTimeMillis() - a+" ms");
//		a = System.currentTimeMillis();
		
		
//		DiskUtil.movieSingeFile(new File("D:/Test.rar"), new File("D:/temp/ttt.txt.e12"));
//		System.out.println(System.currentTimeMillis() - a+" ms");
//		a = System.currentTimeMillis();
//		AESUtil.encryptFile(new File("D:/sqldeveloper-1.5.5.59.69.zip"), new File("D:/temp/ttt.txt.e2"));
//		System.out.println(System.currentTimeMillis() - a+" ms");
//		a = System.currentTimeMillis();
//		
//		AESUtil.encryptBigFile(new File("D:/sqldeveloper-1.5.5.59.69.zip"), new File("D:/temp/ttt.txt.e2"));
//		System.out.println(System.currentTimeMillis() - a+" ms");
//		a = System.currentTimeMillis();
//		DiskUtil.movieSingeFile(new File("D:/sqldeveloper-1.5.5.59.69.zip"), new File("D:/temp/ttt.txt.e22"));
//		//解密
//		AESUtil.decryptFile(new File("D:/temp/test.dat"), new File("D:/temp/test.dat.txt"));
////		AESUtil.decryptFile(new File("D:/temp/test2.dat"), new File("D:/temp/test2.dat.txt"));
//		AESUtil.decryptFile(new File("D:/temp/test.dat2"), new File("D:/temp/test.dat2.txt"));
//		AESUtil.decryptFile(new File("D:/temp/logsfy.rar.e1"), new File("D:/temp/logsfy.e1.rar"));
//		AESUtil.decryptFile(new File("D:/temp/ttt.txt.e1"), new File("D:/temp/ttt2.txt"));
//		//比较
//		getFile();
		
//		System.out.println("密钥为："+AESUtil.getHexStringPrivateKey());
		
//		String key = "a1959a86380426ff";
//		System.out.println(AESUtil.encryptStrToHexStr("5", key));
////		System.out.println(AESUtil.decryptHexStrToStr(AESUtil.encryptStrToHexStr("5", key), key));
//		System.out.println(AESUtil.decryptHexStrToStr("997979f1c97bdac53012a7ce316b8721", key).trim());
//		
//		System.out.println(System.currentTimeMillis() - a+" ms");
		
		systeInfo();
	}
	
	public static void getFile()
	{
		
		System.out.println(AESUtil.getHexStringPrivateKey());
		
		
//		String f = "/c:/aes/tp1.jpg";		
//		File file3 = new File(f+".aes."+DiskUtil.getExt(f));
//		File file2 = new File(f+".aes.");
//		File file1 = new File(f);
//		
//		System.out.println("size1="+file1.length());
//		System.out.println("size2="+file2.length());
//		System.out.println("size3="+file3.length());		
//		try {
//			String md51 = MD5Util.getFileMD5(file1);
//			
//			String md52 = MD5Util.getFileMD5(file2);
//			String md53 = MD5Util.getFileMD5(file3);
//			System.out.println("md51="+md51);
//			System.out.println("md52="+md52);
//			System.out.println("md53="+md53);
//		} catch (Exception e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}
	
	public static void systeInfo(){
		//空闲内存：
//		System.out.println("   空闲内存："+Runtime.getRuntime().freeMemory());
//		//最大内存：
//		System.out.println("   最大内存："+Runtime.getRuntime().maxMemory());
		//已占用的内存：
		System.out.println("已占用的内存："+(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())+"，可用百分比："+(Runtime.getRuntime().freeMemory()*100/Runtime.getRuntime().totalMemory()));
		
	}

}
