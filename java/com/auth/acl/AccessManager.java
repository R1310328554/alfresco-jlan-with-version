package com.auth.acl;

import java.util.Enumeration;

import org.alfresco.jlan.debug.Debug;
import org.alfresco.jlan.server.SrvSession;
import org.alfresco.jlan.server.auth.ClientInfo;
import org.alfresco.jlan.server.auth.acl.ACLParseException;
import org.alfresco.jlan.server.auth.acl.AccessControl;
import org.alfresco.jlan.server.auth.acl.AccessControlFactory;
import org.alfresco.jlan.server.auth.acl.AccessControlManager;
import org.alfresco.jlan.server.auth.acl.AccessControlParser;
import org.alfresco.jlan.server.auth.acl.InvalidACLTypeException;
import org.alfresco.jlan.server.config.InvalidConfigurationException;
import org.alfresco.jlan.server.config.ServerConfiguration;
import org.alfresco.jlan.server.core.ShareMapper;
import org.alfresco.jlan.server.core.ShareType;
import org.alfresco.jlan.server.core.SharedDevice;
import org.alfresco.jlan.server.core.SharedDeviceList;
import org.alfresco.jlan.server.filesys.DiskDeviceContext;
import org.alfresco.jlan.server.filesys.DiskInterface;
import org.alfresco.jlan.server.filesys.DiskSharedDevice;
import org.alfresco.jlan.server.filesys.FilesystemsConfigSection;
import org.alfresco.jlan.server.filesys.cache.FileStateCache;
import org.alfresco.jlan.server.filesys.cache.StandaloneFileStateCache;
import org.apache.log4j.Logger;
import org.springframework.extensions.config.ConfigElement;
import org.springframework.extensions.config.element.GenericConfigElement;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.auth.acl.jdbc.DBAccessLoader;
import com.auth.acl.jdbc.Exception.LoadAccessException;
import com.auth.acl.jdbc.Exception.ParseAccessException;
import com.run.VirtuaServiceRun;
import com.util.DBUtil;

public class AccessManager implements AccessControlManager {

	private AccessControlFactory m_factory;

	private DBAccessLoader accessLoder;

	private ShareMapper shareMapper;

	private Logger log4j = Logger.getLogger(this.getClass());

	public AccessManager() {
		m_factory = new AccessControlFactory();
	}

	public ShareMapper getShareMapper() {
		log4j.debug(" AccessManager.getShareMapper = " + shareMapper);
		return shareMapper;
	}

	public void setShareMapper(ShareMapper shareMapper) {
		log4j.debug(" AccessManager.setShareMapper = " + shareMapper);
		this.shareMapper = shareMapper;
	}

	public DBAccessLoader getAccessLoder() {
		log4j.debug(" AccessManager.getAccessLoder = " + accessLoder);
		return accessLoder;
	}

	public void setAccessLoder(DBAccessLoader accessLoder) {
		log4j.debug(" AccessManager.setAccessLoder = " + accessLoder);
		this.accessLoder = accessLoder;
	}

	public void addAccessControlType(AccessControlParser arg0) {
//		log4j.debug(" AccessManager.addAccessControlType = " + arg0);
		m_factory.addParser(arg0);
	}

	public int checkAccessControl(SrvSession session, SharedDevice share) {
		
		log4j.debug("\n\n********checkAccessControl  for  session : " + session+" , SharedDevice="+share);
		
		int accessOpt = 0;
		ClientInfo info = session.getClientInformation();
		String uname = info.getUserName();
		log4j.debug(" checkAccess  for  user : " + uname);
		try {
			if ("".equals(uname))
				return accessOpt;
			if (!accessLoder.hasAccess(uname, share.getName()))
				return accessOpt;

			accessOpt = parseAuthority(uname, share.getName());
		} catch (ParseAccessException ex) {
			log4j.error("error: ParseAccess Exception!" + ex);

		} catch (LoadAccessException e) {
			log4j.error("error: LoadAccess Exception!" + e);
		}

		return accessOpt;
	}

	public AccessControl createAccessControl(String type, ConfigElement params)
			throws ACLParseException, InvalidACLTypeException {
//		log4j.debug(" AccessManager.createAccessControl = " + type);
		return m_factory.createAccessControl("user", params);
	}

	@SuppressWarnings("unchecked")
	public SharedDeviceList filterShareList(SrvSession sess,
			SharedDeviceList shares) {

		log4j.debug("\n\n过滤共享　;SrvSession="+sess+" ; SharedDeviceList="+shares);
		@SuppressWarnings("unused")
		Authentication authContext = SecurityContextHolder.getContext().getAuthentication();
//		SharedDeviceList shareList = null;
//		try{
//			shareList = shareMapper.getShareList(null, sess, false);
//		}catch(Exception e){
//			log4j.error("filterShareList ERROR : shareList is null ",e);
//		}
//		if(shareList == null){
//			log4j.error("×××××××××××  当前存储池为空  ×××××××××××");
//		}
//		FilesystemsConfigSection filesysConfig = (FilesystemsConfigSection) VirtuaServiceRun.xmlConfig.getConfigSection("Filesystems");
		ClientInfo info = sess.getClientInformation();
//		 System.out.println("[list]:"+shares.toString());
		String uname = info.getUserName();
		SharedDeviceList filterList = new SharedDeviceList();
		try {
//		Enumeration enm = shareList.enumerateShares();
		Enumeration enm = shares.enumerateShares();
		log4j.debug("uname="+uname+" ; ClientInfo="+info);
			boolean isContainUserDevice = false;//
			while (enm.hasMoreElements()) {
				SharedDevice device = (SharedDevice) enm.nextElement();
				if((uname+DBUtil.SPECIAL_CHAR).equalsIgnoreCase(device.getName()))
				{
					isContainUserDevice = true;//包含用户的共享分区
				}
				int parsedAccess = parseAuthority(uname, device.getName());
//				filesysConfig.addShare(device);
				if (parsedAccess > AccessControl.NoAccess)
					filterList.addShare(device);
				if (device.getInterface() instanceof DiskInterface) {
					DiskSharedDevice diskDev = (DiskSharedDevice)device;
					DiskDeviceContext devCtx = diskDev.getDiskContext();
					FileStateCache stateCache = null;
					if ( devCtx.requiresStateCache() && stateCache == null) {
						stateCache = new StandaloneFileStateCache();
						stateCache.initializeCache( new GenericConfigElement( "stateCache"),null);
					}
					
					if ( devCtx.requiresStateCache() == false && stateCache != null)
						throw new InvalidConfigurationException( "Filesystem does not use state caching");

					devCtx.setStateCache( stateCache);
					
					diskDev.setConfiguration( null);
//	                if (devCtx.hasStateCache()) {
//	                	filesysConfig.addFileStateCache( diskDev.getName(),devCtx.getStateCache());
//	                }
					devCtx.startFilesystem(diskDev);
					if ( devCtx.hasStateCache())
						devCtx.getStateCache().setDriverDetails(diskDev);
//					filesysConfig.addShare(diskDev);
				 }	
			}
			if(isContainUserDevice==false)
			{
				log4j.debug(" 不包含用户的盘: " + uname + " 重新查询用户的的盘　");
				/*
				 如果不包含先重新包含的 ,用户网盘 username@
				 */
				SharedDevice device = shareMapper.findShare(null, uname+DBUtil.SPECIAL_CHAR, ShareType.DISK, sess, true);
				log4j.debug(uname+" 返回的device: " + device);
				if(null !=device)
				{
					FilesystemsConfigSection filesysConfig = (FilesystemsConfigSection) VirtuaServiceRun.xmlConfig.getConfigSection("Filesystems");
					
					filterList.addShare(device);
					if (device.getInterface() instanceof DiskInterface) {
						DiskSharedDevice diskDev = (DiskSharedDevice)device;
						DiskDeviceContext devCtx = diskDev.getDiskContext();
						FileStateCache stateCache = null;
						if ( devCtx.requiresStateCache() && stateCache == null) {
							stateCache = new StandaloneFileStateCache();
							stateCache.initializeCache( new GenericConfigElement( "stateCache"),null);
						}
						
						if ( devCtx.requiresStateCache() == false && stateCache != null)
							throw new InvalidConfigurationException( "Filesystem does not use state caching");

						devCtx.setStateCache( stateCache);
						
						diskDev.setConfiguration( null);
		                if (devCtx.hasStateCache()) {
		                	filesysConfig.addFileStateCache( diskDev.getName(),devCtx.getStateCache());
		                }
		                devCtx.startFilesystem(diskDev);
		                if ( devCtx.hasStateCache()) {
		                	filesysConfig.addFileStateCache( diskDev.getName(), devCtx.getStateCache());
		                }
						if ( devCtx.hasStateCache())
							devCtx.getStateCache().setDriverDetails(diskDev);
						filesysConfig.addShare(diskDev);
					 }	
				}
			}

		} catch (Exception ex) {
//			ex.printStackTrace();
		}

		log4j.debug(" user " + uname + " filtred  ShareDevice:" + filterList.toString());
		return filterList;
	}

	public void initialize(ServerConfiguration arg0, ConfigElement arg1)
			throws InvalidConfigurationException {
		log4j.debug("call  initialize method  ");

	}

	public int parseAuthority(String Username, String shareName)
			throws ParseAccessException {
		int accessVal = 0;
		try {
			String accessStr = accessLoder.LoadAccessByName(Username,shareName);
			if (!"".equals(accessStr)) {
				if (accessStr.indexOf("w") != -1)
					accessVal = AccessControl.ReadWrite;
				else if (accessStr.indexOf("r") != -1)
					accessVal = AccessControl.ReadOnly;
			}
		} catch (LoadAccessException e) {
			log4j.error("error: LoadAccess failure .. " + e);

		}
		log4j.debug("[0: Denied ,1: ReadOnly,2:ReadWrite]  username :"
					+ Username + " for " + shareName + " AccessValue is "
					+ accessVal);
		return accessVal;
	}

}

