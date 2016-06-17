package com.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Date;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.log4j.Logger;

/**
 * 对文件加密/解密和压缩/解压缩对象类
 * AES压缩加密/解压缩解密，网上一般用base64来对byte[]编码,其实不需要，指定AES/CBC/PKCS5Padding
 * 来指定加密解密时候位数不对的情况下，用pkcs5padding来附加位数，不过这个时候读解密的文件的时候，要多读16位的验证位就不会报异常
 * @author lk
 *
 */
public class AESUtilOld {
	
	private static Logger logger = Logger.getRootLogger();
	
	public static final int eSize = 1048576;//1024*1024，1M
	public static final int dSize = 1048592;//1024*1024+16
	public static Key privateKey;
	private static boolean flag = false;
	
	/**
	 * 初始化
	 */
	static
	{
		if(null ==privateKey)
		{
			FileInputStream fis  = null;
			try{
			    File publicKeyFile = new File(FileUtil.getRootPath()+"/WEB-INF/conf/public_old.key");  
			    File privateKeyFile = new File(FileUtil.getRootPath()+"/WEB-INF/conf/linkapp_old.key");
			    
			    String publicKeyValue = "";
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
			File privateKeyFile = new File(FileUtil.getRootPath()+"/WEB-INF/conf/linkapp_old.key");
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
	public static byte[] encryptByte(byte[] b,int pos,int length) throws Exception
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
		catch(javax.crypto.BadPaddingException e)
		{
			logger.error("Old解密文件出错",e);
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
	
//	/**
//	 * 把文件inFile解密后存储为outFile
//	 * 
//	 * @param srcFile
//	 * @param destFile
//	 * @param privateKey
//	 * @throws Exception
//	 */
//	public static FileInputStream decryptFileToInputStream(File inFile) throws Exception {
//		if(null == privateKey)
//		{
//			logger.error("软件密钥错误！不能解密文件");
//			return null;
//		}
//		FileInputStream fis = null;
//		FileInputStream inputStream = null;
//		FileOutputStream fos = null;
//		try
//		{
//			if(inFile.exists())
//			{				
//				String tmpfilepath = DiskUtil.getRootPath()+"/archive/temp/transactions/"+MD5Util.hash(inFile.getAbsolutePath())+(new Date().getTime()) + "eml.temp";// 临时文件
//				File tmpFile = new File(tmpfilepath);
//				if(!tmpFile.getParentFile().exists())
//				{
//					tmpFile.getParentFile().mkdirs();
//				}
//				
//				fis = new FileInputStream(inFile);
//				fos = new FileOutputStream(tmpfilepath);
//				byte[] b = new byte[dSize];
//				int len;
//				while ((len = fis.read(b)) != -1) {
//					fos.write(decryptByte(b,0,len));
//				}
//				fos.close();
//				fis.close();
//				
//				inputStream = new FileInputStream(tmpfilepath);
//			}
//			else
//			{
//				logger.error("decryptFile ERROR; "+inFile.getAbsolutePath()+" not exists");
//			}
//		}
//		catch(Exception e)
//		{
//			logger.error("解密文件流出错",e);
//			throw e;
//		}
//		finally
//		{
//			if(null != fis)
//			{
//				fis.close();
//			}
//			if(null != fos)
//			{
//				fos.close();
//			}
//		}
//		return inputStream;
//	}
	
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
		AESUtilOld.encryptFile(new File("D:/temp/logsfy.rar"), new File("D:/temp/logsfy.rar.e1"));
//		AESUtil.encryptFile(new File("D:/temp/logsfy.rar"), new File("D:/temp/logsfy.rar.e2"));
//		AESUtil.encryptFile(new File("D:/temp/logsfy.rar"), new File("D:/temp/logsfy.rar.e3"));
		AESUtilOld.encryptFile(new File("D:/temp/ttt.txt"), new File("D:/temp/ttt.txt.e1"));
//		//解密
//		AESUtil.decryptFile(new File("D:/temp/test.dat"), new File("D:/temp/test.dat.txt"));
////		AESUtil.decryptFile(new File("D:/temp/test2.dat"), new File("D:/temp/test2.dat.txt"));
//		AESUtil.decryptFile(new File("D:/temp/test.dat2"), new File("D:/temp/test.dat2.txt"));
		AESUtilOld.decryptFile(new File("D:/temp/logsfy.rar.e1"), new File("D:/temp/logsfy.e1.rar"));
		AESUtilOld.decryptFile(new File("D:/temp/ttt.txt.e1"), new File("D:/temp/ttt2.txt"));
//		//比较
//		getFile();
		
//		System.out.println("密钥为："+AESUtil.getHexStringPrivateKey());
		
//		String key = "a1959a86380426ff";
//		System.out.println(AESUtil.encryptStrToHexStr("5", key));
////		System.out.println(AESUtil.decryptHexStrToStr(AESUtil.encryptStrToHexStr("5", key), key));
//		System.out.println(AESUtil.decryptHexStrToStr("997979f1c97bdac53012a7ce316b8721", key).trim());
//		
		System.out.println(System.currentTimeMillis() - a+" ms");
		
		systeInfo();
	}
	
	public static void getFile()
	{
		
		System.out.println(AESUtilOld.getHexStringPrivateKey());
		
		
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
