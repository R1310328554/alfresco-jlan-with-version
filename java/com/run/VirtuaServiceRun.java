package com.run;

import org.alfresco.jlan.app.XMLServerConfiguration;
import org.alfresco.jlan.server.NetworkServer;
import com.mchange.v2.codegen.bean.Property;
import com.util.SpringUtil;

import org.springframework.context.ApplicationContext;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
public class VirtuaServiceRun{

	private Property configProperty;
	private static final String LOG4J_PATH_POP = "log4j.properties";
	private static ApplicationContext springContext;
	public static XMLServerConfiguration xmlConfig;
	private static boolean SHUT_SERVER = false;
	private static Logger log4j = Logger.getLogger(VirtuaServiceRun.class);
	

	public Property getConfigProperty() {
		return configProperty;
	}

	public void setConfigProperty(Property configProperty) {
		this.configProperty = configProperty;
	}

	/**
	 * 初始化服务器配置
	 */
	
	
	public void init() {
		PropertyConfigurator.configure("/"+SpringUtil.getRootPath()+LOG4J_PATH_POP); // 初始化日志
		springContext = SpringUtil.getApplicationContext();
		
		xmlConfig = (XMLServerConfiguration) SpringUtil.getBean("xmlServerConfig");
		NetworkServer smbserv = (NetworkServer) SpringUtil.getBean("smbService");
		NetworkServer netBIOS = (NetworkServer) SpringUtil.getBean("netBIOS");
		xmlConfig.addServer(smbserv);
		xmlConfig.addServer(netBIOS);		
		
	}
 
	public void start() {
		try {
			if (springContext == null || xmlConfig == null){
				if(!SHUT_SERVER){
					init();
					for (int i = 0; i < xmlConfig.numberOfServers(); i++){
						xmlConfig.getServer(i).startServer();
					}
				}
			}

		} catch (Exception e) {
			log4j.error("error !  virtua server  start failure  ..! " + e);
			e.printStackTrace();
		}
	}

	public void shutDownServer() {
		log4j.info("shutDown  server........");
		int idx = xmlConfig.numberOfServers() - 1;
		while (idx >= 0) {
			NetworkServer srv = xmlConfig.getServer(idx--);
			if (srv.isActive())
				srv.shutdownServer(true);
		}
	}
	//测试
	public static void main(String[] arg)
	{
		VirtuaServiceRun cifsService = new VirtuaServiceRun();
		cifsService.start();
	}
}
