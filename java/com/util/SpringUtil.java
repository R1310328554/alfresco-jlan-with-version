package com.util;

import java.net.URL;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

/**
 * 
 * @author Administrator
 * 
 */
public class SpringUtil {

	private static ApplicationContext applicationContext = null;

	private static String SPRING_CONFIG = "applicationContext.xml";

	/**
	 * 获取applicationContext
	 * 
	 * @return
	 */
	public static ApplicationContext getApplicationContext() {
		if (null == applicationContext) {
			applicationContext = new FileSystemXmlApplicationContext("/"+getRootPath()+SPRING_CONFIG);
		}
		return applicationContext;
	}

	/**
	 * 根据Bean名称获取实例
	 * 
	 * @param name
	 * @return
	 * @throws BeansException
	 */
	public static Object getBean(String name) throws BeansException {
		return getApplicationContext().getBean(name);
	}
	
	public static String getRootPath(){
		String path = "";
		URL url = SpringUtil.class.getResource("/");
		path = url.getPath();
		return path;
	}
}