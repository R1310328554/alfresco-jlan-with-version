package com.fileSystem;

import java.io.File;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;

import net.sf.ehcache.CacheManager;

import org.alfresco.jlan.server.SrvSession;
import org.alfresco.jlan.server.auth.acl.ACLParseException;
import org.alfresco.jlan.server.auth.acl.AccessControlManager;
import org.alfresco.jlan.server.auth.acl.InvalidACLTypeException;
import org.alfresco.jlan.server.auth.acl.UserAccessControlParser;
import org.alfresco.jlan.server.config.ConfigurationListener;
import org.alfresco.jlan.server.config.InvalidConfigurationException;
import org.alfresco.jlan.server.config.ServerConfiguration;
import org.alfresco.jlan.server.core.DeviceContextException;
import org.alfresco.jlan.server.core.ShareMapper;
import org.alfresco.jlan.server.core.SharedDevice;
import org.alfresco.jlan.server.core.SharedDeviceList;
import org.alfresco.jlan.server.filesys.DiskDeviceContext;
import org.alfresco.jlan.server.filesys.DiskInterface;
import org.alfresco.jlan.server.filesys.DiskSharedDevice;
import org.alfresco.jlan.server.filesys.SrvDiskInfo;
import org.alfresco.jlan.server.filesys.VolumeInfo;
import org.alfresco.jlan.server.filesys.cache.FileStateCache;
import org.alfresco.jlan.server.filesys.cache.StandaloneFileStateCache;
import org.apache.log4j.Logger;
import org.springframework.extensions.config.ConfigElement;
import org.springframework.extensions.config.element.GenericConfigElement;

import com.base.config.ShareConfig;
import com.base.config.UserShareConfig;
import com.fileSystem.exception.LoadFileSystemException;
import com.fileSystem.exception.SharePathException;
import com.util.DBUtil;


public class FilesMappingForDB implements ShareMapper, ConfigurationListener {

	private FileSystemDAO fileSysDao;
	private DiskInterface diskInterface;
	private CacheManager cacheManager;

	private AccessControlManager aclManager;
	private Logger log4j = Logger.getLogger(this.getClass());
	@SuppressWarnings("unused")
	private ExecutorService pool;
	private static ConfigElement usConfigElement;
	
	public static ConfigElement getUsConfigElement() {
		if(null == usConfigElement)
		{
			usConfigElement =  getUSConfigElement();
		}
		return usConfigElement;
	}

	static {
		if(null == usConfigElement)
		{
			usConfigElement =  getUSConfigElement();
		}
	}

	public AccessControlManager getAclManager() {
		return aclManager;
	}

	public void setAclManager(AccessControlManager aclManager) {
		this.aclManager = aclManager;
	}

	public FileSystemDAO getFileSysDao() {
		return fileSysDao;
	}

	public void setFileSysDao(FileSystemDAO fileSysDao) {
		this.fileSysDao = fileSysDao;
	}

	public DiskInterface getDiskInterface() {
		return diskInterface;
	}

	public void setDiskInterface(DiskInterface diskInterface) {
		this.diskInterface = diskInterface;
	}

	public FilesMappingForDB() {

	}

	public void closeMapper() {

	}

	public void deleteShares(SrvSession arg0) {

	}

	public SharedDevice findShare(String host, String name, int typ, SrvSession sess, boolean create) throws LoadFileSystemException {
		log4j.debug("\n\n获得目录FMDB#findShare SharedDevice findShare name="+name+",host="+host);
		ShareConfig shareDTO;
		SharedDevice shareDevice = null;

		try {
			if(name.endsWith(DBUtil.SPECIAL_CHAR))
			{
//				UserShareConfig shareDTOU = fileSysDao.findUserShareByName(name);//用户多会不断查询。查询1000次容易连接不上数据库
//				long totalSpace = 0l;
//				long hasSpace   = 0l;
//				shareDTOU.setFreeSpace(hasSpace);
//				shareDTOU.setTotalSpace(totalSpace);
				
				UserShareConfig shareDTOU = new UserShareConfig();
				shareDTOU.setId(0);
				shareDTOU.setName(name);//直接返回对象本身
				shareDTOU.setStatus(0);
				shareDTOU.setCreatTime("");
				long totalSpace = 0l;
				long hasSpace   = 0l;
				shareDTOU.setFreeSpace(hasSpace);
				shareDTOU.setTotalSpace(totalSpace);
				
				shareDevice = converUserDevice(shareDTOU);
				log4j.debug("findShare#load User ShareDevice "+ shareDTOU.getName()+ " to  ShareList...");
			}
			else
			{
				shareDTO = fileSysDao.findFileByName(name);
				shareDevice = converDevice(shareDTO);
				
				log4j.debug("load LBD  ShareDevice " + name + "");
			}
		} catch (Exception e) {
			log4j.error("error: load share devive failure .. " , e);
		}
		return shareDevice; 
	}

	public SharedDeviceList getShareList(String host, SrvSession sess, boolean allShares) {
		SharedDeviceList shareList = new SharedDeviceList();
		log4j.debug("\nFMDB#getShareList host:"+host+",SrvSession="+sess+" , allShares:"+allShares);
		try {
			List<ShareConfig> configList = fileSysDao.findAllShares();
			if (configList != null) {
				for (ShareConfig shareDTO : configList) {
					long totalSpace = 0l;
					long hasSpace   = 0l;
					try {
						List list = fileSysDao.findDiskSpace(shareDTO.getPool_id(),shareDTO.getPath());
						if(list!=null && list.size()>0)
						{
							for(int i=0;i<list.size();i++)
							{
								totalSpace = (Long) list.get(0);
								hasSpace = (Long) list.get(1);
							}
						}
					} catch (SQLException e) {
						e.printStackTrace();
					}
					shareDTO.setFreeSpace(hasSpace);
					shareDTO.setTotalSpace(totalSpace);
						if (checkSharePath(shareDTO.getPath(),shareDTO.getName())) {
							SharedDevice sharedDevice = converDevice(shareDTO);
							shareList.addShare(sharedDevice);
							
//							log4j.debug("getShareList#load ShareDevice "+ shareDTO.getName()+ " to  ShareList...");
						}
				} 
			}
			
			List<UserShareConfig> userConfigList = fileSysDao.findUserShares();
			if (userConfigList != null) {
				for (UserShareConfig shareDTO : userConfigList) {
					long totalSpace = 0l;
					long hasSpace   = 0l;
					shareDTO.setFreeSpace(hasSpace);
					shareDTO.setTotalSpace(totalSpace);		
					SharedDevice sharedDevice = converUserDevice(shareDTO);
					if(null != sharedDevice)
					{
						shareList.addShare(sharedDevice);	
					}
					
//					log4j.debug("getShareList#load User ShareDevice "+ shareDTO.getName()+ " to  ShareList...");
				} 
			}
		} catch (Exception e) {
			log4j.error("error: load share devive failure .. " ,e);
		}
		return shareList;
	}
	
	
	/**
	 * 转换卷设备
	 * @param config
	 * @return
	 * @throws DeviceContextException
	 * @throws ACLParseException
	 * @throws InvalidACLTypeException
	 * @throws Exception
	 */
	public SharedDevice converDevice(ShareConfig config)
			throws DeviceContextException, ACLParseException,
			InvalidACLTypeException {
	    ConfigElement rootElement = new ConfigElement("Driver", "Driver1");
	    ConfigElement pathElement = new ConfigElement("LocalPath", config.getPath());
	    rootElement.addChild(pathElement);
		DiskDeviceContext context =   (DiskDeviceContext) diskInterface.createContext(config.getName(), rootElement);
		context.setConfigurationParameters(rootElement);
		context.setShareName(config.getName());
		long totalSpace = config.getTotalSpace()/512/64;
		long freeSpace = config.getFreeSpace()/512/64;
		SrvDiskInfo srv = null;
		if(totalSpace>=0&&freeSpace>=0)
		{
			 srv= new SrvDiskInfo(totalSpace, 64, 512, freeSpace);
		}
		else
		{
			// Default to a 8000Gb sized disk with 90% free space
			srv = new SrvDiskInfo(256000000, 64, 512, 230400000);
		}
		
		context.setDiskInformation(srv);
		
		DiskSharedDevice diskDev = new DiskSharedDevice(config.getName(),diskInterface, context);
		GenericConfigElement params = new GenericConfigElement("acl");
		GenericConfigElement user = new GenericConfigElement("name");
		user.setValue("super");
		GenericConfigElement access = new GenericConfigElement("access");
		access.setValue("Write");
		params.addChild(user);
		params.addChild(access);
		aclManager.addAccessControlType(new UserAccessControlParser());
		diskDev.addAccessControl(aclManager.createAccessControl("user", params));
		return diskDev;
		
	}
	
	public static  ConfigElement getUSConfigElement()
	{
		ConfigElement rootElement = new ConfigElement("Driver", "Driver2");
	    ConfigElement classElement = new ConfigElement("class", "org.alfresco.jlan.server.filesys.db.DBDiskLDriver");		
	    ConfigElement pathElement = new ConfigElement("LocalPath", "/usr/linkapp/bin");
	    ConfigElement cacheTimeElement = new ConfigElement("CacheTime", "-1");
	    ConfigElement debugElement = new ConfigElement("Debug", "");
	    rootElement.addChild(classElement);
	    rootElement.addChild(pathElement);
	    rootElement.addChild(cacheTimeElement);
	    rootElement.addChild(debugElement);
	    ConfigElement databaseInterfaceElement = new ConfigElement("DatabaseInterface", "");
	    ConfigElement d_classElement = new ConfigElement("class", "org.alfresco.jlan.server.filesys.db.mysql.MySQLDBLInterface");
	    ConfigElement d_FileSystemTableElement = new ConfigElement("FileSystemTable", "jweb_commonfilecache");
	    ConfigElement d_FileRevisionTableElement = new ConfigElement("FileRevisionTable", "jweb_commonfilerevision");
	    ConfigElement d_StreamsTableElement = new ConfigElement("StreamsTable", "jweb_JLANStreamsCommon");
	    ConfigElement d_RetentionTableElement = new ConfigElement("RetentionTable", "jweb_JLANRetainCommon");
	    ConfigElement d_QueueTableTableElement = new ConfigElement("QueueTable", "jweb_JLANQueueCommon");
	    ConfigElement d_TransactQueueTableElement = new ConfigElement("TransactQueueTable", "jweb_JLANTransQueueCommon");
	    ConfigElement d_DataTableElement = new ConfigElement("DataTable", "jweb_JLANDataCommon");	
	    databaseInterfaceElement.addChild(d_classElement);
	    databaseInterfaceElement.addChild(d_FileSystemTableElement);
	    databaseInterfaceElement.addChild(d_FileRevisionTableElement);
	    databaseInterfaceElement.addChild(d_StreamsTableElement);
	    databaseInterfaceElement.addChild(d_RetentionTableElement);
	    databaseInterfaceElement.addChild(d_QueueTableTableElement);
	    databaseInterfaceElement.addChild(d_TransactQueueTableElement);
	    databaseInterfaceElement.addChild(d_DataTableElement);
	    databaseInterfaceElement.addChild(debugElement);
	    
	    rootElement.addChild(databaseInterfaceElement);
	    
	    ConfigElement FileLoaderElement = new ConfigElement("FileLoader", "");
	    ConfigElement f_classElement = new ConfigElement("class", "org.alfresco.jlan.server.filesys.db.DBFileLoader");
	    ConfigElement f_ThreadPoolSizeElement = new ConfigElement("ThreadPoolSize", "6:2");
	    ConfigElement f_TempDirectoryElement = new ConfigElement("TempDirectory", "/usr/linkapp/data/linkapp/archive/view/smbcommon");
	    ConfigElement f_MaximumFilesPerDirectoryElement = new ConfigElement("MaximumFilesPerDirectory", "1000");
	    FileLoaderElement.addChild(f_classElement);
	    FileLoaderElement.addChild(f_ThreadPoolSizeElement);
	    FileLoaderElement.addChild(f_TempDirectoryElement);
	    FileLoaderElement.addChild(f_MaximumFilesPerDirectoryElement);
	    FileLoaderElement.addChild(debugElement);
	    
	    rootElement.addChild(FileLoaderElement);
	    
	    return rootElement;
	}
	
	/**
	 * 转换卷设备
	 * @param config
	 * @return
	 * @throws DeviceContextException
	 * @throws ACLParseException
	 * @throws InvalidACLTypeException
	 * @throws Exception
	 */
	public SharedDevice converUserDevice(UserShareConfig config)
			throws DeviceContextException, ACLParseException,
			InvalidACLTypeException {
	    ConfigElement rootElement = getUsConfigElement();
	    
//		DiskDeviceContext context =   (DiskDeviceContext) diskInterface.createContext(config.getName(), rootElement);
//		context.setConfigurationParameters(rootElement);
//		context.setShareName(config.getName());
//		long totalSpace = config.getTotalSpace()/512/64;
//		long freeSpace = config.getFreeSpace()/512/64;
//		SrvDiskInfo srv = null;
//		if(totalSpace>=0&&freeSpace>=0)
//		{
//			 srv= new SrvDiskInfo(totalSpace, 64, 512, freeSpace);
//		}
//		else
//		{
//			 srv= new SrvDiskInfo(2560000, 64, 512, 2304000);
//		}
//		
//		context.setDiskInformation(srv);
		
//		DiskSharedDevice diskDev = new DiskSharedDevice(config.getName(),diskInterface, context);
//		GenericConfigElement params = new GenericConfigElement("acl");
//		GenericConfigElement user = new GenericConfigElement("name");
//		user.setValue("super");
//		GenericConfigElement access = new GenericConfigElement("access");
//		access.setValue("Write");
//		params.addChild(user);
//		params.addChild(access);
//		aclManager.addAccessControlType(new UserAccessControlParser());
//		diskDev.addAccessControl(aclManager.createAccessControl("user", params));
//		return diskDev;
	    
	    Object drvObj = null;
	    try{
	    	drvObj = Class.forName("org.alfresco.jlan.server.filesys.db.DBDiskLDriver").newInstance();
	    }
	    catch(Exception e)
	    {
	    	log4j.error("converUserDevice Exception !!",e);
	    }
		// Create volume information using the share name
		VolumeInfo volInfo = new VolumeInfo(config.getName(), (int) System.currentTimeMillis(), new Date(System.currentTimeMillis()));
		// Default to a 8000Gb sized disk with 90% free space
		SrvDiskInfo diskInfo = new SrvDiskInfo(256000000, 64, 512, 230400000);
		
		FileStateCache stateCache = null;
		if ( drvObj instanceof DiskInterface) {
			// Create the driver

			DiskInterface diskDrv = (DiskInterface) drvObj;

			// Create a context for this share instance, save the configuration parameters as
			// part of the context

			DiskDeviceContext devCtx = (DiskDeviceContext) diskDrv.createContext(config.getName(), rootElement);
			devCtx.setConfigurationParameters(rootElement);
			// Enable/disable change notification for this device
//			devCtx.enableChangeHandler(true);
			devCtx.enableChangeHandler(false);
			// Set the volume information, may be null
			devCtx.setVolumeInformation(volInfo);
			// Set the disk sizing information, may be null
			devCtx.setDiskInformation(diskInfo);

			// Set the share name in the context

			devCtx.setShareName(config.getName());
			
			if ( devCtx.requiresStateCache() && stateCache == null) {
				stateCache = new StandaloneFileStateCache();
			}
			devCtx.setStateCache( stateCache);
			
			DiskSharedDevice diskDev = new DiskSharedDevice(config.getName(),diskDrv, devCtx);
			GenericConfigElement params = new GenericConfigElement("acl");
			GenericConfigElement user = new GenericConfigElement("name");
//			user.setValue("super");
			user.setValue(config.getName().replace(DBUtil.SPECIAL_CHAR, ""));
			GenericConfigElement access = new GenericConfigElement("access");
			access.setValue("Write");
			params.addChild(user);
			params.addChild(access);
			aclManager.addAccessControlType(new UserAccessControlParser());
			diskDev.addAccessControl(aclManager.createAccessControl("user", params));
//			devCtx.startFilesystem(diskDev);
			return diskDev;
		}
		else
		{
			log4j.debug(" drvObj not instanceof DiskInterface");
		}
		return null;	
	}
	
	

	/**
	 * 检查共享路径
	 * 
	 * @param path
	 * @return
	 * @throws SharePathException
	 */
	public boolean checkSharePath(String path,String name)
			throws SharePathException {
		File file = new File(path);
		if (!file.exists())// file不存在
		{
			log4j.info(" loadfile: share path  : " + path + "  not  exist!");
		} else// file存在
		{
			if (file.isDirectory())// file是目录（此file一般为目录不可能为单独的文件）
			{
//				pool = Executors.newFixedThreadPool(1);
//				FileWatch filWatch = new FileWatch(path,name);
//				Thread thread = new Thread(filWatch);
//				thread.setDaemon(true);// 守护进程
//				pool.submit(thread);
				return true;
			}
		}
		return false;
	}

	public void initializeMapper(ServerConfiguration config,
			org.springframework.extensions.config.ConfigElement params)
			throws InvalidConfigurationException {
		log4j.debug("\nFMDB#initializeMapper ServerConfiguration:"+config+",ConfigElement="+params);
		
	}

	public int configurationChanged(int id, ServerConfiguration config,
			Object newVal) throws InvalidConfigurationException {
		log4j.debug("\nFMDB#initializeMapper ServerConfiguration:"+config+",newVal="+newVal+" , id:"+id);
		// TODO Auto-generated method stub
		return 0;
	}

	public CacheManager getCacheManager() {
		return cacheManager;
	}

	public void setCacheManager(CacheManager cacheManager) {
		this.cacheManager = cacheManager;
	}
	
}