/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */

package org.alfresco.jlan.server.filesys.db;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import org.alfresco.jlan.locking.LockConflictException;
import org.alfresco.jlan.server.SrvSession;
import org.alfresco.jlan.server.core.DeviceContext;
import org.alfresco.jlan.server.core.DeviceContextException;
import org.alfresco.jlan.server.filesys.AccessDeniedException;
import org.alfresco.jlan.server.filesys.DiskDeviceContext;
import org.alfresco.jlan.server.filesys.DiskFullException;
import org.alfresco.jlan.server.filesys.DiskInterface;
import org.alfresco.jlan.server.filesys.DiskOfflineException;
import org.alfresco.jlan.server.filesys.DiskSizeInterface;
import org.alfresco.jlan.server.filesys.DiskVolumeInterface;
import org.alfresco.jlan.server.filesys.FileAccessToken;
import org.alfresco.jlan.server.filesys.FileAttribute;
import org.alfresco.jlan.server.filesys.FileExistsException;
import org.alfresco.jlan.server.filesys.FileIdInterface;
import org.alfresco.jlan.server.filesys.FileInfo;
import org.alfresco.jlan.server.filesys.FileName;
import org.alfresco.jlan.server.filesys.FileNameException;
import org.alfresco.jlan.server.filesys.FileOfflineException;
import org.alfresco.jlan.server.filesys.FileOpenParams;
import org.alfresco.jlan.server.filesys.FileSharingException;
import org.alfresco.jlan.server.filesys.FileStatus;
import org.alfresco.jlan.server.filesys.FileType;
import org.alfresco.jlan.server.filesys.NetworkFile;
import org.alfresco.jlan.server.filesys.SearchContext;
import org.alfresco.jlan.server.filesys.SecurityDescriptorInterface;
import org.alfresco.jlan.server.filesys.SrvDiskInfo;
import org.alfresco.jlan.server.filesys.SymbolicLinkInterface;
import org.alfresco.jlan.server.filesys.TreeConnection;
import org.alfresco.jlan.server.filesys.VolumeInfo;
import org.alfresco.jlan.server.filesys.cache.FileState;
import org.alfresco.jlan.server.filesys.cache.FileStateCache;
import org.alfresco.jlan.server.filesys.loader.FileRequest;
import org.alfresco.jlan.server.filesys.loader.FileRequestQueue;
import org.alfresco.jlan.server.filesys.loader.FileSegment;
import org.alfresco.jlan.server.filesys.loader.FileSegmentInfo;
import org.alfresco.jlan.server.filesys.loader.NamedFileLoader;
import org.alfresco.jlan.server.filesys.loader.SingleFileRequest;
import org.alfresco.jlan.server.filesys.quota.QuotaManager;
import org.alfresco.jlan.server.locking.FileLockingInterface;
import org.alfresco.jlan.server.locking.LockManager;
import org.alfresco.jlan.server.locking.OpLockInterface;
import org.alfresco.jlan.server.locking.OpLockManager;
import org.alfresco.jlan.smb.SharingMode;
import org.alfresco.jlan.smb.WinNT;
import org.alfresco.jlan.smb.nt.SecurityDescriptor;
import org.alfresco.jlan.smb.server.SMBSrvException;
import org.alfresco.jlan.smb.server.ntfs.NTFSStreamsInterface;
import org.alfresco.jlan.smb.server.ntfs.StreamInfo;
import org.alfresco.jlan.smb.server.ntfs.StreamInfoList;
import org.alfresco.jlan.util.MemorySize;
import org.alfresco.jlan.util.WildCard;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.extensions.config.ConfigElement;

import com.base.config.UserBean;
import com.util.DBUtil;
import com.util.DiskUtil;

/**
 * Database Disk Driver Class
 *
 * @author gkspencer
 */
public class DBDiskDriver implements DiskInterface, DiskSizeInterface, DiskVolumeInterface, NTFSStreamsInterface,
  FileLockingInterface, FileIdInterface, SymbolicLinkInterface, OpLockInterface, SecurityDescriptorInterface {

	private Logger log4j = Logger.getLogger(this.getClass());
  //  Attributes attached to the file state
  
  public static final String DBStreamList   = "DBStreamList";
  
  //  Default mode values for files/folders, if not specified in the file/folder create parameters
  
  public static final int DefaultNFSFileMode    = 0644;
  public static final int DefaultNFSDirMode     = 0755;
  
  //  Maximum file name length
  
  public static final int MaxFileNameLen  = 255;
  
  //  Maximum timestamp value to allow for file timestamps (01-Jan-2030 00:00:00)
  
  public static final long MaxTimestampValue	= 1896134400000L;
  
  //  Enable/disable debug output
  
  private boolean m_debug = false;
  
  /**
   * Close the specified file
   * 
   * @param sess  Session details
   * @param tree  Tree connection
   * @param file  Network file details
   * @exception IOException
   */
  public void closeFile(SrvSession sess, TreeConnection tree, NetworkFile file)
    throws IOException {
	
    //  Access the database context
    DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();
    String shareName = tree.getContext().getShareName();
    //  Check if the file is an NTFS stream
    
    if ( file.isStream()) {
      
      //  Close the NTFS stream
      
      closeStream(sess, tree, file);
      
      //  Check if the stream is marked for deletion
      
      if ( file.hasDeleteOnClose())
        deleteStream(sess, tree, file.getFullNameStream());
      return;    
    }
    
    //  Debug
//	log4j.debug("DBD#closeFile() file=" + file.getFullName());
   
      
    //  Close the file

    dbCtx.getFileLoader().closeFile(sess, file);
    file.setClosed( true);
    
    //  Access the JDBC file
    
    DBNetworkFile jdbcFile = null;
    
    if ( file instanceof DBNetworkFile) {
      
      //  Access the JDBC file
      
      jdbcFile = (DBNetworkFile) file;

      //  Decrement the open file count
      
      FileState fstate = jdbcFile.getFileState();

      //  Check if the file state is valid, if not then check the main file state cache

      if ( fstate == null) {
        
        //  Check the main file state cache
              
        fstate = getFileState(file.getFullName(), dbCtx, false);

        //  DEBUG
//        log4j.debug("** Last file close, no file state for " + file.getFullName());
      }
      else {
        
        // If the file open count is now zero then reset the stored sharing mode
          
        if ( dbCtx.getStateCache().releaseFileAccess(fstate, file.getAccessToken()) == 0) {
          
          //  DEBUG
//        	log4j.debug("** Last file close, reset shared access for " + file.getFullName() + ", state=" + fstate);
        }
        else
        {
//        	log4j.debug("** File close, file=" + file.getFullName() + ", openCount=" + fstate.getOpenCount());
        }
        
        // Check if there is an oplock on the file
        
        if ( jdbcFile.hasOpLock()) {
        	
        	// Release the oplock
        	
            OpLockInterface flIface = (OpLockInterface) this;
            OpLockManager oplockMgr = flIface.getOpLockManager(sess, tree);
            
            oplockMgr.releaseOpLock( jdbcFile.getOpLock().getPath());

            //  DEBUG
            log4j.debug("Released oplock for closed file, file=" + jdbcFile.getFullName());
        }

        // Clear the access token
        
        file.setAccessToken( null);
      }

      //  Release any locks on the file owned by this session
      
      if ( jdbcFile.hasLocks()) {
        
        //  Get the lock manager
        
        FileLockingInterface flIface = (FileLockingInterface) this;
        LockManager lockMgr = flIface.getLockManager(sess, tree);
        
        //  DEBUG
        log4j.debug("Releasing locks for closed file, file=" + jdbcFile.getFullName() + ", locks=" + jdbcFile.numberOfLocks());
          
        //  Release all locks on the file owned by this session
        
        lockMgr.releaseLocksForFile(sess, tree, file);
      }
      
      //  Check if we have a valid file state
            
      if ( fstate != null) {
        
        //  Update the cached file size
        
        DBFileInfo finfo = (DBFileInfo) fstate.findAttribute(FileState.FileInformation);
        if ( finfo != null && file.getWriteCount() > 0) {
          
          //  Update the file size
          
          finfo.setSize(jdbcFile.getFileSize());
          
          //  Update the modified date/time
          
          finfo.setModifyDateTime(jdbcFile.getModifyDate());
          
          //  DEBUG
          log4j.debug("  File size=" + jdbcFile.getFileSize() + ", modifyDate=" + jdbcFile.getModifyDate());
        }

        //  DEBUG
//        log4j.debug("  Open count=" + jdbcFile.getFileState().getOpenCount());
      }
      
      //  Check if the file/directory is marked for delete
      
      if ( file.hasDeleteOnClose()) {
        
        //  Check for a file or directory
        if(shareName.equals(DBUtil.SHARENAME_RECIVEFILE)){
        	log4j.warn(DBUtil.SHARENAME_RECIVEFILE+"  中 不允许删除 操作 path:"+file.getFullName());
        	throw new AccessDeniedException("不允许删除");
        }else{
        	try{
        		if ( file.isDirectory())
            		deleteDirectory(sess, tree, file.getFullName());
            	else
            		deleteFile(sess, tree, file.getFullName());
        	}catch(AccessDeniedException ex)
        	{
        		throw new AccessDeniedException("不允许删除");
        	}
        }
        
        //  DEBUG
        log4j.debug("  Marked for delete ,fileId:"+file.getFileId()+" ,fullName:"+file.getFullName());
      }
    }
    else
    {
    	log4j.error("DBD#closeFile() Not DBNetworkFile fileId:"+file.getFileId()+", file="+file);
    }
      
    //  Check if the file was opened for write access, if so then update the file size and modify date/time
    
    if ( file.getGrantedAccess() != NetworkFile.READONLY && file.isDirectory() == false &&
         file.getWriteCount() > 0) {
      
      //  DEBUG
      log4j.debug("  Update file size=" + file.getFileSize());
        
      //  Get the current date/time
      
      long modifiedTime = 0L;
      if ( file.hasModifyDate())
        modifiedTime = file.getModifyDate();
      else
        modifiedTime = System.currentTimeMillis();

      //  Check if the modified time is earlier than the file creation date/time
      
      if ( file.hasCreationDate() && modifiedTime < file.getCreationDate()) {
        
        //  Use the creation date/time for the modified date/time
        
        modifiedTime = file.getCreationDate();
        
        //  DEBUG
        log4j.debug("Close file using creation date/time for modified date/time");
      }
      
      //  Update the file details
      
      try {
        
        //  Update the file details

        FileInfo finfo = new FileInfo();
        
        finfo.setFileSize( file.getFileSize());
        finfo.setModifyDateTime(modifiedTime);
        
        finfo.setFileInformationFlags(FileInfo.SetFileSize + FileInfo.SetModifyDate);

        //  Call the database interface
        
        dbCtx.getDBInterface().setFileInformation(file.getDirectoryId(), file.getFileId(), finfo,shareName);
      }
      catch (DBException ex) {
    	  log4j.error(ex);
      }
    }
  }

  /**
   * Create a new directory
   * 
   * @param sess   Session details
   * @param tree   Tree connection
   * @param params Directory create parameters
   * @exception IOException
   */
  public void createDirectory(SrvSession sess, TreeConnection tree, FileOpenParams params)
    throws IOException {

    //  Access the database context

    DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();
    String shareName = tree.getContext().getShareName();
    String userName = sess.getClientInformation().getUserName();
    String ipAddress = sess.getClientInformation().getClientAddress();
    // Check if the database is online
    
    if ( dbCtx.getDBInterface().isOnline() == false)
      throw new DiskOfflineException( "Database is offline");
    
    //  Get, or create, a file state for the new path. Initially this will indicate that the directory
    //  does not exist.
    
    FileState fstate = getFileState(params.getPath(), dbCtx, false);
    if ( fstate != null && fstate.fileExists() == true)
    {
    	log4j.error("Path " + params.getPath() + " exists");
      throw new FileExistsException("Path " + params.getPath() + " exists");
    }

    //  If there is no file state check if the directory exists
    
    if ( fstate == null) {

      //  Create a file state for the new directory
      
      fstate = getFileState(params.getPath(), dbCtx, true);
      
      //  Get the file details for the directory
      
      if ( getFileDetails(params.getPath(), dbCtx, fstate,userName,shareName) != null)
      {
    	  log4j.error("Path " + params.getPath() + " exists");
    	  throw new FileExistsException("Path " + params.getPath() + " exists");
      }
    }

    //  Find the parent directory id for the new directory
    
    int dirId = findParentDirectoryId(dbCtx,params.getPath(),true,userName,shareName);
    if ( dirId == -1)
    {
    	log4j.error("Cannot find parent directory path:"+params.getPath());
      throw new IOException("Cannot find parent directory");
    }
      
    //  Create the new directory entry
    
    FileAccessToken accessToken = null;
    int fid = -1;
    
    try {

      //  Get the directory name
      
      String[] paths = FileName.splitPath(params.getPath());
      String dname = paths[1];

      //不允许在指定目录中创建文件
      if (params.getPath().equalsIgnoreCase("\\" + dname))
      {
    	if(shareName.equalsIgnoreCase(DBUtil.SHARENAME_USERFILE) || shareName.equalsIgnoreCase(DBUtil.SHARENAME_COMMFILE) || shareName.equalsIgnoreCase(DBUtil.SHARENAME_COMMFILE_ALIAS))
    	{
    		log4j.warn("DBD#createDirectory() 用户层级不允许创建文件夹 , path:"+params.getPath());
      		throw new AccessDeniedException("用户层级不允许创建文件夹");
      	}
  	  }
    //检查是否是公共资料库的第一层
      if (params.getPath().equalsIgnoreCase("\\" + userName + DBUtil.SPECIAL_CHAR + "\\"+ dname)) {
    	  if(shareName.equals(DBUtil.SHARENAME_COMMFILE) || shareName.equals(DBUtil.SHARENAME_COMMFILE_ALIAS))
    	  {
    		  log4j.warn("DBD#createDirectory() 资料库根目录不允许创建文件文件夹, path:"+params.getPath());
    		  throw new AccessDeniedException("资料库根目录不允许创建文件夹");
    	  }
	  }
      if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_RECIVEFILE))
      {
    	  log4j.warn("共享文件不允许创建");
		  throw new AccessDeniedException("共享文件不允许创建");
      }
      //  Check if the directory name is too long
      
      if ( dname != null && dname.length() > MaxFileNameLen)
      {
    	  log4j.error("Directory name too long, " + dname);
    	  throw new FileNameException("Directory name too long, " + dname);
      }
      
      //  If retention is enabled check if the file is a temporary folder
      
      boolean retain = true;
      
      if ( dbCtx.hasRetentionPeriod()) {
        
        //  Check if the file is marked delete on close
        
        if ( params.isDeleteOnClose())
          retain = false;
      }
      
      //  Set the default NFS file mode, if not set
      
      if ( params.hasMode() == false)
        params.setMode(DefaultNFSDirMode);

      //  Make sure the create directory option is enabled
      
      if ( params.hasCreateOption( WinNT.CreateDirectory) == false)
      {
    	  log4j.error( "Create directory called for non-directory");
        throw new IOException( "Create directory called for non-directory");
      }
      
   	  // Check if the file can be opened in the requested mode
  	  //
  	  // Note: The file status is set to NotExist at this point, the file record creation may fail
    	
      accessToken = dbCtx.getStateCache().grantFileAccess( params, fstate, FileStatus.NotExist);
      
      //  Use the database interface to create the new file record
      
      fid = dbCtx.getDBInterface().createFileRecord(dname, dirId, params, retain,userName,shareName,ipAddress);

      //  Indicate that the path exists
      
      fstate.setFileStatus( FileStatus.DirectoryExists, FileState.ReasonFolderCreated);
      
      //  Set the file id for the new directory
      
      fstate.setFileId(fid);
      
      //  If retention is enabled get the expiry date/time
      
      if ( dbCtx.hasRetentionPeriod() && retain == true) {
        RetentionDetails retDetails = dbCtx.getDBInterface().getFileRetentionDetails(dirId, fid);
        if ( retDetails != null)
          fstate.setRetentionExpiryDateTime(retDetails.getEndTime());
      }
      
      //  Check if the file loader handles create directory requests
      
      if ( fid != -1 && dbCtx.getFileLoader() instanceof NamedFileLoader) {
        
        //  Create the directory in the filesystem/repository
        
        NamedFileLoader namedLoader = (NamedFileLoader) dbCtx.getFileLoader();
        namedLoader.createDirectory(params.getPath(), fid);
      }
      
      // Release the access token
      
      if ( accessToken != null) {
          dbCtx.getStateCache().releaseFileAccess(fstate, accessToken);
          accessToken = null;
      }
    }
    catch (DBException ex) {
    	log4j.error( "createDirectory error",ex);
    	throw new IOException();
    }
    finally {
    	
      // Check if the file is not valid but an access token has been allocated
      	
      if ( fid == -1 && accessToken != null)
        dbCtx.getStateCache().releaseFileAccess(fstate, accessToken);
    }
  }

  /**
   * Create a new file entry
   * 
   * @param sess SrvSession
   * @param tree TreeConnection
   * @param params FileOpenParams
   * @return NetworkFile
   */
  public NetworkFile createFile(SrvSession sess, TreeConnection tree, FileOpenParams params)
    throws IOException {

    //  Access the database context
	  String userName = sess.getClientInformation().getUserName();
	  String shareName = tree.getContext().getShareName();
	  String ipAddress = sess.getClientInformation().getClientAddress();
    DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();
    // Check if the database is online
    
    if ( dbCtx.getDBInterface().isOnline() == false)
    {
    	log4j.error("Database is offline");
      throw new DiskOfflineException( "Database is offline");
    }
    
    // Check if this is a stream create
    
    FileState fstate = getFileState(params.getPath(), dbCtx, true);
    log4j.debug("DBD#createFile(); params.isStream:" +  params.isStream()+" ; path:"+params.getPath());
    
    if ( params.isStream()) {

    	// Make sure the parent file exists

    	if ( fileExists( sess, tree, params.getPath()) == FileStatus.FileExists) {
    		
    		//  Create a new stream associated with the existing file
      
    		return createStream(sess, tree, params, fstate, dbCtx);
    	}
    	else {
    		
    		// Parent file does not exist
    		log4j.error("Parent file does not exist to create stream, " + params.getPath());
    		throw new FileNotFoundException("Parent file does not exist to create stream, " + params.getPath());
    	}
    }
    else if ( fstate.fileExists()) {

    	// File already exists
    	log4j.error("File exists, " + params.getPath());
        throw new FileExistsException("File exists, " + params.getPath());
    }
      
    //  Split the path string and find the directory id to attach the file to
    
    int dirId = findParentDirectoryId(dbCtx,params.getPath(),true,userName,shareName);
    if ( dirId == -1)
    {
    	log4j.error("Cannot find parent directory path:"+params.getPath());
      throw new IOException("Cannot find parent directory");
    }

    //  Check if the allocation size for the new file is greater than the maximum allowed file size
    
    if ( dbCtx.hasMaximumFileSize() && params.getAllocationSize() > dbCtx.getMaximumFileSize())
    {
    	log4j.error("Required allocation greater than maximum file size");
      throw new DiskFullException( "Required allocation greater than maximum file size");
    }
    
    //  Create a new file
    
    DBNetworkFile file = null;
    FileAccessToken accessToken = null;

    try {

      //  Get the file name
      
      String[] paths = FileName.splitPath(params.getPath());
      String fname = paths[1];
      
      //  Check if the file name is too long
      
      if ( fname != null && fname.length() > MaxFileNameLen)
      {
    	  log4j.error("File name too long, " + fname);
        throw new FileNameException("File name too long, " + fname);
      }

      //不允许在用户层中创建文件
      if (params.getPath().equalsIgnoreCase("\\" + fname))
      {
    	  if(shareName.equalsIgnoreCase(DBUtil.SHARENAME_USERFILE)|| shareName.equals(DBUtil.SHARENAME_COMMFILE) || shareName.equals(DBUtil.SHARENAME_COMMFILE_ALIAS))
    	  {
    		  log4j.warn("DBD#createFile() 用户层级不允许创建文件 , path:"+params.getPath());
    	  		throw new AccessDeniedException("用户层级不允许创建文件");
    	  }
  	  }
      //检查是否是公共资料库的第一层
      if (params.getPath().equalsIgnoreCase("\\" + userName + DBUtil.SPECIAL_CHAR + "\\"+ fname)) {
    	  if(shareName.equals(DBUtil.SHARENAME_COMMFILE) || shareName.equals(DBUtil.SHARENAME_COMMFILE_ALIAS))
    	  {
    		  log4j.warn("DBD#createFile() 资料库根目录不允许创建文件, path:"+params.getPath());
    	      throw new AccessDeniedException("资料库根目录不允许创建文件");
    	  }
	  }
      if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_RECIVEFILE))
      {
    	  log4j.warn("共享文件不允许创建");
		  throw new AccessDeniedException("共享文件不允许创建");
      }
      
      //  If retention is enabled check if the file is a temporary file
      
      boolean retain = true;
      
      if ( dbCtx.hasRetentionPeriod()) {
        
        //  Check if the file is marked delete on close
        
        if ( params.isDeleteOnClose())
          retain = false;
      }
      
      //  Set the default NFS file mode, if not set
      
      if ( params.hasMode() == false)
        params.setMode(DefaultNFSFileMode);
      
  	  // Check if the current file open allows the required shared access
  	
      if ( params.getPath().equals( "\\") == false) {

      	// Check if the file can be opened in the requested mode
    	//
    	// Note: The file status is set to NotExist at this point, the file record creation may fail
      	
      	accessToken = dbCtx.getStateCache().grantFileAccess( params, fstate, FileStatus.NotExist);
      }
      int fid = dbCtx.getDBInterface().createFileRecord(fname, dirId, params, retain,userName,shareName,ipAddress);
      //  Create a new file record

      //  Indicate that the file exists
        
      fstate.setFileStatus( FileStatus.FileExists, FileState.ReasonFileCreated);

      //  Save the file id
        
      fstate.setFileId(fid);

      //  If retention is enabled get the expiry date/time
      
      if ( dbCtx.hasRetentionPeriod() && retain == true) {
        RetentionDetails retDetails = dbCtx.getDBInterface().getFileRetentionDetails(dirId, fid);
        if ( retDetails != null)
          fstate.setRetentionExpiryDateTime(retDetails.getEndTime());
      }
      
      //  Create a network file to hold details of the new file entry
      
      file = (DBNetworkFile) dbCtx.getFileLoader().openFile(params, fid, 0, dirId, true, false, userName);
      file.setFullName(params.getPath());
      file.setDirectoryId(dirId);
      file.setAttributes(params.getAttributes());
      file.setFileState( dbCtx.getStateCache().getFileStateProxy(fstate));
        
      file.setAccessToken( accessToken);
      
      //  Open the file
        
      file.openFile(true);
    }
    catch (DBException ex) {
      
      // Remove the file state for the new file
      
      dbCtx.getStateCache().removeFileState( fstate.getPath());
      
      // DEBUG
      log4j.error("Create file error: " ,ex);
    }
    finally {
    	
      // Check if the file is not valid but an access token has been allocated
    	
      if ( file == null && accessToken != null)
    	dbCtx.getStateCache().releaseFileAccess(fstate, accessToken);
    }

    //  Return the new file details

    if (file == null)
    {
    	log4j.error("Failed to create file " + params.getPath());
      throw new IOException( "Failed to create file " + params.getPath());
    }
/**
    else {
      
      // Save the file sharing mode, needs to be done before the open count is incremented
        
      fstate.setSharedAccess( params.getSharedAccess());
      fstate.setProcessId( params.getProcessId());
        
      //  Update the file state
    
      fstate.incrementOpenCount();
    }
**/
//    log4j.debug("DBD#createFile 完成，延时1秒.....");
//    try {
//		Thread.sleep(1000);//保存完后，延时1秒
//		log4j.debug("DBD#createFile 延时结束。");
//	} catch (Exception e) {
//		e.printStackTrace();
//	}
    //  Return the network file
    return file;
  }

  /**
   * Delete a directory
   * 
   * @param sess  Session details
   * @param tree  Tree connection
   * @param dir   Path of directory to delete
   * @exception IOException
   */
  public void deleteDirectory(SrvSession sess, TreeConnection tree, String dir)
    throws IOException {
    
    //  Debug
    String userName = sess.getClientInformation().getUserName();
    String shareName = tree.getContext().getShareName();
    String ipAddress = sess.getClientInformation().getClientAddress();
    log4j.debug("DBD#deleteDirectory() dir=" + dir);
      
    //  Access the JDBC context

    DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();
    if(shareName.equalsIgnoreCase(DBUtil.SHARENAME_RECIVEFILE))
    {
    	log4j.warn(shareName+" ,不允许删除");
    	throw new AccessDeniedException("不允许删除");
    }
    if (dir.equalsIgnoreCase("\\"+userName+DBUtil.SPECIAL_CHAR)) {
    	log4j.warn(dir+" ,不允许删除");
    	throw new AccessDeniedException("用户目录不允许删除");
  	}
    
    // Check if the database is online
    
    if ( dbCtx.getDBInterface().isOnline() == false)
    {
    	log4j.error("Database is offline");
      throw new DiskOfflineException( "Database is offline");
    }
    
    //  Get the file state for the path
    
    FileState fstate = getFileState(dir,dbCtx,false);
    if ( fstate != null && fstate.fileExists() == false)
    {
    	log4j.error("Path does not exist, " + dir);
      throw new FileNotFoundException("Path does not exist, " + dir);
    }

    //  Create a file state if it does not exist
    
    if ( fstate == null)
      fstate = getFileState(dir,dbCtx,true);
            
    //  Get the directory details

    DBFileInfo dinfo = getFileDetails(dir, dbCtx,fstate,userName,shareName);
    if ( dinfo == null)
    {
    	log4j.error("dinfo does not exist, " + dir);
      throw new FileNotFoundException(dir);
    }
    if(dinfo.isReadOnly())
    {
    	log4j.warn(dir+" ,只读文件");
    	throw new AccessDeniedException("只读文件");
    }
    
    //  Check if the directory contains any files
    try {

      //  Check if the file loader handles delete directory requests. Called first as the loader may throw an exception
      //  to stop the directory being deleted.
      
      if ( dbCtx.isTrashCanEnabled() == false && dbCtx.getFileLoader() instanceof NamedFileLoader) {
        
        //  Delete the directory in the filesystem/repository
        
        NamedFileLoader namedLoader = (NamedFileLoader) dbCtx.getFileLoader();
        namedLoader.deleteDirectory(dir, dinfo.getFileId());
      }

      //  Delete the directory file record, or mark as deleted if the trashcan is enabled
      
      dbCtx.getDBInterface().deleteFileRecord(dinfo.getDirectoryId(), dinfo.getFileId(), dbCtx.isTrashCanEnabled(),userName,shareName,ipAddress);
        
      //  Indicate that the path does not exist

      fstate.setFileStatus( FileStatus.NotExist, FileState.ReasonFolderDeleted);
      fstate.setFileId(-1);
      fstate.removeAttribute(FileState.FileInformation);
    }
    catch (DBException ex) {
    	log4j.error("DBException:" ,ex);
      throw new IOException();
    }
  }

  /**
   * Delete a file
   * 
   * @param sess  Session details
   * @param tree  Tree connection
   * @param name  Name of file to delete
   * @exception IOException
   */
  public void deleteFile(SrvSession sess, TreeConnection tree, String name)
    throws IOException {
     
    //  Access the JDBC context
	  String userName = sess.getClientInformation().getUserName();
	  String shareName = tree.getContext().getShareName();
	  String ipAddress = sess.getClientInformation().getClientAddress();
	 
    DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();
    if(shareName.equalsIgnoreCase(DBUtil.SHARENAME_RECIVEFILE))
    {
    	log4j.warn(shareName+", 不允许删除");
    	throw new AccessDeniedException("不允许删除");
    }
    // Check if the database is online
    
    if ( dbCtx.getDBInterface().isOnline() == false)
    {
    	log4j.error( "Database is offline");
      throw new DiskOfflineException( "Database is offline");
    }
    
    //  Check if the file name is a stream
    
    if ( FileName.containsStreamName(name)) {
      
      //  Delete a stream within a file
      
      deleteStream(sess, tree, name);
      return;
    }
    
    String ext = DiskUtil.getExt(name);//文件后缀
    //  Get the file state for the path
    
    FileState fstate = getFileState(name, dbCtx, false);
    if ( fstate != null && fstate.fileExists() == false)
    {
    	if(StringUtils.isEmpty(ext) || "tmp".equals(ext))
  	  {
  		  log4j.warn("DBD#deleteFile() 未产生的临时文件不返回 File does not exist  name:"+name);
//  		  return ;// 不往下执行
  	  }
  	  else
  	  {
  		log4j.warn("File does not exist, " + name);
//    	throw new FileNotFoundException("File does not exist, " + name);
  	  }
    }
    
    //  Create a file state for the file, if not already valid
    
    if ( fstate == null)
      fstate = getFileState(name, dbCtx, true);
        
    DBFileInfo dbInfo = null;
    
    try {

      //  Check if the file is within an active retention period
      
      getRetentionDetailsForState( dbCtx, fstate);
      
      if ( fstate.hasActiveRetentionPeriod())
      {
    	  log4j.error("File retention active");
        throw new AccessDeniedException("File retention active");
      }

      //  Get the file details
      
      dbInfo = getFileDetails(name, dbCtx, fstate,userName,shareName);
      
      if ( dbInfo == null)
      {
    	  
    	  if(StringUtils.isEmpty(ext) || "tmp".equals(ext))
    	  {
    		  log4j.error("DBD#deleteFile() 未产生的临时文件不返回 FileNotFoundException  name:"+name);
//    		  return ;// 不往下执行
    	  }
    	  else
    	  {
    		  log4j.error("DBD#deleteFile() FileNotFoundException  name:"+name);
    		  throw new FileNotFoundException(name);
    	  }
      }
      
      //  DEBUG
      log4j.debug("DBD#deleteFile() name=" + name + ", state=" + fstate);
      /* 按条件不执行删除：(office改名删除的情况) start 
      int userId = 0;
	  	try{
	  		UserBean user = dbCtx.getDBInterface().getUserByUsername(userName);
	  		if(null != user)
	  		{
	  			userId = user.getId();
	  		}
	  	}
	  	catch (DBException ex) {
	  		log4j.error("User is not exist from database, userId:"+userId+" username:"+userName,ex);
	  		throw new AccessDeniedException("User is not exist from database, userId:"+userId+" username:"+userName,ex);
	  	}
      DBFileInfo finfo = dbCtx.getDBInterface().getFileInformation(0, fstate.getFileId(), DBInterface.FileNameOnly,userId,shareName,userName);
      if(null != finfo)
      {
    	  String[] paths = FileName.splitPath( name);
          String fname = paths[1];
    	  if(!finfo.getFileName().equalsIgnoreCase(fname))
    	  {
    		  //名称不同，不执行删除
    		  return ;//不执行下面的操作
    	  }
    	  if(finfo.isReadOnly())
    	  {
    	    log4j.warn(fstate.getFileId()+" ,只读文件");
    	    throw new AccessDeniedException("只读文件");
    	  }
      }
      */
      /* 按条件不执行删除：(office改名的情况) end */
      //  Delete the file in the filesystem/repository, the loader may prevent the file delete by throwing
      //  an exception
//      boolean canDel = !"xls".equals(ext);//xls不能删除关联对象,xls2003会报错
      boolean canDel = (!"xls".equals(ext) && !"xlsx".equals(ext))||(!"doc".equals(ext) && !"docx".equals(ext));//xls不能删除关联对象,xls2003会报错
      if ( dbCtx.isTrashCanEnabled() == false && canDel)
        dbCtx.getFileLoader().deleteFile(name,fstate.getFileId(), 0,shareName);

      //  If the file is a symbolic link delete the symbolic link record
      
      if (null !=dbInfo && dbInfo.isFileType() == FileType.SymbolicLink && canDel)
        dbCtx.getDBInterface().deleteSymbolicLinkRecord( dbInfo.getDirectoryId(), fstate.getFileId());
      
      /* 这部分可能导致保存后，WPS保存doc及时 (加上这一段Office2007编辑时会报错）
      // Check if the file has any NTFS streams
      
      StreamInfoList streamList = getStreamList( sess, tree, name);
      
      if ( streamList != null && streamList.numberOfStreams() > 0) {
    	  
    	  // Make a copy of the streams list as streams are removed from the original list as we delete them
    	  
    	  StreamInfoList sList = new StreamInfoList( streamList);
    	  
    	  // Delete the streams

    	  StringBuilder sPath = new StringBuilder( 256);
    	  sPath.append( name);
    	  int delCnt = 0;
    	  
    	  for ( int idx = 0; idx < sList.numberOfStreams(); idx++) {
    		  
    		  // Get the current stream details
    		  
    		  StreamInfo sInfo = sList.getStreamAt( idx);
    		  if ( sInfo.getName().equals( FileName.MainDataStreamName) == false) {
    			  
    			  // Build the full path to the stream
    			  
    			  sPath.setLength( name.length());
    			  sPath.append( sInfo.getName());
    			  
    			  // Delete the stream
    			  
    			  deleteStream( sess, tree, sPath.toString());
    			  delCnt++;
    		  }
    	  }
    	  
    	  // DEBUG
    	  
          if ( delCnt > 0)
        	  log4j.debug("DBD#deleted " + delCnt + " streams for name=" + name);
      }
      */
      //  Delete the file record
      if(null != dbInfo && null != fstate)
    	  dbCtx.getDBInterface().deleteFileRecord(dbInfo.getDirectoryId(), fstate.getFileId(), dbCtx.isTrashCanEnabled(),userName,shareName,ipAddress);
      
      //  Indicate that the path does not exist
      /*  这部分可能导致保存后，提示权限不对。暂不执行*/
//      if("tmp".equals(ext)|| "xls".equals(ext))
//    	if("tmp".equals(ext)|| "xls".equals(ext) || "xlsx".equals(ext) || (name.startsWith("~$") && "doc".equals(ext)) || (name.startsWith("~$") && "docx".equals(ext)))
//      if("tmp".equals(ext) || "xls".equals(ext)  || (name.startsWith("~$") && "doc".equals(ext))||(name.startsWith("~$") && "xlsx".equals(ext)))
      if("tmp".equals(ext) || "xls".equals(ext)  || (name.startsWith("~$") && "doc".equals(ext))||(name.startsWith("~$") && "xlsx".equals(ext)))
      {
	      fstate.setFileStatus( FileStatus.NotExist, FileState.ReasonFileDeleted);
	      fstate.setFileId(-1);
	      log4j.warn("DBD#deleteFile() FileStatus.NotExist-fstate.setFileId(-1): name"+name+" , ext:"+ext);
      }
      fstate.removeAttribute(FileState.FileInformation);
      
      //  Check if there is a quota manager, if so then release the file space
      
      if ( dbCtx.hasQuotaManager() && null != dbInfo) {
        
        //  Release the file space back to the filesystem free space
        
        dbCtx.getQuotaManager().releaseSpace(sess, tree, fstate.getFileId(), null, dbInfo.getSize());
      }
    }
    catch (DBException ex) {
    	log4j.error("Failed to delete file "+name);
    	throw new IOException( "Failed to delete file " + name);
    }
  }

  /**
   * Check if the specified file exists, and it is a file.
   *
   * @param sess  Session details
   * @param tree  Tree connection
   * @param name  File name
   * @return int
   */
  public int fileExists(SrvSession sess, TreeConnection tree, String name) {
    //  Access the JDBC context
	  String userName = sess.getClientInformation().getUserName();
	  String shareName = tree.getContext().getShareName();
    DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();
    //debug
//    log4j.debug("DBD#fileExists name=" + name);
    //  Check if the path contains an NTFS stream name

    int fileSts = FileStatus.NotExist;
    FileState fstate = null;
    
//  Check for the root directory    
    if ( name.length() == 0 || name.compareTo("\\") == 0) {
//    	 log4j.debug("DBD#fileExists 根目录 , 直接返回 DirectoryExists ");
    	 return FileStatus.DirectoryExists;
    }
    if (name.equalsIgnoreCase("\\"+userName+DBUtil.SPECIAL_CHAR)) {
//   	 log4j.debug("DBD#fileExists username@用户目录 , 直接返回 DirectoryExists ");
    	return FileStatus.DirectoryExists;
    }
    if(name.toLowerCase().startsWith("\\"+userName.toLowerCase()+DBUtil.SPECIAL_CHAR)==false)
    {
    	log4j.warn("DBD#fileExists 不符合规则的name参数 , 直接返回 NotExist ; name:"+name+",userName:"+userName);
//    	return FileStatus.NotExist;
    	sess.setLoggedOn(false);
    	sess.closeSession();
    	userName = name.substring(name.indexOf("\\")+1);
    	if(userName.indexOf(DBUtil.SPECIAL_CHAR)>0)
    	{
    		userName = userName.substring(0, userName.indexOf(DBUtil.SPECIAL_CHAR));
    	}
    	else if(userName.indexOf("\\")>0)
    	{
    		userName = userName.substring(0, userName.indexOf("\\"));
    	}
    	 sess.getClientInformation().setUserName(userName);
    	log4j.warn("重置userName : "+userName);
    }
        
    if ( FileName.containsStreamName(name)) {
      
    	// Get the file information for the stream    	
    	FileInfo fInfo = null;
    	try {
    		fInfo = getFileInformation( sess, tree, name);
    	}
    	catch ( IOException ex) {
    		log4j.error(ex );
    	}

    	// Check if the file information was retrieved for the stream    	
    	if ( fInfo != null)
    		fileSts = FileStatus.FileExists;
    	
        //  Debug
    	log4j.debug("DBD#fileExists() nameWithStream=" + name + ", fileSts=" + FileStatus.asString(fileSts));
    }
    else {

      //  Get, or create, the file state for the path
      
      fstate = getFileState( name, dbCtx, true);
  
      //  Check if the file exists status has been cached
      
      fileSts = fstate.getFileStatus();
      
//        if ( fstate.getFileStatus() == FileStatus.Unknown) {
      String ext = DiskUtil.getExt(name);
  	 if ( fstate.getFileStatus() == FileStatus.Unknown || (fstate.getFileStatus()==FileStatus.NotExist &&DBUtil.SUPPORT_EXT.contains(ext))) {
  		 //fstate.getFileStatus()==FileStatus.NotExist &&DBUtil.SUPPORT_EXT.contains(ext) ā
        
        //  Get the file details
        
        DBFileInfo dbInfo = getFileDetails(name,dbCtx,fstate,userName,shareName);

        if ( dbInfo != null) {
          if ( dbInfo.isDirectory() == true)
            fileSts = FileStatus.DirectoryExists;
          else
            fileSts = FileStatus.FileExists;
          
          // Save the file id
          
          if ( dbInfo.getFileId() != -1)
        	  fstate.setFileId( dbInfo.getFileId());
        }
        else {
      
          //  Indicate that the file does not exist
          
          fstate.setFileStatus( FileStatus.NotExist);
          fileSts = FileStatus.NotExist;
        }
        
        //  Debug
//        log4j.debug("DBD#fileExists() name=" + name + ", fileSts=" + FileStatus.asString(fileSts));
      }
      else {
        
        //  DEBUG
//    	  log4j.debug("@@ Cache hit - fileExists() name=" + name + ", fileSts=" + FileStatus.asString(fileSts));
      }
    }
    
    //  Return the file exists status
    
    return fileSts;
  }

  /**
   * Flush buffered data for the specified file
   * 
   * @param sess  Session details
   * @param tree  Tree connection
   * @param file  Network file
   * @exception IOException
   */
  public void flushFile(SrvSession sess, TreeConnection tree, NetworkFile file)
    throws IOException {
    
    //  Debug
	  log4j.debug("DBD#flushFile()");
      
    //  Flush any buffered data
    
    file.flushFile();
    
  }

  /**
   * Return file information about the specified file
   * 
   * @param sess  Session details
   * @param tree  Tree connection
   * @param name  File name
   * @return SMBFileInfo
   * @exception IOException
   */
  public FileInfo getFileInformation(SrvSession sess, TreeConnection tree, String name)
    throws IOException {

    //  Check for the null file name
	  String userName = sess.getClientInformation().getUserName();
	  String shareName = tree.getContext().getShareName();
    if (name == null)
      return null;
    //  Access the JDBC context

    DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

    // Check if the database is online
    
    if ( dbCtx.getDBInterface().isOnline() == false)
    {
    	log4j.error("Database is offline");
      throw new DiskOfflineException( "Database is offline");
    }
    
    //  Check if the path is a file stream
    
    FileState fstate = null;
    FileInfo finfo = null;
    
    if ( FileName.containsStreamName(name)) {

      //  Check if there is an active file state for the stream
      
      fstate = getFileState(name,dbCtx,true);
//    	fstate = getFileState(name,dbCtx,false);//不创建缓存
      
      if ( fstate != null) {
        
    	// Check if the file stream exists
    	  
    	if ( fstate.getFileStatus() != FileStatus.NotExist) {
    		  
    		//  Check if the file information is available
        
    		finfo = (FileInfo) fstate.findAttribute(FileState.FileInformation);
    	}
    	else
    		return null;
      }

      //  If the cached file information is not available then create it
      
      if ( finfo == null) {     

        //  Get, or create, the file state for main file path
        
        String filePath = FileName.getParentPathForStream( name);
        FileState parent = getFileState(filePath,dbCtx,false);
        
        // Get the file information for the parent file to load the cache
        
        if ( parent == null) {
        	
        	// Get the file information for the parent file
        	log4j.debug("parent is null 再次调用:getFileInformation() ; filepath:" +filePath+", finfo:"+finfo);
        	getFileInformation( sess, tree, filePath);
        	
        	// File state should exist for the parent now
        	
//        	parent = getFileState(filePath,dbCtx,false);
        	parent = getFileState(filePath,dbCtx,true);//改为存在后创建上级目录缓存
        }
  
        //  Check if the top level file exists
        
        if ( parent != null && parent.fileExists() == true) {
          
          //  Get the top level file details
        
          DBFileInfo dbInfo = getFileDetails(name,dbCtx,parent,userName,shareName);
          
          if ( dbInfo != null) {
  
            //  Get the list of available streams
            
            StreamInfoList streams = getStreamList(sess, tree, filePath);
            
            if ( streams != null && streams.numberOfStreams() > 0) {
              
              // Parse the path into directory, file and stream names
            	
              String[] paths = FileName.splitPathStream( name);
            	
              //  Get the details for the stream, if the information is valid copy it to a file information
              //  object
              
              StreamInfo sInfo = streams.findStream(paths[2]);
              
              if ( sInfo != null) {
                
                //  Create a file information object, copy the stream details to it
                
                finfo = new DBFileInfo(paths[1], name, dbInfo.getFileId(), dbInfo.getDirectoryId());
                
                finfo.setFileId(sInfo.getFileId());
                finfo.setFileSize(sInfo.getSize());
                
                finfo.setCreationDateTime( sInfo.getCreationDateTime());
                finfo.setAccessDateTime( sInfo.getAccessDateTime());
                finfo.setModifyDateTime( sInfo.getModifyDateTime());
                
                //  Attach to the file state
                
                fstate.addAttribute(FileState.FileInformation, finfo);
                
                //  DEBUG
                log4j.debug("DBD#getFileInformation() stream=" + name + ", info=" + finfo);
              }
            }
          }
        }
      }
    }
    else {

      //  Get, or create, the file state for the path
      
      fstate = getFileState(name, dbCtx, true);
//    	fstate = getFileState(name, dbCtx, false);
      
      //  Get the file details for the path
      
      DBFileInfo dbInfo = getFileDetails(name, dbCtx, fstate,userName,shareName);
  
      //  Set the full file/path name
      
      if ( dbInfo != null)
        dbInfo.setFullName(name);
      finfo = dbInfo;
    }
    //  DEBUG
   
//    if ( Debug.EnableInfo && hasDebug() && finfo != null)
//      Debug.println("getFileInformation info=" + finfo.toString());

    //  Return the file information

    return finfo;
  }

  /**
   * Determine if the disk device is read-only.
   *
   * @param sess  Session details
   * @param ctx   Device context
   * @return true if the device is read-only, else false
   * @exception IOException  If an error occurs.
   */
  public boolean isReadOnly(SrvSession sess, DeviceContext ctx)
    throws IOException {
    return false;
  }

  /**
   * Open a file
   * 
   * @param sess    Session details
   * @param tree    Tree connection
   * @param params  File open parameters
   * @return NetworkFile
   * @exception IOException
   */
  public NetworkFile openFile(SrvSession sess, TreeConnection tree, FileOpenParams params)
    throws IOException {

    //  Access the JDBC context
	  String userName = sess.getClientInformation().getUserName();
	  String shareName = tree.getContext().getShareName();
    DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

    // Check if the database is online
    
    if ( dbCtx.getDBInterface().isOnline() == false)
    {
    	log4j.error("Database is offline");
      throw new DiskOfflineException( "Database is offline");
    }
    //  Get, or create, the file state
    FileState fstate = getFileState(params.getPath(), dbCtx, true);
    
    // Check if the file has a data update in progress, the file will be offline until the
    // data update completes
    
    if ( fstate != null && fstate.hasDataUpdateInProgress())
    {
    	log4j.error("DBD#Data update in progress");
    	throw new FileOfflineException( "Data update in progress");
    }
    
    //  Check if we are opening a stream associated with the main file
    
    if ( fstate != null && params.isStream()) {
      
      //  Open an NTFS stream
      
      return openStream(sess, tree, params, fstate, dbCtx);
    }
    
    //  Get the file name
    
    String[] paths = FileName.splitPath(params.getPath());
    String fname = paths[1];
    // DEBUG
//    log4j.debug("DBD#openFile FileName [1]= " + fname);

    //  Check if the file name is too long
    
    if ( fname != null && fname.length() > MaxFileNameLen)
    {
    	log4j.error("DBD#File name too long, "+fname);
      throw new FileNameException("File name too long, " + fname);
    }
    
    //  Get the file information
    
    DBFileInfo finfo = getFileDetails(params.getPath(), dbCtx, fstate,userName,shareName);
    
    if (finfo == null)
    {
//      throw new AccessDeniedException();
    	log4j.error("DBD#openFile FileNotFoundException, fname:"+fname);
   		throw new FileNotFoundException();
    }

    //  If retention is enabled get the expiry date/time
    
    if ( dbCtx.hasRetentionPeriod()) {
      try {
        
        //  Get the file retention expiry date/time
        
        RetentionDetails retDetails = dbCtx.getDBInterface().getFileRetentionDetails(finfo.getDirectoryId(), finfo.getFileId());
        if ( retDetails != null)
          fstate.setRetentionExpiryDateTime( retDetails.getEndTime());
      }
      catch (DBException ex) {
    	  log4j.error("DBD#Retention error, "+ex.getMessage());
        throw new AccessDeniedException("Retention error, " + ex.getMessage());
      }
    }
    
	// Check if the current file open allows the required shared access
	
    FileAccessToken accessToken = null;
    
    if ( params.getPath().equals( "\\") == false) {

    	// Check if the file can be opened in the requested mode
    	
    	accessToken = dbCtx.getStateCache().grantFileAccess( params, fstate, finfo.isDirectory() ? FileStatus.DirectoryExists : FileStatus.FileExists);
    }
	
	// DEBUG
//    log4j.debug("DBD#openFile() name=" + params.getPath() + ", sharing=0x" + Integer.toHexString(params.getSharedAccess()) + ", PID=" + params.getProcessId() + ", token=" + accessToken);

	DBNetworkFile jdbcFile = null;
	
	try {
	    //  Create a JDBC network file and open the top level file
	
	    jdbcFile = (DBNetworkFile) dbCtx.getFileLoader().openFile(params, finfo.getFileId(), 0, finfo.getDirectoryId(), false, finfo.isDirectory(),userName);
	
	    jdbcFile.setFileDetails(finfo);
	    jdbcFile.setFileState( dbCtx.getStateCache().getFileStateProxy( fstate));
	    
	    jdbcFile.openFile( false);
	        
	    // Set the granted file access
	    
	    if ( params.isReadOnlyAccess())
	    	jdbcFile.setGrantedAccess( NetworkFile.READONLY);
	    else if ( params.isWriteOnlyAccess())
	    	jdbcFile.setGrantedAccess( NetworkFile.WRITEONLY);
	    else
	    	jdbcFile.setGrantedAccess( NetworkFile.READWRITE);
	    
	    //  Set the file owner
	    
	    if ( sess != null)
	      jdbcFile.setOwnerSessionId(sess.getUniqueId());
	      
	    // Save the access token
	    
	    jdbcFile.setAccessToken( accessToken);
	}
	finally {
		
		// If the file object is not valid then release the file access that was granted
		
		if ( jdbcFile == null)
			dbCtx.getStateCache().releaseFileAccess(fstate, accessToken);
	}
	
    //  Return the network file
        
    return jdbcFile;
  }

  /**
   * Read a block of data from a file
   * 
   * @param sess  Session details
   * @param tree  Tree connection
   * @param file  Network file
   * @param buf   Buffer to return data to
   * @param bufPos Starting position in the return buffer
   * @param siz   Maximum size of data to return
   * @param pos   File offset to read data
   * @return Number of bytes read
   * @exception IOException
   */
  public int readFile(SrvSession sess, TreeConnection tree, NetworkFile file, byte[] buf, int bufPos, int siz, long pos)
    throws IOException {
      
    //  Debug
//	  log4j.debug("DBD#readFile() fileId:"+file.getFileId()+",name:"+file.getName()+",fileSize:"+file.getFileSize()+",filePos=" + pos + ", len=" + siz);
    //  Access the JDBC context

    DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

    // Check if the database is online
    
    if ( dbCtx.getDBInterface().isOnline() == false)
    {
    	log4j.error("Database is offline");
      throw new DiskOfflineException( "Database is offline");
    }

    //  Check that the network file is our type

    int rxsiz = 0;
    
    if (file instanceof DBNetworkFile) {

      //  Access the JDBC network file
      
      DBNetworkFile jfile = (DBNetworkFile) file;

      //  Check if there are any locks on the file
      
      //  Check if there are any locks on the file
      
      if ( jfile.hasFileState() && jfile.getFileState().hasActiveLocks()) {
        
        //  Check if this session has write access to the required section of the file
        
        if ( jfile.getFileState().canReadFile( pos, siz, sess.getProcessId()) == false)
        {
        	log4j.error("DBD#readFile() LockConflictException jfile.getFileState().canReadFile == false");
          throw new LockConflictException();
        }
      }
      
      //  Read from the file

      rxsiz = jfile.readFile(buf, siz, bufPos, pos);
      
      
      //  Check if we have reached the end of file
      if ( rxsiz == -1)
      {
    	  log4j.debug("DBD#readFile() rxsiz==-1 未读到数据");
        rxsiz = 0;
      }
    }

    //  Return the actual read length

    return rxsiz;
  }

  /**
   * Rename a file
   * 
   * @param sess  Session details
   * @param tree  Tree connection
   * @param oldName Existing file name
   * @param newName New file name
   * @exception IOException
   */
  public void renameFile(SrvSession sess, TreeConnection tree, String oldName, String newName)
    throws IOException {
    
    //  Debug
	  String userName = sess.getClientInformation().getUserName();
	  String shareName = tree.getContext().getShareName();
	  log4j.debug("DBD#renameFile() ,time:"+System.currentTimeMillis()+", from=" + oldName + " to=" + newName);
	  if (oldName.equalsIgnoreCase("\\"+userName+DBUtil.SPECIAL_CHAR)) {
	    	log4j.warn(oldName+" ,不允许改名");
	    	throw new AccessDeniedException("用户目录不允许改名");
	  }
    //  Access the JDBC context

    DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

    // Check if the database is online
    
    if ( dbCtx.getDBInterface().isOnline() == false)
    {
    	log4j.error("Data is offline");
      throw new DiskOfflineException( "Database is offline");
    }
    
    //  Get, or create, the file state for the existing file
    
    FileState fstate = getFileState(oldName, dbCtx, true);
    
    try {
      //  Get the file name
      
      String[] paths = FileName.splitPath( newName);
      String newFname = paths[1];
      
      String[] oldPaths = FileName.splitPath(oldName);
      String oldFname = oldPaths[1];
      
      //  Check if the file name is too long
      
      if ( newFname != null && newFname.length() > MaxFileNameLen)
      {
    	  log4j.error("Destination name too long, " + newFname);
        throw new FileNameException("Destination name too long, " + newFname);
      }
      
      //  Check if the file is within an active retention period
      
      getRetentionDetailsForState( dbCtx, fstate);
      
      if ( fstate.hasActiveRetentionPeriod())
      {
    	  log4j.error("File retention active");
        throw new AccessDeniedException("File retention active");
      }

      //  Get the file id of the existing file
      
      int fid = fstate.getFileId();
      int dirId = -1;
      
      if ( fid == -1) {
        
        //  Split the current path string and find the file id of the existing file/directory
        
        dirId = findParentDirectoryId(dbCtx, oldName, true,userName,shareName);
        if ( dirId == -1)
        {
        	log4j.error("DBD#renameFile() dirId==-1 FileNotFoundException ,name:"+oldName);
          throw new FileNotFoundException(oldName);
        }
    
        //  Get the current file/directory name
        
        int userId = 0;
    	try{
    		UserBean user = dbCtx.getDBInterface().getUserByUsername(userName);
    		if(null != user)
    		{
    			userId = user.getId();
    		}
    	}
    	catch (DBException ex) {
    		log4j.error("User is not exist from database, userId:"+userId+" username:"+userName,ex);
    		throw new AccessDeniedException("User is not exist from database, userId:"+userId+" username:"+userName,ex);
    	}
        //  Get the file id
        
        fid = getFileId(oldName, oldFname, dirId, dbCtx,userId,shareName,userName);
        if ( fid == -1)
        {
        	log4j.error("DBD#renameFile() fid==-1 FileNotFoundException ,name:"+oldName);
          throw new FileNotFoundException(oldName);
        } 
        //  Update the file state
        
        fstate.setFileId(fid);
      }

      //  Get the existing file/directory details
      
      DBFileInfo curInfo = getFileDetails(oldName, dbCtx, fstate,userName,shareName);
      if ( dirId == -1 && curInfo != null)
        dirId = curInfo.getDirectoryId();
      
      //  Check if the new name file/folder already exists
      
//      DBFileInfo newInfo = getFileDetails(newName, dbCtx);
      DBFileInfo newInfo = getFileDetails(newName, dbCtx, null,userName,shareName);//防止shareName 和userName为空的情况
      if ( newInfo != null)
      {
//    	  log4j.warn("Rename to file/folder already exists,继续保存" + newName);
//    	  boolean isWord2003 = oldName.toLowerCase().endsWith(".tmp") && oldName.indexOf("~")>-1;
    	  boolean isWord2003 = oldFname.startsWith("~") && oldFname.toLowerCase().endsWith(".tmp") && newName.toLowerCase().endsWith(".doc");
    	  if(isWord2003)
    	  {
    		  //office 保存word文件时，如果存在文件又重命名，会提示保存失败。所以这里这样处理
    		  log4j.error("isWord2003 FileExistsException oldName:"+oldName+" ,newName:" + newName);
    		  throw new FileExistsException("Rename to file/folder already exists," + newName);
    	  }
      }
      //如果是word,wps执行将文件改名为其它文件，则不执行
      String oldExt = DiskUtil.getExt(oldFname);
      String newExt = DiskUtil.getExt(newFname);
      boolean isWpsRename = !oldFname.toLowerCase().endsWith(".tmp") && newFname.toLowerCase().endsWith(".tmp");
      boolean isWordRename = !oldFname.startsWith("~$") && newFname.startsWith("~$");
      if(isWpsRename || isWordRename)
      {
    	  log4j.warn("DBD#renameFile() 改名操作不执行!isWpsRename:"+isWpsRename+" , isWordRename:"+isWordRename+", oldFname:"+oldFname+", newFname:"+newFname);   	  
//    	  fstate.removeAllAttributes();
//    	  FileState newFstate = getFileState(newName, dbCtx, true);
//    	  //file id改变了，可能存在。则清缓存
//    	  if(null != newFstate)
//    	  {
//    		  newFstate.setFileId(0);
//    		  newFstate.setFileStatus( FileStatus.FileExists, FileState.ReasonFileCreated);
//    	  }    	  
//    	  log4j.error("DBD#renameFile() 改名创建的假缓存文件 newFstate:"+newFstate);
      }
      else
      {
    	  
          //  Check if the loader handles rename requests, an exception may be thrown by the loader
          //  to prevent the file/directory rename.
          //移到到后面实现
//          if ( dbCtx.getFileLoader() instanceof NamedFileLoader) {
//            
//            //  Rename the file/directory
//            
//            NamedFileLoader namedLoader = (NamedFileLoader) dbCtx.getFileLoader();
//            namedLoader.renameFileDirectory(oldName, fid, newName, curInfo.isDirectory());
//          }
          
          //  Get the new file/directory name
          
          int newDirId = findParentDirectoryId(dbCtx, newName, true,userName,shareName);
          if ( newDirId == -1)
          {
        	  log4j.error("DBD#renameFile() newDirId==-1 FileNotFoundException , name:"+newName);
            throw new FileNotFoundException(newName);
          }
          
	      //  Rename the file/folder, this may also link the file/folder to a new parent directory
	      int newFid = dbCtx.getDBInterface().renameFileRecord(dirId, fid, newFname, newDirId,shareName, null);
	      //处理改名问题
	      FileSegmentInfo fileSegInfo = (FileSegmentInfo) fstate.findAttribute(ObjectIdFileLoader.DBFileSegmentInfo);
    	  if(null !=fileSegInfo)
    	  {
    	  dbCtx.getDBInterface().modifyFileTemporaryFile(newFid,fileSegInfo,shareName);//修改上传的null路径为临时文件路径
    	  }
	      if(newFid != fid)
	      {
	    	  if ( dbCtx.getFileLoader() instanceof NamedFileLoader) {
	              //  Rename the file/directory
	              NamedFileLoader namedLoader = (NamedFileLoader) dbCtx.getFileLoader();
	              namedLoader.renameFileDirectory(oldName, newFid, newName, curInfo.isDirectory());
	          }
	    	  fstate = getFileState(newName, dbCtx, true);
	    	  //file id改变了，可能存在。则清缓存
	    	  if(null != fstate)
	    	  {
	    		  fstate.setFileId(newFid);
	    		  
	    		  if(null != fileSegInfo)
	    		  {
	    			  File temFile = new File(fileSegInfo.getTemporaryFile());
	    			  if(null != temFile && temFile.length()>0)
	    			  {
	    				  fstate.setFileSize(temFile.length());//重设文件大小
	    			  }
	    		  }
	    	  }
	    	  fstate.setFileStatus( FileStatus.FileExists, FileState.ReasonFileCreated);
	    	  /*
	    	     这部分可能导致保存后，提示权限不对。暂不执行
	    	  //删除原来的
	          fstate.setFileStatus( FileStatus.NotExist, FileState.ReasonFileDeleted);
	          fstate.setFileId(-1); 
	          fstate.removeAttribute(FileState.FileInformation);
	    	  fstate = getFileState(newFname, dbCtx, true);//重新获得对象
	    	  //创建
	    	  fstate.setFileStatus( FileStatus.FileExists, FileState.ReasonFileCreated);
	          //  Save the file id
	          fstate.setFileId(newFid);
	    	  */
	      }
	      else
	      {
	    	  if ( dbCtx.getFileLoader() instanceof NamedFileLoader) {
	              //  Rename the file/directory
	              NamedFileLoader namedLoader = (NamedFileLoader) dbCtx.getFileLoader();
	              namedLoader.renameFileDirectory(oldName, fid, newName, curInfo.isDirectory());
	            }
	      }
	      //  Update the file state with the new file name/path
	      dbCtx.getStateCache().renameFileState(newName, fstate, curInfo.isDirectory());
	      fstate.removeAllAttributes();
      }
    }
    catch (DBException ex) {
    	log4j.error("DBD#renameFile() FileNotFoundException , name:"+oldName+" , newName:"+newName,ex);
      throw new FileNotFoundException(oldName);
    }
  }

  /**
   * Seek to the specified point within a file
   * 
   * @param sess  Session details
   * @param tree  Tree connection
   * @param file  Network file
   * @param pos   New file position
   * @param typ   Seek type
   * @return  New file position
   * @exception IOException
   */
  public long seekFile(SrvSession sess, TreeConnection tree, NetworkFile file, long pos, int typ)
    throws IOException {
    
    //  Debug
  	log4j.error("DBD#seekFile() pos:"+pos+" , typ:"+typ);

    //  Check that the network file is our type

    long newpos = 0;
    
    if (file instanceof DBNetworkFile) {

      //  Seek within the file

      DBNetworkFile jfile = (DBNetworkFile) file;
      newpos = jfile.seekFile(pos, typ);
    }

    //  Return the new file position

    return newpos;
  }

  /**
   * Set file information
   * 
   * @param sess  Session details
   * @param tree  Tree connection
   * @param name  File name
   * @param info  File information to be set
   * @exception IOException
   */
  public void setFileInformation(SrvSession sess, TreeConnection tree, String name, FileInfo info)
    throws IOException {
      
    //  Debug
	  String userName = sess.getClientInformation().getUserName();
	  String shareName = tree.getContext().getShareName();
	  log4j.debug("DBD#setFileInformation() name=" + name + ", info=" + info + ", set flags=" + info.getSetFileInformationFlagsString());

    // If the only flag set is the delete on close flag then return, nothing to do
    
    if ( info.getSetFileInformationFlags() == FileInfo.SetDeleteOnClose)
    	return;
    
    //  Access the JDBC context

    DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

    // Check if the database is online
    
    if ( dbCtx.getDBInterface().isOnline() == false)
    {
    	log4j.error("Database is offline");
        throw new DiskOfflineException( "Database is offline");
    }
    
    //  Get, or create, the file state
    
    FileState fstate = getFileState(name, dbCtx, true);
    
    //  Get the file details
    
    DBFileInfo dbInfo = getFileDetails( name, dbCtx, fstate,userName,shareName);
    if ( dbInfo == null)
    {
    	log4j.error("FileNotFoundException :"+name);
      throw new FileNotFoundException(name);
    }

    try {

      //  Check if the file is within an active retention period
      
      getRetentionDetailsForState( dbCtx, fstate);
      
      if ( fstate.hasActiveRetentionPeriod())
        throw new AccessDeniedException("File retention active");
      
      //  Check if the loader handles set file information requests, an exception may be thrown by the loader
      //  to prevent the update
      
      if ( dbCtx.getFileLoader() instanceof NamedFileLoader) {
        
        //  Set the file information
        
        NamedFileLoader namedLoader = (NamedFileLoader) dbCtx.getFileLoader();
        namedLoader.setFileInformation(name, dbInfo.getFileId(), info);
      }

      //  Validate any timestamp updates
      //
      //  Switch off invalid updates from being written to the database but allow them to be cached.
      //  To allow test apps such as IFSTEST to complete successfully.
      
      int origFlags = info.getSetFileInformationFlags();
      int dbFlags   = origFlags;
      
      if ( info.hasSetFlag(FileInfo.SetAccessDate) && info.getAccessDateTime() > MaxTimestampValue)
    	  dbFlags -= FileInfo.SetAccessDate;
      
      if ( info.hasSetFlag(FileInfo.SetCreationDate) && info.getCreationDateTime() > MaxTimestampValue)
    	  dbFlags -= FileInfo.SetCreationDate;
      
      if ( info.hasSetFlag(FileInfo.SetModifyDate) && info.getModifyDateTime() > MaxTimestampValue)
    	  dbFlags -= FileInfo.SetModifyDate;

      //  Check if the inode change date/time has been set

      if ( info.hasChangeDateTime() == false) {
        info.setChangeDateTime(System.currentTimeMillis());
        if ( info.hasSetFlag(FileInfo.SetChangeDate) == false)
          info.setFileInformationFlags(info.getSetFileInformationFlags() + FileInfo.SetChangeDate);
      }
      else if ( info.hasSetFlag(FileInfo.SetChangeDate) && info.getChangeDateTime() > MaxTimestampValue)
    	  dbFlags -= FileInfo.SetChangeDate;
              
      // Check if file attributes are being set
      
      if ( info.hasSetFlag(FileInfo.SetAttributes)) {

    	  // Check if this is a folder, make sure the Directory attribute does not get reset
	  
		  if ( dbInfo.isDirectory() && (info.getFileAttributes() & FileAttribute.Directory) == 0)
			  info.setFileAttributes( info.getFileAttributes() + FileAttribute.Directory);
      }
      
      //  Update the information flags for the database update
      
      info.setFileInformationFlags( dbFlags);
      
      //  Update the file information
      
      if ( dbFlags != 0)
    	  dbCtx.getDBInterface().setFileInformation(dbInfo.getDirectoryId(), dbInfo.getFileId(), info,shareName);

      //  Use the original information flags when updating the cached file information details
      
      info.setFileInformationFlags(origFlags);
      
      //  Copy the updated values to the file state
      
      if ( info.hasSetFlag(FileInfo.SetFileSize))
        dbInfo.setFileSize(info.getSize());
      
      if ( info.hasSetFlag(FileInfo.SetAllocationSize))
        dbInfo.setAllocationSize(info.getAllocationSize());
      
      if ( info.hasSetFlag(FileInfo.SetAccessDate))
        dbInfo.setAccessDateTime(info.getAccessDateTime());
      
      if ( info.hasSetFlag(FileInfo.SetCreationDate))
        dbInfo.setAccessDateTime(info.getCreationDateTime());
      
      if ( info.hasSetFlag(FileInfo.SetModifyDate))
        dbInfo.setAccessDateTime(info.getModifyDateTime());
      
      if ( info.hasSetFlag(FileInfo.SetChangeDate))
        dbInfo.setAccessDateTime(info.getChangeDateTime());

      if ( info.hasSetFlag(FileInfo.SetGid))
        dbInfo.setGid(info.getGid());
      
      if ( info.hasSetFlag(FileInfo.SetUid))
        dbInfo.setUid(info.getUid());
      
      if ( info.hasSetFlag(FileInfo.SetMode))
        dbInfo.setMode(info.getMode());
      
      if ( info.hasSetFlag(FileInfo.SetAttributes))
    	  dbInfo.setFileAttributes(info.getFileAttributes());
      
      //  Update the file state
      
      fstate.setFileId(dbInfo.getFileId());
    }
    catch (DBException ex) {
    	log4j.error(ex);
      throw new IOException();
    }
  }

  /**
   * Start a search of the file system
   * 
   * @param sess SrvSession
   * @param tree TreeConnection
   * @param searchPath String
   * @param attrib int
   * @return SearchContext
   * @exception FileNotFoundException
   */
  public SearchContext startSearch(SrvSession sess, TreeConnection tree, String searchPath, int attrib)
    throws FileNotFoundException {

    //  Access the JDBC context
	String shareName = tree.getContext().getShareName();
    DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();
    String userName = sess.getClientInformation().getUserName();
    
    log4j.debug("##DBD#startSearch  searchPath:"+searchPath+",userName:"+userName+", shareName:"+shareName);
    // Check if the database is online
    
    if ( dbCtx.getDBInterface().isOnline() == false)
    {
    	log4j.error("Database is offline");
      throw new FileNotFoundException( "Database is offline");
    }
    //debug
//    log4j.debug("DBD#startSearch searchPath:"+searchPath+", shareName:"+tree.getContext().getShareName()+" ,attrib:"+attrib);
    
    //  Prepend a leading slash to the path if not on the search path
    if ( searchPath.startsWith("\\") == false)
      searchPath = "\\" + searchPath;
    if(StringUtils.isNotEmpty(searchPath) && !searchPath.equals("\\") && !searchPath.equals("\\*")  && searchPath.toLowerCase().startsWith("\\"+userName.toLowerCase()+DBUtil.SPECIAL_CHAR)==false)
    {
    	log4j.warn("DBD#startSearch  不符合规则的searchPath值!  searchPath:"+searchPath+",userName:"+userName);
//    	return null;
    	sess.setLoggedOn(false);
    	sess.closeSession();
    	userName = searchPath.substring(searchPath.indexOf("\\")+1);
    	if(userName.indexOf(DBUtil.SPECIAL_CHAR)>0)
    	{
    		userName = userName.substring(0, userName.indexOf(DBUtil.SPECIAL_CHAR));
    	}
    	else if(userName.indexOf("\\")>0)
    	{
    		userName = userName.substring(0, userName.indexOf("\\"));
    	}
    	 sess.getClientInformation().setUserName(userName);
    	log4j.warn("重置userName : "+userName);
    }
    
    //  Get the directory id for the last directory in the path
//    log4j.debug("DBD#startSearch findParentDirectoryId  ,searchPath:"+searchPath);
    int dirId = findParentDirectoryId(dbCtx,searchPath,true,userName,shareName);
    if ( dirId == -1)
    {
//    	log4j.error("DBD#startSearch dirId==-1, Invalid Path "+searchPath);
      throw new FileNotFoundException("Invalid path");
    }

    //  Start the search
    
    SearchContext search = null;
    
    try {
    
      //  Check if the search path is a none wildcard search, the file information may be in the
      //  state cache  
      
      if ( WildCard.containsWildcards( searchPath) == false) {
        
        //  Check if there is a file state for the search path
        
        FileState searchState = getFileState( searchPath, dbCtx, false);
        if ( searchState != null && searchState.fileExists() == true) {
          
          //  Check if the file state has the file information attached
          
          DBFileInfo finfo = (DBFileInfo) searchState.findAttribute(FileState.FileInformation);
          
          if ( finfo != null) {
            
            //  Create a single file search context using the cached file information
//        	log4j.debug("DBD#startSearch finfo != null  ,finfo:"+finfo.getFileId()+" ,fullName:"+finfo.getFullName());
            search = new CachedSearchContext( finfo);
            
            //  DEBUG
//            log4j.debug("DBD#StartSearch using cached file information, path=" + searchPath + ", info=" + finfo);
          }
        }
      }
      
      //  Start the search via the database interface, if the search is not valid
      
      if ( search == null) {
      
        // Start the search

        DBSearchContext dbSearch = dbCtx.getDBInterface().startSearch(dirId, searchPath, attrib, DBInterface.FileAll, -1,userName,shareName);
        
        // Check if files should be marked as offline
        
        dbSearch.setMarkAsOffline( dbCtx.hasOfflineFiles());
        dbSearch.setOfflineFileSize( dbCtx.getOfflineFileSize());
        
//        log4j.debug("DBD#startSearch getDBInterface().startSearch end ,dirId:"+dirId+" , searchPath:"+searchPath);
        search = dbSearch;  
      }
    }
    catch ( DBException ex) {
    	log4j.error(ex);
      throw new FileNotFoundException();
    }

    //  Return the search context

    return search;
  }

  /**
   * Truncate a file to the specified size
   * 
   * @param sess   Server session
   * @param tree   Tree connection
   * @param file   Network file details
   * @param siz    New file length
   * @exception java.io.IOException The exception description.
   */
  public void truncateFile(SrvSession sess, TreeConnection tree, NetworkFile file, long siz)
    throws java.io.IOException {
      
    //  Debug
	  String userName = sess.getClientInformation().getUserName();
	  String shareName = tree.getContext().getShareName();

    //  Check that the network file is our type

    if (file instanceof DBNetworkFile) {

      //  Access the JDBC context

      DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

      //  Get the JDBC file
      
      DBNetworkFile jfile = (DBNetworkFile) file;
      
//      log4j.debug("DBD#truncateFile(),fullName:"+jfile.getFullName());
      if(jfile.isStream())
      {
    	  log4j.warn("DBD#truncateFile(),附加流不截断 fullName:"+jfile.getFullName());
    	  return;
      }
      
      //  Get, or create, the file state
    
      FileState fstate = jfile.getFileState();
    
      //  Get the file details
    
      DBFileInfo dbInfo = getFileDetails(jfile.getFullName(),dbCtx,fstate,userName,shareName);
      if ( dbInfo == null)
      {
    	  log4j.error("FileNotFoundException ,fullName:"+jfile.getFullName());
        throw new FileNotFoundException(jfile.getFullName());
      }

      //  Check if the new file size is greater than the maximum allowed file size, if enabled
      
      if ( dbCtx.hasMaximumFileSize() && siz > dbCtx.getMaximumFileSize()) {
        
        // Mark the file to delete on close
        
        file.setDeleteOnClose( true);

        // Return a disk full error
        log4j.error("Write is beyond maximum allowed file size");
        throw new DiskFullException( "Write is beyond maximum allowed file size");
      }
      
      //  Keep track of the allocation/release size in case the file resize fails
      
      long allocSize   = 0L;
      long releaseSize = 0L;
      
      //  Check if there is a quota manager

      QuotaManager quotaMgr = dbCtx.getQuotaManager();
            
      if ( dbCtx.hasQuotaManager()) {
        
        //  Determine if the new file size will release space or require space allocating
        
        if ( siz > dbInfo.getAllocationSize()) {
          
          //  Calculate the space to be allocated
          
          allocSize = siz - dbInfo.getAllocationSize();
          
          //  Allocate space to extend the file
          
          quotaMgr.allocateSpace(sess, tree, file, allocSize);
        }
        else {
          
          //  Calculate the space to be released as the file is to be truncated, release the space if
          //  the file truncation is successful
          
          releaseSize = dbInfo.getAllocationSize() - siz;
        }
      }
      
      //  Set the file length

      try {
        jfile.truncateFile(siz);
      }
      catch (IOException ex) {
        
        //  Check if we allocated space to the file
        
        if ( allocSize > 0 && quotaMgr != null)
          quotaMgr.releaseSpace(sess, tree, file.getFileId(), null, allocSize);

        //  Rethrow the exception
        log4j.error(ex);
        throw ex;       
      }
      
      //  Check if space has been released by the file resizing
      
      if ( releaseSize > 0 && quotaMgr != null)
        quotaMgr.releaseSpace(sess, tree, file.getFileId(), null, releaseSize);
        
      //  Update the file information
      
      if ( allocSize > 0)
        dbInfo.setAllocationSize(dbInfo.getAllocationSize() + allocSize);
      else if ( releaseSize > 0)
        dbInfo.setAllocationSize(dbInfo.getAllocationSize() - releaseSize);
        
      //  Update the last file change date/time
                
      try {

        //  Build the file information to set the change date/time
        
        FileInfo finfo = new FileInfo();
        
        finfo.setChangeDateTime(System.currentTimeMillis());
        finfo.setFileInformationFlags(FileInfo.SetChangeDate);
        
        //  Set the file change date/time
        
        dbCtx.getDBInterface().setFileInformation(jfile.getDirectoryId(), jfile.getFileId(), finfo,shareName);
        
        //  Update the cached file information
        
        dbInfo.setChangeDateTime(finfo.getChangeDateTime());
        dbInfo.setAllocationSize(siz);
      }
      catch (Exception ex) {       
    	  log4j.error(ex);
      }
    }
  }

  /**
   * Write a block of data to a file
   * 
   * @param sess  Session details
   * @param tree  Tree connection
   * @param file  Network file
   * @param buf   Data to be written
   * @param bufoff Offset of data within the buffer
   * @param siz   Number of bytes to be written
   * @param fileoff Offset within the file to start writing the data
   */
  public int writeFile(SrvSession sess,TreeConnection tree,NetworkFile file,byte[] buf,int bufoff,int siz,long fileoff)
    throws IOException {
      
    //  Debug
	  String userName = sess.getClientInformation().getUserName();
	  String shareName = tree.getContext().getShareName();
//	  log4j.debug("DBD#writeFile() fid:"+file.getFileId()+",size:"+file.getFileSize()+",isStream:"+file.isStream()+",name:"+file.getFullName()+" , siz:"+siz+", fileoff:"+fileoff);

    //  Access the JDBC context

    DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

    // Check if the database is online
    
    if ( dbCtx.getDBInterface().isOnline() == false)
      throw new DiskOfflineException( "Database is offline");
    
    //  Check if the file name is a stream
    
    if (file.isStream()) {
    	log4j.warn("DBD#writeFile() NTFS Alternate Data Streams 交换数据流 ，不做保存处理 , fid:"+file.getFileId()+",name:"+file.getFullName()+" , siz:"+siz+", fileoff:"+fileoff);
//    	return 0;//报错
    	return siz;
    }

    //  Check that the network file is our type

    if (file instanceof DBNetworkFile) {

      //  Access the JDBC network file

      DBNetworkFile jfile = (DBNetworkFile) file;

      //  Check if there are any locks on the file
      
      if ( jfile.hasFileState() && jfile.getFileState().hasActiveLocks()) {
        
        //  Check if this session has write access to the required section of the file
        
        if ( jfile.getFileState().canWriteFile( fileoff, siz, sess.getProcessId()) == false)
          throw new LockConflictException();
      }
      
      // Check if there is a maximum file size, if so then check if the write is beyond the allowed file size
      
      if ( dbCtx.hasMaximumFileSize() && (fileoff + siz) > dbCtx.getMaximumFileSize()) {
        
        // Mark the file to delete on close
        
        file.setDeleteOnClose( true);

        // Return a disk full error
        log4j.error("Write is beyond maximum allowed file size");
        throw new DiskFullException( "Write is beyond maximum allowed file size");
      }
      
      //  Check if there is a quota manager
      
      QuotaManager quotaMgr = dbCtx.getQuotaManager();
      
      if ( quotaMgr != null) {
        
        //  Get the file information
        
        DBFileInfo finfo = getFileDetails(jfile.getFullName(), dbCtx, jfile.getFileState(),userName,shareName);
        if ( finfo == null)
        {
        	log4j.error("FileNotFoundException name:"+jfile.getFullName());
          throw new FileNotFoundException(jfile.getFullName());
        }
        
        //  Check if the file requires extending
        
        long extendSize = 0L;
        long endOfWrite = fileoff + siz;
        
        if ( endOfWrite > finfo.getSize()) {
          
          //  Calculate the amount the file must be extended

          extendSize = endOfWrite - finfo.getSize();
          
          //  Allocate space for the file extend
          
          quotaMgr.allocateSpace(sess, tree, file, extendSize);
        }
                
        //  Write to the file
        
        try {
          jfile.writeFile(buf, siz, bufoff, fileoff);
        }
        catch (IOException ex) {
        
          //  Check if we allocated space to the file
        
          if ( extendSize > 0 && quotaMgr != null)
            quotaMgr.releaseSpace(sess, tree, file.getFileId(), null, extendSize);

          //  Rethrow the exception
          log4j.error(ex);
          throw ex;       
        }

        //  Update the file information
      
        if ( extendSize > 0)
            finfo.setAllocationSize(MemorySize.roundupLongSize(endOfWrite));
      }
      else {      

        //  Write to the file
        
        jfile.writeFile(buf, siz, bufoff, fileoff);
      }
    }

    //  Return the actual write length

    return siz;
  }

  /**
   * Parse/validate the parameter string and create a device context for this share
   * 
   * @param shareName String
   * @param args ConfigElement
   * @return DeviceContext
   * @exception DeviceContextException
   */
  public DeviceContext createContext(String shareName, ConfigElement args)
    throws DeviceContextException {
	
    //  Check the arguments

    if (args.getChildCount() < 3)
    {
    	log4j.error("Not enough context arguments");
      throw new DeviceContextException("Not enough context arguments");
    }

    //  Check for the debug enable flags
    
    if ( args.getChild("Debug") != null)
      m_debug = true;

    //  Create the database device context

    DBDeviceContext ctx = new DBDeviceContext(args);
    //  Return the database device context

    return ctx;
  }


  /**
   * Get the file id for a file
   * 
   * @param path String
   * @param dbCtx DBDeviceContext
   * @return DBFileInfo
   */
  protected final DBFileInfo getFileDetails(String path, DBDeviceContext dbCtx) {
	  
    return getFileDetails(path, dbCtx, null,null,dbCtx.getShareName());
  }
  
  /**
   * Get the file id for a file
   * 
   * @param path String
   * @param dbCtx DBDeviceContext
   * @param fstate FileState
   * @return DBFileInfo
   */
  protected final DBFileInfo getFileDetails(String path, DBDeviceContext dbCtx, FileState fstate,String userName,String shareName) {
	//debug
//	log4j.debug("DBD#getFileDetails ;path:"+path+" , userName:"+path+" , shareName:"+shareName);
    //  Check if the file details are attached to the file state
	String ext = DiskUtil.getExt(path);
    if ( fstate != null) {      
      //  Return the file information      
      DBFileInfo finfo = (DBFileInfo) fstate.findAttribute(FileState.FileInformation);
      if ( finfo != null)
          return finfo;
////      log4j.debug("DBD#getFileDetails fstate != null ;fileId:"+fstate.getFileId()+" ,fullName:"+fstate.getPath()+" , status:"+fstate.getFileStatus());
//      if (fstate.getFileStatus()==FileStatus.NotExist && DBUtil.SUPPORT_EXT.contains(ext))
//      {
//    	  log4j.debug("DBD#getFileDetails fstate != null 重新执行下面的获得-------"+path);
//      }
//      else if ( finfo != null)
//        return finfo;
    }
    
    //  Check for the root directory    
    if ( path.length() == 0 || path.compareTo("\\") == 0) {      
      //  Get the root directory information from the device context
      DBFileInfo rootDir = dbCtx.getRootDirectoryInfo();
      
      //  Mark the directory as existing
      if ( fstate != null)
        fstate.setFileStatus(FileStatus.DirectoryExists);
      
//      log4j.debug("DBD#getFileDetails 返回root目录   fileId:"+rootDir.getFileId()+" ,fileName:" + rootDir.getFileName());
      
      return rootDir;
    }
    
    if(path.equalsIgnoreCase("\\"+userName+DBUtil.SPECIAL_CHAR))  //新增加
    {
    //  Get the root directory information from the device context
        DBFileInfo rootDir = dbCtx.getRootDirectoryInfo();
        rootDir.setPath(path);
        rootDir.setFileName(path);
        rootDir.setFileName(userName+DBUtil.SPECIAL_CHAR);
        //  Mark the directory as existing
        if ( fstate != null)
          fstate.setFileStatus(FileStatus.DirectoryExists);
        
//        log4j.debug("DBD#getFileDetails 返回root目录   fileId:"+rootDir.getFileId()+" , userName:"+userName+" ,fileName:" + rootDir.getFileName());
        
        return rootDir;
    }
    
    if(StringUtils.isNotEmpty(path) && !path.equals("\\") && !path.equals("\\*")  && path.toLowerCase().startsWith("\\"+userName.toLowerCase()+DBUtil.SPECIAL_CHAR)==false)
    {
    	log4j.warn("DBD#getFileDetails  不符合规则的searchPath值!  path:"+path+",userName:"+userName);
    	if(path.toLowerCase().startsWith("\\"+userName.toLowerCase()))
    	{
    		path = path.replaceFirst(userName, userName+DBUtil.SPECIAL_CHAR);
    		log4j.warn("DBD#getFileDetails  不符合规则的searchPath值!  重置path:"+path+",userName:"+userName);
    	}
    	else
    	{
    		userName = path.substring(path.indexOf("\\")+1);
        	if(userName.indexOf(DBUtil.SPECIAL_CHAR)>0)
        	{
        		userName = userName.substring(0, userName.indexOf(DBUtil.SPECIAL_CHAR));
        	}
        	else if(userName.indexOf("\\")>0)
        	{
        		userName = userName.substring(0, userName.indexOf("\\"));
        	}
        	log4j.warn("重置userName : "+userName);
//    		return null;
    	}
    }
    
    //  Split the path string and find the parent directory id    
    int dirId = findParentDirectoryId(dbCtx,path,true,userName,shareName);
    if ( dirId == -1)
    {
//    	log4j.warn("DBD#getFileDetails  findParentDirectoryId dirId =-1 ;path:"+path);
      return null;
    }
    
    int userId = 0;
	try{
		UserBean user = dbCtx.getDBInterface().getUserByUsername(userName);
		if(null != user)
		{
			userId = user.getId();
		}
	}
	catch (DBException ex) {
		log4j.error("获得用户id出错：userName:"+userName);
		return null;
	}

    // Strip any trailing slash from the path
    
    if ( path.length() > 1 && path.endsWith( FileName.DOS_SEPERATOR_STR))
    	path = path.substring( 0, path.length() - 1);
//    log4j.debug("DBD#getFileDetails path.substring  path:"+path);
    //  Get the file name
    
    String[] paths = FileName.splitPathStream(path);
    String fname = paths[1];
    
    String filePath = null;
    
    if ( paths[0] != null && paths[0].endsWith(FileName.DOS_SEPERATOR_STR) == false)
      filePath = paths[0] + FileName.DOS_SEPERATOR_STR + paths[1];
    else
      filePath = paths[0] + paths[1];

    //  Get the file id for the specified file
//    log4j.debug("DBD#getFileDetails path.substring path:"+path+" ,filePath:"+filePath);
    int fid = getFileId(filePath, fname, dirId, dbCtx,userId,shareName,userName);
    if (fid == -1) {    	
    	// Set the file status as not existing
    	if ( fstate != null)
    		fstate.setFileStatus( FileStatus.NotExist);
    	
    	return null;
//    	log4j.warn("DBD#getFileDetails fid == -1;dirId:"+dirId+" ,fname:"+fname);
//    	if (DBUtil.SUPPORT_EXT.contains(ext))//
//    	{
//    		log4j.debug("DBD#getFileDetails fstate != null 重新执行下面的获得2222-------"+filePath);
//    	}
//    	else 
//    		return null;
    }
    
    //  Create the file information
    
    DBFileInfo finfo = getFileInfo( filePath, dirId, fid, dbCtx,userName,shareName);
    
    if ( finfo != null && fstate != null) {
      
      //  Set various file state details
      
      fstate.setFileStatus( finfo.isDirectory() ? FileStatus.DirectoryExists : FileStatus.FileExists);
      fstate.setFileId(finfo.getFileId());
      
      //  Set the file name
      
      finfo.setFileName( fname);
      finfo.setFullName(path);
      
      // Check if files should be marked as offline
      
      if ( dbCtx.hasOfflineFiles() && finfo.hasAttribute( FileAttribute.NTOffline) == false) {
        if ( dbCtx.getOfflineFileSize() == 0 || finfo.getSize() >= dbCtx.getOfflineFileSize())
          finfo.setFileAttributes( finfo.getFileAttributes() + FileAttribute.NTOffline);
      }
    }
    else if ( finfo == null && fstate != null) {
    	
    	// Set the file status
    	
    	fstate.setFileStatus( FileStatus.NotExist);
    }

    //  Check if the path is a file stream
    
    if ( paths[2] != null) {
//    	log4j.debug("DBD#getFileDetails paths[2] != null paths:"+paths);
      //  Get the file information for the stream
      
      finfo = getStreamInfo(fstate, paths, dbCtx);
    }
    
    //  Return the file/stream information
    
    return finfo;
  }
  
  /**
   * Get the file id for a file
   * 
   * @param path String
   * @param name String
   * @param dirId int
   * @param dbCtx DBDeviceContext
   * @return int
   */
  protected final int getFileId(String path, String name, int dirId, DBDeviceContext dbCtx,int userId,String shareName,String userName) {

	 if(path.equalsIgnoreCase("\\"+userName+DBUtil.SPECIAL_CHAR) || path.equalsIgnoreCase("\\"+userName))  //新增加
	 {
		  return 0;//\admin@\
	 }
    //  Check if the file is in the cache
    
    FileStateCache cache = dbCtx.getStateCache();
    FileState state = null;
    
    if ( cache != null) {
      
      //  Search for the file state

      state = cache.findFileState(path);
      if ( state != null) {

        //  Checkif the file id is cached
    	if(state.getFileId()==-1)
    	{
    		if(state.isDirectory())
    			return state.getFileId();//目录不存在，直接返回不存在
    		
//    		log4j.debug("@@ Cache hit - getFileId()==-1 name=" + name);
    		if(name.equalsIgnoreCase("desktop.ini") || name.equalsIgnoreCase("Thumbs.db") || name.equalsIgnoreCase("folder.jpg") || name.equalsIgnoreCase("folder.gif") || name.equalsIgnoreCase(".LFS"))
    			return state.getFileId();
    		
    		log4j.debug("@@ Cache hit - getFileId()==-1 ,status:"+state.getFileStatus()+" ,path:" + path+", name:"+name+" , dirId:"+dirId+" , shareName:"+shareName);
    	}
        
        if ( state.getFileId() != -1) {
        
          //  Debug
        log4j.debug("@@ Cache hit - getFileId() name=" + name);
          
          //  Return the file id
          
          return state.getFileId();
        }
        else if ( state.getFileStatus() == FileStatus.NotExist) {
          
          //  DEBUG
        log4j.debug("@@ Cache hit - getFileStatus() name=" + name + ", sts=NotExist");
          
          //  Indicate that the file does not exist
          
          return -1;
        }
      }
    }
    
    //  Get the file id from the database
    
    int fileId = -1;
    
    try {
    
      //  Get the file id
      
      fileId = dbCtx.getDBInterface().getFileId(dirId, name, false, false,userId,shareName,userName);
    }
    catch (DBException ex) {
    }

    //  Update the cache entry, if available
    
    if ( state != null)
      state.setFileId(fileId);
      
    //  Return the file id, or -1 if the file was not found

    return fileId;
  }

  /**
   * Load the retention details for a file state, if enabled
   * 
   * @param dbCtx DBDeviceContext
   * @param fstate FileState
   * @exception DBException
   */
  protected final void getRetentionDetailsForState(DBDeviceContext dbCtx, FileState fstate)
    throws DBException {

    //  If retention is enabled get the expiry date/time
    
    if ( dbCtx.hasRetentionPeriod()) {
        
      //  Get the file retention expiry date/time
      
      RetentionDetails retDetails = dbCtx.getDBInterface().getFileRetentionDetails(-1, fstate.getFileId());
      if ( retDetails != null)
        fstate.setRetentionExpiryDateTime( retDetails.getEndTime());
    }
  }
  
  /**
   * Find the directory id for the parent directory in the specified path
   * 
   * @param ctx DBDeviceContext
   * @param path String
   * @param filePath boolean
   * @return int
   */
  protected final int findParentDirectoryId(DBDeviceContext ctx, String path, boolean filePath,String userName,String shareName) {
	  
	if(null !=path && path.equalsIgnoreCase("\\"+userName+DBUtil.SPECIAL_CHAR))//用户根目录
		  return 0;
	
    //  Split the path
    String[] paths = null;
        
    if ( path != null && path.startsWith("\\")) {

      //  Split the path      
      paths = FileName.splitPath(path);
    }
    else {
      
      //  Add a leading slash to the path before parsing
      
      paths = FileName.splitPath("\\" + path);
    }
    
    if ( paths[0] != null && paths[0].compareTo("\\") == 0 || paths[0].startsWith("\\") == false)
      return 0;
      
    //  Check if the file is in the cache
    
    FileStateCache cache = ctx.getStateCache();
    FileState state = null;
    
    if ( cache != null) {
      
      //  Search for the file state
      
      state = cache.findFileState(paths[0]);
      if ( state != null && state.getFileId() != -1) {
        
        //  Debug
//    	  int pid = 0;
//        String path1 = paths[0];

//    	log4j.debug("@@ Cache hit - findParentDirectoryId() path=" + paths[0]);
        
        //  Return the file id
        
        return state.getFileId();
//        return pid;
      }
    }

    //  Get the directory id list
    
    int[] ids = findParentDirectoryIdList(ctx,path,filePath,userName,shareName);
    if ( ids == null)
    {
//    	log4j.warn("DBD#getFileDetails  findParentDirectoryIdList ids=null ;path:"+path);
      return -1;
    }
      
    //  Check for the root directory id only
    
    if ( ids.length == 1)
      return ids[0];
      
    //  Return the directory id of the last directory
    
    int idx = ids.length - 1;
    if ( filePath == true && ids[ids.length - 1] == -1)
      idx--;
      
    return ids[idx];
  }
      
  /**
   * Find the directory ids for the specified path list
   * 
   * @param ctx DBDeviceContext
   * @param path String
   * @param filePath boolean
   * @return int[]
   */
  protected final int[] findParentDirectoryIdList(DBDeviceContext ctx, String path, boolean filePath,String userName,String shareName) {
//	  log4j.debug("DBD#findParentDirectoryIdList() path:" +path+" , filePath:"+filePath+" ,userName:"+userName);
    //  Validate the paths and check for the root path
    String[] paths = FileName.splitAllPaths(path);
    
    if ( paths == null || paths.length == 0)
      return null;
    if ( paths[0].compareTo("*.*") == 0 || paths[0].compareTo("*") == 0 ||
        (filePath == true && paths.length == 1)||path.equals("\\"+userName+DBUtil.SPECIAL_CHAR)) {
      int[] ids = { 0 };
      return ids;
    }
    if ( paths[0].startsWith("\\")) {
      
      //  Trim the leading slash from the first path
      
      paths[0] = paths[0].substring(1);
    }
      
    //  Find the directory id by traversing the list of directories
    
    int endIdx = paths.length - 1;
    if ( filePath == true)
      endIdx--;
      
    //  If there are no paths to check then return the root directory id
    
    if ( endIdx <= 1 && paths[0].length() == 0) {
      int[] ids = new int[1];
      ids[0] = 0;
      return ids;
    }

    //  Allocate the directory id list
    
    int[] ids = new int[paths.length];
    for ( int i = 0; i < ids.length; i++)
      ids[i] = -1;
      
    //  Build up the current path as we traverse the list
    
    StringBuffer pathStr = new StringBuffer("\\");
    
    //  Check for paths in the file state cache
    
    FileStateCache cache = ctx.getStateCache();
    FileState fstate = null;

    //  Traverse the path list, initialize the directory id to the root id
    
    int dirId = 0;
    int parentId = -1;
    int idx = 0;
    
    int userId = 0;
    try {
	      UserBean user = ctx.getDBInterface().getUserByUsername(userName);
	  	  if(null != user)
	  	  {
	  		 userId = user.getId();
	  	  }
	  	  else
	      {
	    	  log4j.error("User is not exist from database ,userId:"+userId+", username:"+userName);
	    	  return null;
	      }
      
      //  Loop until the end of the path list

      while ( idx <= endIdx) {
        
        //  Get the current path, and add to the full path string
        
        String curPath = paths[idx];
        pathStr.append(curPath);
//        log4j.debug("DBD#findParentDirectoryIdList() idx:" +idx+" , curPath:"+curPath+" ,pathStr:"+pathStr+", parentId:"+parentId+",dirId:"+dirId);
        //  Check if there is a file state for the current path
        
        fstate = cache.findFileState(pathStr.toString());
        
        if ( fstate != null && fstate.getFileId() != -1) {
          
          //  Get the file id from the cached information

          ids[idx] = fstate.getFileId();
          parentId = dirId;
          dirId    = ids[idx];
        }
        else {
          
          //  Search for the current directory in the database

          parentId = dirId;
          
          dirId = ctx.getDBInterface().getFileId(dirId, curPath, true, true,userId,shareName,userName);
          
          if ( dirId != -1) {
            
            //  Get the next directory id

            ids[idx] = dirId;
            
            //  Check if we have a file state, or create a new file state for the current path
            
            if ( fstate != null) {
              
              //  Set the file id for the file state
              
              fstate.setFileId(dirId);
            }
            else {
              
              //  Create a new file state for the current path
              
              fstate = cache.findFileState(pathStr.toString(), true);
  
              //  Get the file information
              
              DBFileInfo finfo = ctx.getDBInterface().getFileInformation(parentId, dirId, DBInterface.FileAll,userId,shareName,userName);
              fstate.addAttribute(FileState.FileInformation, finfo);
              fstate.setFileStatus( finfo.isDirectory() ? FileStatus.DirectoryExists : FileStatus.FileExists);
              fstate.setFileId(dirId);
            }
          }
          else
          {
//        	  log4j.warn("DBD#findParentDirectoryIdList  dirId = ctx.getDBInterface().getFileId  dirId=-1 ;dirId:"+dirId+",curPath:"+curPath);
            return null;
          }
        }
            
        //  Update the path index
        
        idx++;
        
        //  Update the current path string
        
        pathStr.append("\\");
      }
    }
    catch (DBException ex) {
    	log4j.error(ex);
      return null;
    }
    
    //  Return the directory id list
        
    return ids;
  }
  
  /**
   * Return file information about the specified file, using the internal file id
   * 
   * @param path String
   * @param dirId int
   * @param fid int
   * @param dbCtx DBDeviceContext
   * @return DBFileInfo
   * @exception IOException
   */
  public DBFileInfo getFileInfo(String path, int dirId, int fid, DBDeviceContext dbCtx,String userName,String shareName) {

    //  Check if the file is in the cache
    FileState state = getFileState(path, dbCtx, true);
    
    if ( state != null && state.getFileId() != -1) {
        
      //  Debug
    	log4j.debug("@@ Cache hit - getFileInfo() path=" + path);
      
      //  Return the file information
      
      DBFileInfo finfo = (DBFileInfo) state.findAttribute(FileState.FileInformation);
      if ( finfo != null)
        return finfo;
    }
    
    //  Get the file information from the database
    
    DBFileInfo finfo = null;
    
    try {
      
      //  Get the file information
    	int userId =  0;
    	UserBean user = dbCtx.getDBInterface().getUserByUsername(userName);
		if(null != user)
		{
			userId = user.getId();
		}
		else
        {
        	log4j.error("User is not exist from database ,userId:"+userId+", username:"+userName);
      	  return null;
	    }
	    finfo = dbCtx.getDBInterface().getFileInformation(dirId, fid, DBInterface.FileAll,userId,shareName,userName);
//	    log4j.debug("DBD#getFileInfo ; dirId:" + dirId+" , fid:"+fid+", userId: "+userId+", shareName :"+shareName);
    }
    catch (DBException ex) {
    	log4j.error(ex);
      finfo = null;
    }

    //  Set the full path for the file
    
    if ( finfo != null)
      finfo.setFullName(path);
      
    //  Update the cached information, if available
    
    if ( state != null && finfo != null) {
      state.addAttribute(FileState.FileInformation, finfo);
      state.setFileStatus( finfo.isDirectory() ? FileStatus.DirectoryExists : FileStatus.FileExists);
    }
      
    //  Return the file information

    return finfo;
  }

  /**
   * Get the details for a file stream
   * 
   * @param parent FileState
   * @param paths String[]
   * @param dbCtx DBDeviceContext
   * @return DBFileInfo
   */
  public DBFileInfo getStreamInfo(FileState parent, String[] paths, DBDeviceContext dbCtx) {

    //  Check if the file is in the cache

    String streamPath = paths[0] + paths[1] + paths[2];   
    FileState state = getFileState(streamPath, dbCtx, true);
    
    if ( state != null && state.getFileId() != -1) {
        
      //  Debug
     log4j.debug("@@ Cache hit - getStreamInfo() path=" + streamPath);
      
      //  Return the file information
      
      DBFileInfo finfo = (DBFileInfo) state.findAttribute(FileState.FileInformation);
      if ( finfo != null)
        return finfo;
    }

    //  DEBUG
    
    log4j.debug("DBD#getStreamInfo parent=" + parent.getPath() + ", stream=" + paths[2]);
      
    //  Get a list of the streams for the parent file
    
    DBFileInfo finfo = null;
    
    try {
      
      //  Get the list of streams

      StreamInfoList sList = (StreamInfoList) parent.findAttribute(DBStreamList);
      
      if ( sList == null) {
        
        //  No cached stream information, get the list from the database

        sList = dbCtx.getDBInterface().getStreamsList(parent.getFileId(), DBInterface.StreamAll);
        
        //  Cache the information
        
        parent.addAttribute(DBStreamList, sList);
      }

      //  Find the required stream information
      
      if ( sList != null) {
        
        //  Find the required stream information
        
        StreamInfo sInfo = sList.findStream(paths[2]);
        
        //  Convert the stream information to file information
        
        if ( sInfo != null) {
          
          //  Load the stream information
          
          finfo = new DBFileInfo();
          finfo.setFileId(parent.getFileId());
        
          //  Copy the stream information
        
          finfo.setFileName(sInfo.getName());
          finfo.setSize(sInfo.getSize());
        
          //  Get the file creation date, or use the current date/time

          if ( sInfo.hasCreationDateTime())
            finfo.setCreationDateTime(sInfo.getCreationDateTime());
        
          //  Get the modification date, or use the current date/time
        
          if ( sInfo.hasModifyDateTime())
            finfo.setModifyDateTime(sInfo.getModifyDateTime());
          else if ( sInfo.hasCreationDateTime())
            finfo.setModifyDateTime(sInfo.getCreationDateTime());
        
          //  Get the last access date, or use the current date/time
        
          if ( sInfo.hasAccessDateTime())
            finfo.setAccessDateTime(sInfo.getAccessDateTime());
          else if ( sInfo.hasCreationDateTime())
            finfo.setAccessDateTime(sInfo.getCreationDateTime());
        }
      }
    }
    catch ( DBException ex) {
      log4j.error(ex);
      finfo = null;
    }

    //  Set the full path for the file
    
    if ( finfo != null)
      finfo.setFullName(streamPath);
      
    //  Update the cached information, if available
    
    if ( state != null && finfo != null) {
      state.addAttribute(FileState.FileInformation, finfo);
      state.setFileStatus( FileStatus.FileExists);
    }
      
    //  Return the file information for the stream

    return finfo;
  }
  
  /**
   * Get the cached file state for the specified path
   * 
   * @param path String
   * @param ctx DBDeviceContext
   * @param create boolean
   * @return FileState
   */
  protected final FileState getFileState(String path, DBDeviceContext ctx, boolean create) {

    //  Access the file state cache
    
    FileStateCache cache = ctx.getStateCache();
    if ( cache == null)
      return null;

    //  Return the required file state
    return cache.findFileState(path, create);
  }

  /**
   * Connection opened to this disk device
   * 
   * @param sess  Server session
   * @param tree  Tree connection
   */
  public void treeOpened(SrvSession sess, TreeConnection tree) {
  }
  
  /**
   * Connection closed to this device
   * 
   * @param sess          Server session
   * @param tree          Tree connection
   */
  public void treeClosed(SrvSession sess, TreeConnection tree) {
  }
  
  /**
   * Check if general debug output is enabled
   * 
   * @return boolean
   */
  protected final boolean hasDebug() {
    return m_debug;
  }
  
  /**
   * Return disk information about a shared filesystem
   * 
   * @param ctx DiskDeviceContext
   * @param info SrvDiskInfo
   * @exception IOException
   */
  public final void getDiskInformation(DiskDeviceContext ctx, SrvDiskInfo info)
    throws IOException {

    //  Check if there is static disk size information available
    
    if ( ctx.hasDiskInformation())
      info.copyFrom(ctx.getDiskInformation());
        
    //  Check that the context is a JDBC context
    
    if ( ctx instanceof DBDeviceContext) {
      
      //  Access the associated file loader class, if it implements the disk size interface then call the loader
      //  to fill in the disk size details
      
      DBDeviceContext dbCtx = (DBDeviceContext) ctx;
      
      if ( dbCtx.getFileLoader() instanceof DiskSizeInterface) {
        
        //  Pass the disk size request to the associated file loader
        
        DiskSizeInterface sizeInterface = (DiskSizeInterface) dbCtx.getFileLoader();
        
        sizeInterface.getDiskInformation(ctx, info);
        
        //  DEBUG
        log4j.debug("DBD#getDiskInformation() handed to file loader");
      }
    }
    
    //  Check if there is a quota manager, if so then get the current free space from the quota manager
    
    if ( ctx.hasQuotaManager()) {
      
      //  Get the free space, in bytes, from the quota manager
      
      long freeSpace = ctx.getQuotaManager().getAvailableFreeSpace();
      
      //  Convert the free space to free units
      
      long freeUnits = freeSpace / info.getUnitSize();
      info.setFreeUnits(freeUnits);
      
      //  DEBUG
      log4j.debug("DBD#getDiskInformation() freeSpace:"+freeSpace+" , freeUnits:"+freeUnits);
    }
  }
  
  /**
   * Determine if NTFS streams support is enabled. Check if the associated file loader
   * supports NTFS streams.
   * 
   * @param sess SrvSession
   * @param tree TreeConnection
   * @return boolean 
   */
  public boolean hasStreamsEnabled(SrvSession sess, TreeConnection tree) {

    //  Check that the context is a JDBC context
    
    if ( tree.getContext() instanceof DBDeviceContext) {
      
      //  Access the associated file loader class to check if it supports NTFS streams
      
      DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();
      if ( dbCtx.hasNTFSStreamsEnabled()) {
        
        //  Check if the file loader supports NTFS streams

        return dbCtx.getFileLoader().supportsStreams();
      }
    }
    
    //  Disable streams
    
    return false;
  }

  /**
   * Get the stream information for the specified file stream
   * 
   * @param sess SrvSession
   * @param tree TreeConnection
   * @param streamInfo StreamInfo
   * @return StreamInfo
   * @exception IOException 
   */
  public StreamInfo getStreamInformation(SrvSession sess, TreeConnection tree, StreamInfo streamInfo)
    throws IOException {

    //  DEBUG
	log4j.debug("DBD#getStreamInformation() called ###");
    
    return null;
  }

  /**
   * Return the list of available streams for the specified file
   *
   * @param sess SrvSession
   * @param tree TreeConnection
   * @param fileName String
   * @return StreamInfoList
   * @exception IOException  
   */
  public StreamInfoList getStreamList(SrvSession sess, TreeConnection tree, String fileName)
    throws IOException {

    //  Access the JDBC context
	  String userName = sess.getClientInformation().getUserName();
	  String shareName = tree.getContext().getShareName();
    DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

    //  Get the file state for the top level file
    
    String parentPath = FileName.getParentPathForStream( fileName);
    FileState fstate = getFileState(parentPath, dbCtx, true);
    
    //  DEBUG  
    log4j.debug("DBD#getStreamList() fileName=" + fileName + ", userName:"+userName);
    
    //  Check if the file state already has the streams list cached
    
    StreamInfoList streamList = (StreamInfoList) fstate.findAttribute(DBStreamList);
    if ( streamList != null && streamList.numberOfStreams() > 0)
        return streamList;
    
    //  Get the main file information and convert to stream information
    
    DBFileInfo finfo = getFileDetails(fileName,dbCtx, fstate,userName,shareName);
    if ( finfo == null)
    {
    	log4j.warn("DBD#getStreamList() getFileDetails  finfo==null; fileName=" + fileName + ", userName:"+userName);
    	return null;
    }
    
    //  Create the stream list
    
    streamList = new StreamInfoList();
    
    // Add an entry for the main file data stream
    
    StreamInfo sinfo = new StreamInfo("::$DATA", finfo.getFileId(), 0, finfo.getSize(), finfo.getAllocationSize());
    streamList.addStream(sinfo);

    //  Get the list of streams
    
    StreamInfoList sList = loadStreamList(fstate, finfo, dbCtx, true);
    if ( sList != null) {
      
      //  Copy the stream information to the main list
      
      for ( int i = 0; i < sList.numberOfStreams(); i++) {
        
        //  Add the stream to the main list

        streamList.addStream(sList.getStreamAt(i));
      }
    }

    //  Cache the stream list
    
    fstate.addAttribute(DBStreamList, streamList);
    
    //  Return the stream list
        
    return streamList;
  }
  
  /**
   * Create a new stream with the specified parent file
   * 
   * @param sess SrvSession
   * @param tree TreeConnection
   * @param params FileOpenParams
   * @param parent FileState
   * @param dbCtx DBDeviceContext
   * @return NetworkFile
   * @exception IOException
   */
  protected final NetworkFile createStream(SrvSession sess, TreeConnection tree, FileOpenParams params, FileState parent, DBDeviceContext dbCtx)
    throws IOException {

    //  Get the file information for the parent file
	  String userName = sess.getClientInformation().getUserName();
	  String shareName = tree.getContext().getShareName();
    DBFileInfo finfo = getFileDetails(params.getPath(), dbCtx, parent,userName,shareName);
    
    if (finfo == null)
    {
    	log4j.warn("DBD#createStream() AccessDeniedException  parent:"+parent);
      throw new AccessDeniedException();
    }

    //  Get the list of streams for the file
    
    StreamInfoList streamList = (StreamInfoList) parent.findAttribute(DBStreamList);
    if ( streamList == null)
      streamList = getStreamList(sess, tree, params.getPath());
    
    if ( streamList == null)
    {
    	log4j.warn("DBD#createStream() AccessDeniedException  parent:"+parent);
      throw new AccessDeniedException();
    }
      
    //  Check if the stream already exists
    
    if ( streamList.findStream(params.getStreamName()) != null)
    {
      log4j.error("Stream exists, " + params.getFullPath());
      throw new FileExistsException("Stream exists, " + params.getFullPath());
    }

    //  Create a new stream record

    DBNetworkFile file = null;
    FileAccessToken accessToken = null;
    FileState fstate = null;
    
    try {

   	  // Check if the file can be opened in the requested mode
      	
      fstate = getFileState(params.getFullPath(), dbCtx, true);
      accessToken = dbCtx.getStateCache().grantFileAccess( params, fstate, FileStatus.FileExists);
      
      //  Create a new stream record
      
      int stid = dbCtx.getDBInterface().createStreamRecord(params.getStreamName(), finfo.getFileId());
      
      //  Create a network file to hold details of the new stream

      file = (DBNetworkFile) dbCtx.getFileLoader().openFile(params, finfo.getFileId(), stid, finfo.getDirectoryId(), true, false,userName);
      file.setFullName(params.getFullPath());
      file.setStreamId(stid);
      file.setStreamName(params.getStreamName());
      file.setDirectoryId(finfo.getDirectoryId());
      file.setAttributes(params.getAttributes());
      
      //  Set the file state for the file
      
      file.setFileState(dbCtx.getStateCache().getFileStateProxy(fstate));
      
      // Store the access token
      
      file.setAccessToken( accessToken);
      
      //  Open the stream file
      
      file.openFile(true);
      
      //  Add an entry to the stream list for the new stream
      
      StreamInfo stream = new StreamInfo(params.getStreamName(), file.getFileId(), stid);
      streamList.addStream(stream);
      
      //  DEBUG  
      log4j.debug("DBD#createStream() file=" + params.getPath() + ", stream=" + params.getStreamName() + ", fid/stid=" + file.getFileId() + "/" + stid);
    }
    catch (DBException ex) {
    	log4j.error("Error: " + ex.toString());
    }
    finally {
    	
        // Check if the stream file is not valid but an access token has been allocated
      	
        if ( file == null && accessToken != null)
          dbCtx.getStateCache().releaseFileAccess(fstate, accessToken);
    }

    //  If the file/stream is not valid throw an exception
    
    if ( file == null)
    {
    	log4j.error("AccessDeniedException ,fullPath:"+params.getFullPath());
      throw new AccessDeniedException(params.getFullPath());
    }
      
    //  Return the network file for the new stream
    return file;
  }
  
  /**
   * Open an existing stream with the specified parent file
   * 
   * @param sess SrvSession
   * @param tree TreeConnection
   * @param params FileOpenParams
   * @param parent FileState
   * @param dbCtx DBDeviceContext
   * @return NetworkFile
   * @exception IOException
   */
  protected final NetworkFile openStream(SrvSession sess, TreeConnection tree, FileOpenParams params, FileState parent, DBDeviceContext dbCtx)
    throws IOException {

    //  Get the file information for the parent file
	  String userName = sess.getClientInformation().getUserName();
	  String shareName = tree.getContext().getShareName();
    DBFileInfo finfo = getFileDetails(params.getPath(),dbCtx,parent,userName,shareName);
  
    if (finfo == null)
    {
    	log4j.warn("DBD#openStream() AccessDeniedException  parent:"+parent);
      throw new AccessDeniedException();
    }

    //  Get the list of streams for the file
  
    StreamInfoList streamList = getStreamList(sess, tree, params.getPath());
    if ( streamList == null)
    {
    	log4j.warn("DBD#openStream() AccessDeniedException  parent:"+parent);
      throw new AccessDeniedException();
    }
    
    //  Check if the stream exists

    StreamInfo sInfo = streamList.findStream(params.getStreamName());
    if ( sInfo == null)
    {
    	log4j.error("FileNotFoundException Stream does not exist, " + params.getFullPath());
      throw new FileNotFoundException("Stream does not exist, " + params.getFullPath());
    }

    // Open the stream
    
    DBNetworkFile jdbcFile = null;
    FileAccessToken accessToken = null;
    FileState fstate = null;
    log4j.debug("DBD#openStream() file=" + params.getPath() + ", stream=" + sInfo.getName());
    try {
    	
	    //  Get, or create, a file state for the stream
	  
	    fstate = getFileState(params.getFullPath(), dbCtx, true);

	    // Check if the file stream can be opened in the requested mode
    	
    	accessToken = dbCtx.getStateCache().grantFileAccess( params, fstate, FileStatus.FileExists);
	              
	    //  Check if the file shared access indicates exclusive file access
	  
	    if ( params.getSharedAccess() == SharingMode.NOSHARING && fstate.getOpenCount() > 0 &&
	    		params.getProcessId() != fstate.getProcessId())
	      throw new FileSharingException("File already open, " + params.getPath());
	
	    //  Set the file information for the stream, using the stream information
	    
	    DBFileInfo sfinfo = new DBFileInfo(sInfo.getName(), params.getFullPath(), finfo.getFileId(), finfo.getDirectoryId());
	    
	    sfinfo.setFileSize(sInfo.getSize());
	    sfinfo.setFileAttributes( FileAttribute.Normal);
	    
	    sfinfo.setCreationDateTime( sInfo.getCreationDateTime());
	    sfinfo.setModifyDateTime( sInfo.getModifyDateTime());
	    sfinfo.setAccessDateTime( sInfo.getAccessDateTime());
	
	    fstate.addAttribute(FileState.FileInformation, sfinfo);
	    
	    //  Create a JDBC network file and open the stream
	    log4j.debug("Create a JDBC network file and open the stream");
	    
	    jdbcFile = (DBNetworkFile) dbCtx.getFileLoader().openFile(params, finfo.getFileId(), sInfo.getStreamId(),
	    															finfo.getDirectoryId(), false, false,userName);
	
	    jdbcFile.setFileState(dbCtx.getStateCache().getFileStateProxy(fstate));
	    jdbcFile.setFileDetails( sfinfo);
	    
	    //  Open the stream file, if not a directory
	  
	    jdbcFile.openFile(false);
    }
    finally {
    	
        // Check if the stream file is not valid but an access token has been allocated
      	
        if ( jdbcFile == null && accessToken != null)
          dbCtx.getStateCache().releaseFileAccess(fstate, accessToken);
      }
    
    //  Return the network file
      
    return jdbcFile;
  }
  
  /**
   * Close an NTFS stream
   *
   * @param sess  Session details
   * @param tree  Tree connection
   * @param stream  Network file details
   * @exception IOException
   */
  protected final void closeStream(SrvSession sess, TreeConnection tree, NetworkFile stream)
    throws IOException {

    //  Debug
	  log4j.debug("DBD#closeStream() file=" + stream.getFullName() + ", stream=" + stream.getStreamName() +
                         ", fid/stid=" + stream.getFileId() + "/" + stream.getStreamId());
    
    //  Access the JDBC context

    DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

    //  Close the file

    dbCtx.getFileLoader().closeFile(sess, stream);

    //  Access the JDBC file
    
    DBNetworkFile jdbcFile = null;
    
    if ( stream instanceof DBNetworkFile) {
      
      //  Access the JDBC file
      
      jdbcFile = (DBNetworkFile) stream;

      //  Decrement the open file count
      
      FileState fstate = jdbcFile.getFileState();

      //  Check if the file state is valid, if not then check the main file state cache

      if ( fstate == null) {
        
        //  Check the main file state cache
              
        fstate = getFileState(stream.getFullName(), dbCtx, false);
      }
      
      // Release the file access token for the stream
      
      if ( jdbcFile.hasAccessToken()) {

    	// Release the access token, update the open file count
    	  
    	dbCtx.getStateCache().releaseFileAccess(fstate, jdbcFile.getAccessToken());
      }

      //  Check if we have a valid file state
            
      if ( fstate != null) {
        
        //  Update the cached file size
        
        FileInfo finfo = getFileInformation( sess, tree, fstate.getPath());
        log4j.debug("DBDiskDriver.closeStream ; path:" + fstate.getPath()+" , fstate:"+fstate);
        if ( finfo != null && stream.getWriteCount() > 0) {
          
          //  Update the file size
          
          finfo.setSize(jdbcFile.getFileSize());
          
          //  Update the modified date/time
          
          finfo.setModifyDateTime(jdbcFile.getModifyDate());

          //  DEBUG
          log4j.debug("  Stream size=" + jdbcFile.getFileSize() + ", modifyDate=" + jdbcFile.getModifyDate());
        }
      }
    }
    else
    	log4j.debug("closeFile() Not DBNetworkFile file=" + stream);
      
    //  Check if the stream was opened for write access, if so then update the stream size
    
    if ( stream.getGrantedAccess() != NetworkFile.READONLY && stream.isDirectory() == false &&
         stream.getWriteCount() > 0) {
      
      //  DEBUG
    	log4j.debug("  Update stream size=" + stream.getFileSize());
        
      //  Get the current date/time
      
      long modifiedTime = 0L;
      if ( stream.hasModifyDate())
        modifiedTime = stream.getModifyDate();
      else
        modifiedTime = System.currentTimeMillis();

      //  Check if the modified time is earlier than the file creation date/time
      
      if ( stream.hasCreationDate() && modifiedTime < stream.getCreationDate()) {
        
        //  Use the creation date/time for the modified date/time
        
        modifiedTime = stream.getCreationDate();
        
        //  DEBUG
        log4j.debug("Close stream using creation date/time for modified date/time");
      }
      
      //  Update the in-memory stream information
      
      String parentPath = FileName.getParentPathForStream( stream.getFullName());
      FileState parent = getFileState( parentPath, dbCtx, false);
      StreamInfo sInfo = null;
      int sattr = 0;
      
      if ( parent != null) {
        
        //  Check if the stream list has been loaded
        
        StreamInfoList streamList = getStreamList(sess, tree, parentPath);
        if ( streamList != null) {
          
          //  Find the stream information
          
          sInfo = streamList.findStream(stream.getStreamName());
          if ( sInfo != null) {
            
            //  Update the stream size
            
            sInfo.setSize(stream.getFileSize());
            sattr += StreamInfo.SetStreamSize;
            
            //  DEBUG
            log4j.debug("Updated stream file size");
          }
          else
        	  log4j.error("** Failed to find details for stream " + stream.getStreamName());
        }
        else
        	log4j.error("** Failed to get streams list for " + parentPath);
      }

      //  Update the file details for the file stream in the database
      
      try {

        //  Check if the file strea, details are valid
        
        if ( sInfo == null) {
          
          //  Create the stream information
          
          sInfo = new StreamInfo();
          
          sInfo.setSize(stream.getFileSize());
          sInfo.setStreamId(stream.getStreamId());
          
          sattr += StreamInfo.SetStreamSize;
        }
        
        //  Set the modify date/time for the stream
        
        sInfo.setModifyDateTime(System.currentTimeMillis());
        sattr += StreamInfo.SetModifyDate;
        
        // Set the stream information values to be updated
        
        sInfo.setStreamInformationFlags( sattr);

        //  Update the stream details
        
        dbCtx.getDBInterface().setStreamInformation(stream.getDirectoryId(), stream.getFileId(), stream.getStreamId(), sInfo);
      }
      catch (DBException ex) {
    	  log4j.error(ex);
      }
    }
  }
  
  /**
   * Delete a stream within a file
   * 
   * @param sess SrvSession
   * @param tree TreeConnection
   * @param name String
   * @exception IOException
   */
  protected final void deleteStream(SrvSession sess, TreeConnection tree, String name)
    throws IOException {

    //  Access the JDBC context
	  String userName = sess.getClientInformation().getUserName();
	  String shareName = tree.getContext().getShareName();
    DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();
    log4j.debug("DBD#deleteStream ; name:" +name+" , shareName:"+shareName+", userName:"+userName);
    //  Get, or create, the file state for main file path and stream
      
    String filePath = FileName.getParentPathForStream( name);
    FileState fstate = getFileState(filePath, dbCtx, true);
    FileState sstate = getFileState(name, dbCtx, false);

    //  Check if the file is within an active retention period
    
    if ( fstate.hasActiveRetentionPeriod())
    {
    	log4j.error("AccessDeniedException File retention active");
      throw new AccessDeniedException("File retention active");
    }


    //  Get the top level file information
    
    DBFileInfo finfo = getFileDetails(filePath,dbCtx, fstate,userName,shareName);
    
    //  Get the stream list for the top level file
    
    StreamInfoList streamList = (StreamInfoList) fstate.findAttribute(DBStreamList);
    if ( streamList == null)
      streamList = getStreamList(sess, tree, filePath);
    
    if ( streamList == null)
    {
    	log4j.error("FileNotFoundException Stream not found, " + name);
      throw new FileNotFoundException("Stream not found, " + name);
    }
    
    //  Parse the path string to get the directory, file name and stream name
    
    String[] paths = FileName.splitPathStream(name);

    //  Find the required stream details
    
    StreamInfo sInfo = streamList.findStream(paths[2]);
    if ( sInfo == null)
    {
    	log4j.error("FileNotFoundException Stream not found, " + name);
      throw new FileNotFoundException("Stream not found, " + name);
    }

    //  Delete the stream record from the database
    
    try {

      //  Call the file loader to delete the stream data
      
      dbCtx.getFileLoader().deleteFile(name, sInfo.getFileId(), sInfo.getStreamId(),shareName);
      
      //  Delete the stream record
      
      dbCtx.getDBInterface().deleteStreamRecord(sInfo.getFileId(), sInfo.getStreamId(), dbCtx.isTrashCanEnabled());
      
      //  Remove the stream information from the in memory list
        
      streamList.removeStream(sInfo.getName());
        
      //  Set the streams file state to indicate that it does not exist
        
      if ( sstate != null)
        sstate.setFileStatus( FileStatus.NotExist);
    }
    catch (DBException ex) {
    	log4j.error("Error: " + ex.toString());
    }
  }
  
  /**
   * Load the stream list for the specified file
   * 
   * @param fstate FileState
   * @param finfo DBFileInfo
   * @param dbCtx DBDeviceContext
   * @param dbLoad boolean
   * @return StreamInfoList
   */
  protected final StreamInfoList loadStreamList(FileState fstate, DBFileInfo finfo, DBDeviceContext dbCtx, boolean dbLoad) {

    //  Check if the stream list has already been loaded
    
    StreamInfoList sList = (StreamInfoList) fstate.findAttribute(FileState.StreamsList);
    
    //  If the streams list is not loaded then load it from the database

    if ( sList == null && dbLoad == true) {   

      //  Load the streams list from the database
      
      try {

        //  Load the streams list
        
        sList = dbCtx.getDBInterface().getStreamsList(finfo.getFileId(), DBInterface.StreamAll);
        
        // Cache the streams list via the parent file state
        
        if ( sList != null)
        	fstate.addAttribute( DBStreamList, sList);
      }
      catch (DBException ex) {
    	  log4j.error(ex);
      }
    }
        
    //  Return the streams list
    
    return sList;
  }

  /**
   *  Rename a stream
   *
   * @param sess SrvSession 
   * @param tree TreeConnection
   * @param oldName String
   * @param newName String
   * @param overWrite boolean
   * @exception IOException
   */
  public void renameStream(SrvSession sess, TreeConnection tree, String oldName, String newName, boolean overWrite)
    throws IOException {
  }

  /**
   * Return the volume information
   * 
   * @param ctx DiskDeviceContext
   * @return VolumeInfo 
   */
  public VolumeInfo getVolumeInformation(DiskDeviceContext ctx) {
    
    //  Check if the context has volume information
    
    VolumeInfo volInfo = ctx.getVolumeInformation();
    
    if ( volInfo == null) {
      
      //  Create volume information for the filesystem
      
      volInfo = new VolumeInfo(ctx.getDeviceName());
      
      //  Add to the device context
      
      ctx.setVolumeInformation(volInfo);
    }

    //  Check if the serial number is valid
    
    if ( volInfo.getSerialNumber() == 0) {
      
      //  Generate a random serial number
      
      volInfo.setSerialNumber(new java.util.Random().nextInt());      
    }
    
    //  Check if the creation date is valid
    
    if ( volInfo.hasCreationDateTime() == false) {
      
      //  Set the creation date to now
      
      volInfo.setCreationDateTime(new java.util.Date());
    }
    
    //  Return the volume information
    
    return volInfo;
  }
  
  /**
   * Return the lock manager implementation
   * 
   * @param sess SrvSession
   * @param tree TreeConnection
   * @return LockManager 
   */
  public LockManager getLockManager(SrvSession sess, TreeConnection tree) {
    
    // Access the context
    DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();
    return dbCtx.getFileStateLockManager();
  }
  
  /**
   * Return the oplock manager implementation
   * 
   * @param sess SrvSession
   * @param tree TreeConnection
   * @return OpLockManager 
   */
  public OpLockManager getOpLockManager(SrvSession sess, TreeConnection tree) {
    
    // Access the context
	    
    DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();
    return dbCtx.getFileStateLockManager();
  }
  
	/**
	 * Enable/disable oplock support
	 * 
	 * @param sess SrvSession
	 * @param tree TreeConnection
	 * @return boolean
	 */
	public boolean isOpLocksEnabled(SrvSession sess, TreeConnection tree) {
	    
	    // Access the context
		    
	    DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();
	    return dbCtx.isOpLocksEnabled();
	}
	
  /**
   * Convert a file id to a share relative path
   *
   * @param sess SrvSession
   * @param tree TreeConnection
   * @param dirid int
   * @param fileid
   * @return String
   * @exception FileNotFoundException 
   */
  public String buildPathForFileId(SrvSession sess, TreeConnection tree, int dirid, int fileid)
    throws FileNotFoundException {

    // Access the JDBC context
    String userName = sess.getClientInformation().getUserName();
    String shareName = tree.getContext().getShareName();
    DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();
    //  Build an array of folder names working back from the files id
    
    ArrayList<String> names = new ArrayList<String>(16);
    log4j.debug("DBD#buildPathForFileId ; dirid:" +dirid+" , fileid:"+fileid+" , shareName:"+shareName+", userName:"+userName);
    try {

    	int userId =  0;
    	UserBean user = dbCtx.getDBInterface().getUserByUsername(userName);
		if(null != user)
		{
			userId = user.getId();
		}
		else
        {
        	log4j.error("User is not exist from database ,userId:"+userId+", username:"+userName);
      	  	return null;
	    }
        
      //  Loop, walking backwards up the tree until we hit root
      
      int curFid = fileid;
      int curDid = dirid;
      
      FileInfo finfo = null;
      
      do {
            
        //  Search for the current file in the database
    	log4j.debug("do: curDid:" +curDid+" , curFid:"+curFid);
        finfo = dbCtx.getDBInterface().getFileInformation(curDid, curFid, DBInterface.FileIds, userId,shareName,userName);

        if ( finfo != null) {
          //  Get the filename
          
          names.add(finfo.getFileName());
          
          //  The directory id becomes the next file id to search for
          
          curFid = finfo.getDirectoryId();
          curDid = -1;
        }
        else
        {
        	log4j.warn("FileNotFoundException ; curFid:"+curFid);
        	throw new FileNotFoundException("" + curFid);
        }
      
      } while ( curFid > 0);
    }
    catch ( DBException ex) {
    	log4j.error(ex);
    	return null;
    }

    //  Build the path string

    StringBuffer pathStr = new StringBuffer (256);
    pathStr.append(FileName.DOS_SEPERATOR_STR);
    
    for ( int i = names.size() - 1; i >= 0; i--) {
      pathStr.append(names.get(i));
      pathStr.append(FileName.DOS_SEPERATOR_STR);
    }
    
    //  Remove the trailing slash from the path
    
    if ( pathStr.length() > 0)
      pathStr.setLength(pathStr.length() - 1);
    
    //  Return the path string
    
    return pathStr.toString();
  }
  
  /**
   * Determine if symbolic links are enabled
   * 
   * @param sess SrvSession
   * @param tree TreeConnection
   * @return boolean
   */
  public boolean hasSymbolicLinksEnabled(SrvSession sess, TreeConnection tree) {

    //  Access the associated database interface to check if it supports symbolic links
      
    DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();
    if ( dbCtx.getDBInterface().supportsFeature( DBInterface.FeatureSymLinks)) {
        
      //  Database interface supports symbolic links

      return true;
    }
    
    //  Symbolic links not supported
    
    return false;
  }
  
  /**
   * Read the link data for a symbolic link
   * 
   * @param sess SrvSession
   * @param tree TreeConnection
   * @param path String
   * @return String
   * @exception AccessDeniedException
   * @exception FileNotFoundException 
   */
  public String readSymbolicLink( SrvSession sess, TreeConnection tree, String path)
    throws AccessDeniedException, FileNotFoundException {
    
    //  Access the associated database interface to check if it supports symbolic links
    String userName = sess.getClientInformation().getUserName();
    String shareName = tree.getContext().getShareName();
    DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();
    DBInterface dbInterface = dbCtx.getDBInterface();
    String symLink = null;
    
    if ( dbInterface.supportsFeature( DBInterface.FeatureSymLinks)) {
        
      //  Get, or create, the file state for the existing file
      
      FileState fstate = getFileState( path, dbCtx, true);
      
      //  Get the file id of the existing file
      
      int fid = fstate.getFileId();
      int dirId = -1;
      
      if ( fid == -1) {
        //  Split the current path string and find the file id of the existing file/directory
        
        dirId = findParentDirectoryId( dbCtx, path, true,userName,shareName);
        if ( dirId == -1)
        {
        	log4j.warn("DBD#readSymbolicLink() FileNotFoundException  path:"+path);
          throw new FileNotFoundException( path);
        }
    
        //  Get the file/directory name
  
        String[] oldPaths = FileName.splitPath( path);
        String fname = oldPaths[1];
        
        int userId = 0;
        UserBean user;
		try {
			user = dbInterface.getUserByUsername(userName);
		} catch (DBException e) {
			log4j.error(e);
		}
		if(userId<1)
        {
        	log4j.error("User is not exist from database ,userId:"+userId+", username:"+userName);
      	  return null;
	    }
        
        //  Get the file id
        
        fid = getFileId( path, fname, dirId, dbCtx,userId,shareName,userName);
        if ( fid == -1)
        {
        	log4j.error("FileNotFoundException "+path);
          throw new FileNotFoundException( path);
        }
          
        //  Update the file state
        
        fstate.setFileId(fid);
      }
      
      try {
        
        //  Database interface supports symbolic links, read the symbolic link
        
        symLink = dbInterface.readSymbolicLink( dirId, fid);
      }
      catch ( DBException ex) {
    	  log4j.warn("DBD#readSymbolicLink() FileNotFoundException  path:"+path,ex);
        throw new FileNotFoundException ( path);
      }
    }
    
    //  Return the symbolic link data
    
    return symLink;
  }
  
  /**
   * Return the security descriptor length for the specified file
   * 
   * @param sess      Server session
   * @param tree      Tree connection
   * @param netFile   Network file
   * @return int
   * @exception SMBSrvException
   */
  public int getSecurityDescriptorLength(SrvSession sess, TreeConnection tree, NetworkFile netFile)
    throws SMBSrvException {
	  
	  return 0;
  }

  /**
   * Load a security descriptor for the specified file
   * 
   * @param sess      Server session
   * @param tree      Tree connection
   * @param netFile   Network file
   * @return SecurityDescriptor
   * @exception SMBSrvException
   */
  public SecurityDescriptor loadSecurityDescriptor(SrvSession sess, TreeConnection tree, NetworkFile netFile)
    throws SMBSrvException {
	  
	  return null;
  }

  /**
   * Save the security descriptor for the specified file
   * 
   * @param sess      Server session
   * @param tree      Tree connection
   * @param netFile   Network file
   * @param secDesc   Security descriptor
   * @exception SMBSrvException
   */
  public void saveSecurityDescriptor(SrvSession sess, TreeConnection tree, NetworkFile netFile, SecurityDescriptor secDesc)
    throws SMBSrvException {
	  
  }

public int OfffileExists(SrvSession sess, TreeConnection tree, String name,int createOptn) {
	   //  Access the JDBC context
	  String userName = sess.getClientInformation().getUserName();
	  String shareName = tree.getContext().getShareName();
  DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();
  //debug
//  log4j.debug("DBD#fileExists name=" + name);
  //  Check if the path contains an NTFS stream name

  int fileSts = FileStatus.NotExist;
  FileState fstate = null;
  
//Check for the root directory    
  if ( name.length() == 0 || name.compareTo("\\") == 0) {
//  	 log4j.debug("DBD#fileExists 根目录 , 直接返回 DirectoryExists ");
  	 return FileStatus.DirectoryExists;
  }
  if (name.equalsIgnoreCase("\\"+userName+DBUtil.SPECIAL_CHAR)) {
// 	 log4j.debug("DBD#fileExists username@用户目录 , 直接返回 DirectoryExists ");
  	return FileStatus.DirectoryExists;
  }
  if(name.toLowerCase().startsWith("\\"+userName.toLowerCase()+DBUtil.SPECIAL_CHAR)==false)
  {
  	log4j.warn("DBD#fileExists 不符合规则的name参数 , 直接返回 NotExist ; name:"+name+",userName:"+userName);
  	return FileStatus.NotExist;
  }
      
  if ( FileName.containsStreamName(name)) {
    
  	// Get the file information for the stream    	
  	FileInfo fInfo = null;
  	try {
  		fInfo = getFileInformation( sess, tree, name);
  	}
  	catch ( IOException ex) {
  		log4j.error(ex );
  	}

  	// Check if the file information was retrieved for the stream    	
  	if ( fInfo != null)
  		fileSts = FileStatus.FileExists;
  	
      //  Debug
  	log4j.debug("DBD#fileExists() nameWithStream=" + name + ", fileSts=" + FileStatus.asString(fileSts));
  }
  else {

    //  Get, or create, the file state for the path
    
    fstate = getFileState( name, dbCtx, true);

    //  Check if the file exists status has been cached
    
    fileSts = fstate.getFileStatus();
    
//      if ( fstate.getFileStatus() == FileStatus.Unknown) {
    String ext = DiskUtil.getExt(name);
	 if ( fstate.getFileStatus() == FileStatus.Unknown || (fstate.getFileStatus()==FileStatus.NotExist &&DBUtil.SUPPORT_EXT.contains(ext))) {
		 //fstate.getFileStatus()==FileStatus.NotExist &&DBUtil.SUPPORT_EXT.contains(ext) ā
      
      //  Get the file details
      
      DBFileInfo dbInfo = getFileDetails(name,dbCtx,fstate,userName,shareName);

      if ( dbInfo != null) {
        if ( dbInfo.isDirectory() == true)
          fileSts = FileStatus.DirectoryExists;
        else
          fileSts = FileStatus.FileExists;
        
        // Save the file id
        
        if ( dbInfo.getFileId() != -1)
      	  fstate.setFileId( dbInfo.getFileId());
      }
      else {
    
        //  Indicate that the file does not exist
        
        fstate.setFileStatus( FileStatus.NotExist);
        fileSts = FileStatus.NotExist;
      }
      
      //  Debug
//      log4j.debug("DBD#fileExists() name=" + name + ", fileSts=" + FileStatus.asString(fileSts));
    }
    else {
      
      //  DEBUG
//  	  log4j.debug("@@ Cache hit - fileExists() name=" + name + ", fileSts=" + FileStatus.asString(fileSts));
    }
  }
  
  //  Return the file exists status
  
  return fileSts;
}
}
