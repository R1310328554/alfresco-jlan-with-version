package com.util;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Encodes a string using MD5 hashing
 *
 * @author Rafael Steil
 */
public class MD5Util {
	
	protected static final char hexDigits[] = { '0', '1', '2', '3', '4', '5', '6',
		'7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' }; 
	private static final long bigFileSize = 524288000;//1024*1024*500,//大于500M的文件取前后1M内容的MD5值。可提高速度
	private static final int mSize = 1048576;//1024*1024 ,1M读写的块大小
	private static final int bufferSize = 1048576;//1024*1024 ,IO读写缓冲大小
	
	/**
	 * Encodes a string
	 *
	 * @param str String to encode
	 * @return Encoded String
	 * @throws NoSuchAlgorithmException
	 */
	public static String hash(String str) {
		if (str == null || str.length() == 0) {
			throw new IllegalArgumentException("String to encript cannot be null or zero length");
		}

		StringBuilder hexString = new StringBuilder();

		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.update(str.getBytes());
			byte[] hash = md.digest();

			for (byte element : hash) {
				if ((0xff & element) < 0x10) {
					hexString.append('0').append(Integer.toHexString((0xFF & element)));
				}
				else {
					hexString.append(Integer.toHexString(0xFF & element));
				}
			}
		}
		catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}

		return hexString.toString();
	}
	
	/**
	 * 对文件全文生成MD5
	 * @param file
	 * @return
	 */
	public static String getFileMD5(File file)
	{
		FileInputStream fis = null;
		MessageDigest md;
		try {
			if(null !=file && file.isFile())
			{
				if(file.length()>bigFileSize)
				{
					//大于500M的文件取前后1M内容的MD5值。可提高速度
					return getBigFileMD5(file);
				}
				fis = new FileInputStream(file);
				md = MessageDigest.getInstance("MD5");
				byte[] buffer = new byte[bufferSize];
				int length = -1;
				while ((length = fis.read(buffer)) != -1) {
				 md.update(buffer, 0, length);
				}
				byte[] b = md.digest();
				return byteToHexString(b);
			}
			else
			{
				return null;
			}
			// 16位加密
			// return buf.toString().substring(8, 24);
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}finally {
			md = null;
			try {
				if(null != fis) fis.close();
			}catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}
	
	/**
	 * 对文件全文生成MD5
	 * @param file
	 * @return
	 */
	public static String getBigFileMD5(File file)
	{
		RandomAccessFile randomFile = null; 
		MessageDigest md;
		try {
			if(file.isFile())
			{
				//大于500M的文件，取前1024+后1024字节内容的MD5
				md = MessageDigest.getInstance("MD5");
				randomFile = new RandomAccessFile(file, "r");
				// 文件长度，字节数
	            long fileLength = randomFile.length();
	            byte[] bytes = new byte[mSize];
	            //前1024*1024
	            randomFile.seek(0);
	            int byteread = randomFile.read(bytes);
	            md.update(bytes, 0, byteread);
	            //后1024*1024
	            // 读文件的起始位置
	            long beginIndex = fileLength-mSize;
	            // 将读文件的开始位置移到beginIndex位置。
	            randomFile.seek(beginIndex);
	            byteread = randomFile.read(bytes);
	            md.update(bytes, 0, byteread);
	            byte[] b = md.digest();
				return byteToHexString(b);
			}
			else
			{
				return null;
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}finally {
			md = null;
			try {
				if(null != randomFile) randomFile.close();
			}catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}
	
	/**
	 * 把byte[]数组转换成十六进制字符串表示形式
	 * @param tmp    要转换的byte[]
	 * @return 十六进制字符串表示形式
	 */
	private static String byteToHexString(byte[] tmp)
	{
		String s;
		// 用字节表示就是 16 个字节
		char str[] = new char[16 * 2]; // 每个字节用 16 进制表示的话，使用两个字符，
		// 所以表示成 16 进制需要 32 个字符
		int k = 0; // 表示转换结果中对应的字符位置
		for (int i = 0; i < 16; i++) { // 从第一个字节开始，对 MD5 的每一个字节
			// 转换成 16 进制字符的转换
			byte byte0 = tmp[i]; // 取第 i 个字节
			str[k++] = hexDigits[byte0 >>> 4 & 0xf]; // 取字节中高 4 位的数字转换,
			// >>> 为逻辑右移，将符号位一起右移
			str[k++] = hexDigits[byte0 & 0xf]; // 取字节中低 4 位的数字转换
		}
		s = new String(str); // 换后的结果转换为字符串
		return s;
	}
	
	public static String getFileSHA1(File file)
	{
		//获得文件的完整MD5
		FileInputStream fis = null;
		String str = "";
		try
		{
			if(file.isFile())
			{
				MessageDigest md = MessageDigest.getInstance("SHA1");
				fis = new FileInputStream(file);
				byte[] buffer = new byte[2048];
				int length = -1;
				while ((length = fis.read(buffer)) != -1) {
				 md.update(buffer, 0, length);
				}
				byte[] b = md.digest();
				return byteToHexString(b);
			}
			else
			{
				return null;
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}finally {
			try {
				if(null != fis) fis.close();
			}catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		return str;
	}
	
	public static void main(String arg[])
	{
		System.out.println("start...");
		long start=System.currentTimeMillis(); //获取最初时间
//		File f = new File("c:/918c94a7432799dbfee35507b530d888.Linux-6.01.iso.temp");
		File f = new File("d:/111.log");
		
//		File f = new File("c:/pagefile.sys");
//		File f = new File("c:/apache-tomcat-6.rar");
		String mdstr = MD5Util.getFileMD5(f);
//		String mdstr = MD5.getBigFileMD5(f);
		System.out.println("md5:"+mdstr);
		
//		String sha1 = MD5Util.getFileSHA1(f);//获得完整MD5
//		System.out.println("sha1:"+sha1);
		
		long end=System.currentTimeMillis(); //获取运行结束时间
		System.out.println("\n\n程序共执行了："+(end-start)+"毫秒,约："+(end-start)/1000+"秒");
		
		systeInfo();
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
