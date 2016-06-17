package com.auth;

import java.security.Key;
import java.security.SecureRandom;
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
public class AESUtil {
	
	private static Logger logger = Logger.getRootLogger();
	
	public static final int eSize = 1048576;//1024*1024，1M
	public static final int dSize = 1048592;//1024*1024+16
	public static Key privateKey;
	
	
	
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
		Cipher ciphers = Cipher.getInstance("AES/CBC/PKCS5Padding");
		IvParameterSpec spec = new IvParameterSpec(privateKey.getEncoded());
		ciphers.init(Cipher.DECRYPT_MODE, privateKey, spec, sr);
		int len = b.length;
		if(length>0)
		{
			len = length;
		}		
		return ciphers.doFinal(b, pos, len);
	}
		
	/**
	 * 字符串加密成byte
	 * @param str
	 * @return
	 */
	public static byte[] encryptStrToByte(String str,String key)
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
			return null;
		}
	}
	
	/**
	 * byte 解密成字符
	 * @param str
	 * @return
	 */
	public static String decryptByteToStr(byte[] src,String key)
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
			return null;
		}
	}
	
	/**
	 * byte加密成十六进制
	 * @return
	 */
	public static String encryptStrToHexStr(String str,String key)
	{
		byte[] encrypted = encryptStrToByte(str,key);
		return bytesToHexString(encrypted);
	}
	
	/**
	 * 十六进制解密成字符
	 * @return
	 */
	public static String decryptHexStrToStr(String hexStr,String key)
	{
		byte[] dByte =  hexStringToBytes(hexStr);
		String str = decryptByteToStr(dByte,key);
		if(null != str)
		{
			str = str.trim();
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
			
//		String f = "/c:/aes/tp1.jpg";
//		//加密
//		AESUtil.encryptFile(new File(f), new File(f+".aes"));
//		//解密
//		AESUtil.decryptFile(new File(f+".aes"), new File(f+".aes."+DiskUtil.getExt(f)));
//		//比较
//		getFile();
		String key = "a1959a86380426ff";
		System.out.println(AESUtil.encryptStrToHexStr("5", key));
//		System.out.println(AESUtil.decryptHexStrToStr(AESUtil.encryptStrToHexStr("5", key), key));
		System.out.println(AESUtil.decryptHexStrToStr("997979f1c97bdac53012a7ce316b8721", key).trim());
		
		System.out.println(System.currentTimeMillis() - a+" ms");
	}

}
