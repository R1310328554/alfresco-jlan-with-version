package com.run;

import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.alfresco.jlan.server.NetworkServer;
import org.alfresco.jlan.server.SrvSession;
import org.alfresco.jlan.server.SrvSessionList;
import org.alfresco.jlan.server.auth.ClientInfo;
import org.alfresco.jlan.server.core.DeviceContext;
import org.alfresco.jlan.server.core.DeviceInterface;
import org.alfresco.jlan.server.core.InvalidDeviceInterfaceException;
import org.alfresco.jlan.server.core.ShareMapper;
import org.alfresco.jlan.server.filesys.DiskInterface;
import org.alfresco.jlan.server.filesys.TreeConnection;
import org.alfresco.jlan.server.filesys.cache.FileState;
import org.alfresco.jlan.server.filesys.cache.FileStateCache;
import org.alfresco.jlan.server.filesys.db.DBDeviceContext;
import org.alfresco.jlan.server.filesys.db.DBDiskLDriver;
import org.alfresco.jlan.smb.server.SMBServer;
import org.alfresco.jlan.smb.server.SMBSrvSession;
import org.alfresco.jlan.smb.server.VirtualCircuit;
import org.alfresco.jlan.smb.server.VirtualCircuitList;
import org.apache.log4j.Logger;

import com.util.SpringUtil;

public class VirtuaServerSyncServlet extends HttpServlet {

	private static final long serialVersionUID = -5532731354927539612L;
	
	private static Logger log4j = Logger.getLogger(VirtuaServerSyncServlet.class);

	private static SMBServer smbserv;

	private static NetworkServer netBIOS;
	
	public void init() throws ServletException {
		smbserv = (SMBServer) SpringUtil.getBean("smbService");
		netBIOS = (NetworkServer) SpringUtil.getBean("netBIOS");
		System.out.println("******** 启动调用  VirtuaServerSyncServlet init ********");
	}
	
	
	
	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		log4j.debug("VirtuaServerSyncServlet.service() start! ");
		
		String userId = req.getParameter("userId");
		String fileVirtualPath = req.getParameter("filePath");
		
		String shareName = req.getParameter("shareName");
		String fileId = req.getParameter("fileId");
		String fileName = req.getParameter("fileName");
		String pathId = req.getParameter("pathId");
		fileVirtualPath = fileVirtualPath + "/" + fileName;
		fileVirtualPath = fileVirtualPath.replace("/", "\\");
		
		
		boolean b = notifyAll(userId, pathId, fileId, fileName, fileVirtualPath, shareName);
		
		if (b) {
			resp.getWriter().write("Seems ok!");
		} else {
			resp.getWriter().write("Seems sth wrong!!!");
		}
		
	}
	
	public synchronized static boolean notifyOthers(int userId, int pathId, int fileId, String fileVirtualPath, String fileName, String shareName) {
		

		smbserv = (SMBServer) SpringUtil.getBean("smbService");
		netBIOS = (NetworkServer) SpringUtil.getBean("netBIOS");
		
		//ShareMapper mapper = smbserv.getShareMapper();

		boolean ret = false;
		SrvSessionList sessions = smbserv.getSessions();
		Enumeration<SrvSession> sss = sessions.enumerateSessions();
		while(sss.hasMoreElements()) {
			SMBSrvSession sess = (SMBSrvSession) sss.nextElement();
			
			if (sess ==null) {
				continue;
			}
			
			VirtualCircuitList m_vcircuits = sess.getVcircuits();
			
			for (int j = 0; m_vcircuits != null && j < m_vcircuits.getCircuitCount(); j++) {
				Collection<VirtualCircuit> values = m_vcircuits.getVcircuits().values();
				for (Iterator iterator = values.iterator(); iterator.hasNext();) {
					VirtualCircuit virtualCircuit = (VirtualCircuit) iterator.next();

					if (virtualCircuit ==null) {
						continue;
					}

					ClientInfo clientInfo = virtualCircuit.getClientInformation();
					if (clientInfo == null) {
						log4j.error("NOTIFYOTHERS ERR ");
						continue;
					}
					int uid = clientInfo.getUid();
					// int uid = sess.getClientInformation().getUid(); 似乎永远为 -1，wth
					String un = clientInfo.getUserName();
					if (uid == userId) {
						log4j.debug("CONTINUED ++++ NOTIFYOTHERS uid: " + uid + "  UserName: " + un);
						continue;
					} else {
						log4j.debug("NOTIFYOTHERS uid: " + uid + "  UserName: " + un);
					}
					
					int connectionCount = virtualCircuit.getConnectionCount();
					if (connectionCount>0) {
						Map<Integer, TreeConnection> cc = virtualCircuit.getConnections();
						if (cc != null && !cc.isEmpty()) {
							Collection<TreeConnection> ccv = cc.values();
							if (ccv != null) 
							for (TreeConnection conn : ccv) {
								DBDiskLDriver disk = null;
								try {
									
									disk = (DBDiskLDriver) conn.getSharedDevice().getInterface();
									if (disk != null) {
										log4j.debug("VirtuaServerSyncServlet.service() 11 ");
										disk.reloadFile(fileVirtualPath, shareName, conn);
										ret = true;
									}
									
								} catch (InvalidDeviceInterfaceException e) {
									// TODO Auto-generated catch block
									log4j.warn("InvalidDeviceInterfaceException:" + e.toString());
									e.printStackTrace();
								} catch (Exception e) {
									log4j.error("VirtuaServerSyncServlet Exception:" + e.toString());
									e.printStackTrace();
								}
							}
							
						}
						
					}
				}
			}
		}
		
		
		return ret;
	}


	/**
	 * 
	 * 
	 * @param pathId
	 * @param fileId
	 * @param fileName
	 * @param fileVirtualPath
	 * @param shareName
	 * @return
	 */
	public synchronized static boolean isFileOpen(long userId, String fileVirtualPath, String shareName) {

		smbserv = (SMBServer) SpringUtil.getBean("smbService");
		netBIOS = (NetworkServer) SpringUtil.getBean("netBIOS");
		
		boolean ret = false;
		SrvSessionList sessions = smbserv.getSessions();
		Enumeration<SrvSession> sss = sessions.enumerateSessions();
		
		while(sss.hasMoreElements()) {
			SMBSrvSession sess = (SMBSrvSession) sss.nextElement();
			
			if (sess ==null) {
				continue;
			}
			
			VirtualCircuitList m_vcircuits = sess.getVcircuits();
		
			for (int j = 0; m_vcircuits!=null && j < m_vcircuits.getCircuitCount(); j++) {
				Collection<VirtualCircuit> values = m_vcircuits.getVcircuits().values();
				for (Iterator iterator = values.iterator(); iterator.hasNext();) {
					
					VirtualCircuit virtualCircuit = (VirtualCircuit) iterator.next();
					
					if (virtualCircuit == null) {
						continue;
					}
					ClientInfo clientInfo = virtualCircuit.getClientInformation();
					if (clientInfo == null) {
						log4j.error("ISFILEOPEN ERR ");
						continue;
					}
					int uid = clientInfo.getUid();
					String un = clientInfo.getUserName();
					
					if (uid == userId) {
						log4j.debug("CONTINUED ++++ ISFILEOPEN uid: " + uid + "  UserName: " + un);
						continue;
					} else {
						log4j.debug("ISFILEOPEN uid: " + uid + "  UserName: " + un);
					}
					
					int connectionCount = virtualCircuit.getConnectionCount();
					
					if (connectionCount>0) {
						Map<Integer, TreeConnection> cc = virtualCircuit.getConnections();
						if (cc != null && !cc.isEmpty()) {
							Collection<TreeConnection> ccv = cc.values();
							if (ccv != null) 
							for (TreeConnection conn : ccv) {
								try {
									DBDeviceContext context = (DBDeviceContext) conn.getContext();
									if (context != null) {
										FileStateCache cache = context.getStateCache();
										if (cache != null) {
											FileState fileState = cache.findFileState(fileVirtualPath, false);
											if (fileState != null) {
												int count = fileState.getOpenCount();
												if (count > 0) {
													int sts = fileState.getDataStatus();
													if (sts > FileState.FILE_LOADING) {
														log4j.debug("VirtuaServerSyncServlet.service() ISFILEOPEN ");
														ret  = true;
														return ret;
													}
												}
												//break;
											}
										}
										
									}
									
								} catch (Exception e) {
									// TODO Auto-generated catch block
									// 可能是云编辑报的错， 忽略即可。
									log4j.error("ERROR:" + e.toString());
									e.printStackTrace();
								}
								
							}
							
						}
						
					}
				}
			}
		}
		
		return ret;
	}

	
	private synchronized static boolean notifyAll(String userId, String pathId, String fileId, String fileName, String fileVirtualPath, String shareName) {

		smbserv = (SMBServer) SpringUtil.getBean("smbService");
		netBIOS = (NetworkServer) SpringUtil.getBean("netBIOS");
		
		//ShareMapper mapper = smbserv.getShareMapper();
		
		SrvSessionList sessions = smbserv.getSessions();
		Enumeration<SrvSession> sss = sessions.enumerateSessions();
		

		boolean ret = false;
		int sessLength = 1;
		while(sss.hasMoreElements()) {
			SMBSrvSession sess = (SMBSrvSession) sss.nextElement();
			
			if (sess ==null) {
				continue;
			}
			
			VirtualCircuitList m_vcircuits = sess.getVcircuits();
			
			for (int j = 0; m_vcircuits!=null && j < m_vcircuits.getCircuitCount(); j++) {
				Collection<VirtualCircuit> values = m_vcircuits.getVcircuits().values();
				for (Iterator iterator = values.iterator(); iterator.hasNext();) {
					VirtualCircuit virtualCircuit = (VirtualCircuit) iterator.next();
					if (virtualCircuit ==null) {
						continue;
					}
					
//					int uid = virtualCircuit.getUID();
//					String un = sess.getClientInformation()==null?" xxx ":sess.getClientInformation().getUserName();
					
					ClientInfo clientInfo = virtualCircuit.getClientInformation();
					if (clientInfo == null) {
						log4j.error("NOTIFYALL ERR ");
						continue;
					}
					int uid = clientInfo.getUid();
					String un = clientInfo.getUserName();
					log4j.debug("NOTIFYALL uid: " + uid + "  UserName: " + un);
					
					int connectionCount = virtualCircuit.getConnectionCount();
					if (connectionCount>0) {
						Map<Integer, TreeConnection> cc = virtualCircuit.getConnections();
						if (cc != null && !cc.isEmpty()) {
							Collection<TreeConnection> ccv = cc.values();
							if (ccv != null) 
							for (TreeConnection conn : ccv) {
								DBDiskLDriver disk = null;
								try {
									disk = (DBDiskLDriver) conn.getSharedDevice().getInterface();
									
									if (disk != null) {
										log4j.debug("VirtuaServerSyncServlet.service() 11 ");
										disk.reloadFile(fileVirtualPath, shareName, conn);
										ret = true;
									}
								} catch (InvalidDeviceInterfaceException e) {
									// TODO Auto-generated catch block
									// 可能是云编辑报的错， 忽略即可。
									log4j.warn("InvalidDeviceInterfaceException:" + e.toString());
									e.printStackTrace();
								} catch (Exception e) {
									log4j.error("VirtuaServerSyncServlet Exception:" + e.toString());
									e.printStackTrace();
								}
								
							}
							
						}
						
					}
				}
			}
		}
		
		return ret ;
	}
	
	public void destroy() {
		System.out.println("******** 停止调用	 VirtuaServerSyncServlet destroy ********");
	}


}
