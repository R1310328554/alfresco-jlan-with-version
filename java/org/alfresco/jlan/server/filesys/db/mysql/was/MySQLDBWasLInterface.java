
package org.alfresco.jlan.server.filesys.db.mysql.was;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

import org.alfresco.jlan.server.config.InvalidConfigurationException;
import org.alfresco.jlan.server.filesys.AccessDeniedException;
import org.alfresco.jlan.server.filesys.DiskFullException;
import org.alfresco.jlan.server.filesys.DiskOfflineException;
import org.alfresco.jlan.server.filesys.FileAttribute;
import org.alfresco.jlan.server.filesys.FileExistsException;
import org.alfresco.jlan.server.filesys.FileInfo;
import org.alfresco.jlan.server.filesys.FileName;
import org.alfresco.jlan.server.filesys.FileOpenParams;
import org.alfresco.jlan.server.filesys.FileStatus;
import org.alfresco.jlan.server.filesys.FileType;
import org.alfresco.jlan.server.filesys.cache.FileState;
import org.alfresco.jlan.server.filesys.db.DBDataDetails;
import org.alfresco.jlan.server.filesys.db.DBDataDetailsList;
import org.alfresco.jlan.server.filesys.db.DBDataInterface;
import org.alfresco.jlan.server.filesys.db.DBDeviceContext;
import org.alfresco.jlan.server.filesys.db.DBException;
import org.alfresco.jlan.server.filesys.db.DBFileInfo;
import org.alfresco.jlan.server.filesys.db.DBFileLoader;
import org.alfresco.jlan.server.filesys.db.DBInterface;
import org.alfresco.jlan.server.filesys.db.DBObjectIdInterface;
import org.alfresco.jlan.server.filesys.db.DBQueueInterface;
import org.alfresco.jlan.server.filesys.db.DBSearchContext;
import org.alfresco.jlan.server.filesys.db.JdbcDBInterface;
import org.alfresco.jlan.server.filesys.db.JdbcDBWasLInterface;
import org.alfresco.jlan.server.filesys.db.ObjectIdFileLoader;
import org.alfresco.jlan.server.filesys.db.RetentionDetails;
import org.alfresco.jlan.server.filesys.db.mysql.MySQLSearchContextShare;
import org.alfresco.jlan.server.filesys.db.mysql.MySQLSearchContextU;
import org.alfresco.jlan.server.filesys.loader.CachedFileInfo;
import org.alfresco.jlan.server.filesys.loader.FileRequest;
import org.alfresco.jlan.server.filesys.loader.FileRequestQueue;
import org.alfresco.jlan.server.filesys.loader.FileSegment;
import org.alfresco.jlan.server.filesys.loader.FileSegmentInfo;
import org.alfresco.jlan.server.filesys.loader.MultipleFileRequest;
import org.alfresco.jlan.server.filesys.loader.SingleFileRequest;
import org.alfresco.jlan.smb.server.ntfs.StreamInfo;
import org.alfresco.jlan.smb.server.ntfs.StreamInfoList;
import org.alfresco.jlan.util.MemorySize;
import org.alfresco.jlan.util.StringList;
import org.alfresco.jlan.util.WildCard;
import org.alfresco.jlan.util.db.DBConnectionPool;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.tika.exception.TikaException;
import org.springframework.extensions.config.ConfigElement;

import com.base.config.UserBean;
import com.util.AESUtil;
import com.util.CacheManagerUtil;
import com.util.DBUtil;
import com.util.DiskUtil;
import com.util.FileParser;
import com.util.MD5Util;
import com.util.StringUtil;

/**
 * mySQL Database Interface Class 我的文件接口
 * 
 * <p>
 * mySQL specific implementation of the database interface used by the database
 * filesystem driver (DBDiskDriver).
 * 
 * @author gkspencer
 */
public class MySQLDBWasLInterface extends JdbcDBWasLInterface implements
		DBQueueInterface, DBDataInterface, DBObjectIdInterface {
	// Memory buffer maximum size

	public final static long MaxMemoryBuffer = MemorySize.MEGABYTE / 2; // 1/2Mb


	public final static String TABLE_WAS_TEMPFILE			= "jweb_was_tempfile";// 云编辑临时文件
	
	public final static String FileSysTable_MYFILE			= "jweb_filecache";
	public final static String FileSysTable_COMMFILE			= "jweb_commonfilecache";
	public final static String FileSysTable_SHAREFILE			= "jweb_sharefilecache";
	
	public final static String StreamsTable_MYFILE	    	= "jweb_JLAN_WasUser_Streams";
	public final static String StreamsTable_COMMFILE	    	= "jweb_JLAN_WasCommon_Streams";//三种类型都用一个表
	public final static String StreamsTable_SHAREFILE	    	= "jweb_JLAN_WasShare_Streams";
	
	public final static String FileRevisionTable_MYFILE	= "jweb_filerevision";
	public final static String FileRevisionTable_COMMFILE	= "jweb_commonfilerevision";
	public final static String FileRevisionTable_SHAREFILE	= "jweb_sharefilerevision";
	
//	public final static String FileSysTable_COMMFILE			= "jweb_commonfilecache";
	
	public final static String RetentionTable_COMMFILE		= "jweb_JLAN_WasCommon_Retain";
	public final static String QueueTable_COMMFILE			= "jweb_JLAN_WasCommon_Queue";//三种类型都用一个表
	public final static String TransactQueueTable_COMMFILE	= "jweb_JLAN_WasCommon_TransQueue";//三种类型都用一个表
	public final static String DataTable_COMMFILE			= "jweb_JLAN_WasCommon_Data";//三种类型都用一个表
	
	
//	public final static String FileSysTable_SHAREFILE			= "jweb_sharefilecache";
	
	

//	public final static String RetentionTable_MYFILE		= "jweb_JLANRetain";
//	public final static String QueueTable_MYFILE			= "jweb_JLANQueue";
//	public final static String TransactQueueTable_MYFILE	= "jweb_JLANTransQueue";
//	public final static String DataTable_MYFILE			= "jweb_JLANData";
//	public final static String JarDataTable_MY			= "jweb_JLANJarData";
//	public final static String ObjectIdTable_MY		= "jweb_JLANObjectIds";
//	public final static String SymLinkTable_MY       	= "jweb_JLANSymLinks";
	

	// Lock file name, used to check if server shutdown was clean or not

	public final static String LockFileName = "MySQLLoader.lock";
	public static final String ROLE_VALUE_FOR_ADMIN = "brltwpdgucem";
	SimpleDateFormat sf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	// MySQL error codes
	private Logger log4j = Logger.getLogger(this.getClass());
	private static final int ErrorDuplicateEntry = 1062;
//	private static final String wpsRenameReg = "\\.([a-zA-Z0-9]+)~([a-zA-Z0-9]+)\\.TMP$";// Office临时重命名文件命名归则2

	// Database connection and prepared statement used to write file requests to
	// the queue tables

	private Connection m_dbConn;
	private PreparedStatement m_reqStmt;
	private PreparedStatement m_tranStmt;

	public final static int StsSuccess = 0;
	public final static int StsRequeue = 1;
	public final static int StsError = 2;

	public static final String USERSPACE_MAXSIZE = "userspace.maxSize";// 用户空间大小
	public static final int delFileKeepTime = 10000;// 10000  ms=10s,删除的文件临时保留时长，用于部分软件保存文件时，是先删除原文件，再上传新文件的情况)
	public static boolean initData = false;
	
//	private static final String searchShares_sql = " SELECT -1 as id,0 as pid,name,0 as size,null AS add_time,0 as lastModified,isFile,isHidden,0 as userId,'rw' as permissions FROM  jweb_shares  WHERE status >=0 ";
	
	private static final String searchShares_sql = "SELECT -1 as id,0 as pid,'commonfile' as fname,0 as size,null AS create_date,0 as last_modified,0 as isFile,0 as user_id from dual"
			+" UNION SELECT -1 as id,0 as pid,'userfile' as fname,0 as size,null AS create_date,0 as last_modified,0 as isFile,0 as user_id from dual"
			+" UNION SELECT -1 as id,0 as pid,'sharefile' as fname,0 as size,null AS create_date,0 as last_modified,0 as isFile,0 as user_id from dual";

	/**
	 * Default constructor
	 */
	public MySQLDBWasLInterface() {
		super();
	}

	/**
	 * Return the database interface name
	 * 
	 * @return String
	 */
	public String getDBInterfaceName() {
		return "mySQL";
	}

	/**
	 * Get the supported database features mask
	 * 
	 * @return int
	 */
	protected int getSupportedFeatures() {

		// Determine the available database interface features

		return FeatureNTFS + FeatureRetention + FeatureSymLinks + FeatureQueue
				+ FeatureData + FeatureJarData + FeatureObjectId;
	}

	/**
	 * Initialize the database interface
	 * 
	 * @param dbCtx
	 *            DBDeviceContext
	 * @param params
	 *            ConfigElement
	 * @exception InvalidConfigurationException
	 */
	public void initializeDatabase(DBDeviceContext dbCtx, ConfigElement params)
			throws InvalidConfigurationException {

		// Set the JDBC driver class, must be set before the connection pool is
		// created

		if(initData==false)//未初始化过
		{
			setDriverName("com.mysql.jdbc.Driver");
	
			// Call the base class to do the main initialization
	
			super.initializeDatabase(dbCtx, params);
	
			// Force the autoReconnect to be enabled
	
			if (getDSNString().indexOf("?autoReconnect=") == -1
					&& params.getChild("noAutoReconnect") == null)
				setDSNString(getDSNString() + "?autoReconnect=true");
	
			// Create the database connection pool
	
			try {
				createConnectionPool();
			} catch (Exception ex) {
	
				// DEBUG
				log4j.error("[mySQL] Error creating connection pool, "
						+ ex.toString());
	
				// Rethrow the exception
	
				throw new InvalidConfigurationException(
						"Failed to create connection pool, " + ex.getMessage());
			}
			// Check if the file system table exists

		Connection conn = null;

		try {

			// Open a connection to the database

			conn = getConnection();

			DatabaseMetaData dbMeta = conn.getMetaData();
			ResultSet rs = dbMeta.getTables("", "", "", null);

//			boolean foundStruct = false;
			boolean foundStream_myfile = false;
			boolean foundRetain_myfile = false;
			boolean foundQueue_myfile = false;
			boolean foundTrans_myfile = false;
			boolean foundData_myfile = false;
			
			boolean foundJarData = false;
			boolean foundObjId = false;
			boolean foundSymLink = false;
			boolean foundCommonRevisionValue = false;
			
			boolean foundStream_commfile = false;
			boolean foundRetain_commfile = false;
			boolean foundQueue_commfile = false;
			boolean foundTrans_commfile = false;
			boolean foundData_commfile = false;
			
			boolean foundStream_sharefile = false;
			boolean foundRetain_sharefile = false;
			boolean foundQueue_sharefile = false;
			boolean foundTrans_sharefile = false;
			boolean foundData_sharefile = false;

			while (rs.next()) {

				// Get the table name

				String tblName = rs.getString("TABLE_NAME");

				// Check if we found the filesystem structure or streams table

				/*if (tblName.equalsIgnoreCase(FileSysTableName))
					foundStruct = true;
				else if (hasStreamsTableName()
						&& tblName.equalsIgnoreCase(getStreamsTableName()))
					foundStream = true;
				else if (hasRetentionTableName()
						&& tblName.equalsIgnoreCase(getRetentionTableName()))
					foundRetain = true;
				else if (hasDataTableName()
						&& tblName.equalsIgnoreCase(getDataTableName()))
					foundData = true;
				else if (hasJarDataTableName()
						&& tblName.equalsIgnoreCase(getJarDataTableName()))
					foundJarData = true;
				else if (hasQueueTableName()
						&& tblName.equalsIgnoreCase(getQueueTableName()))
					foundQueue = true;
				else if (hasTransactionTableName()
						&& tblName.equalsIgnoreCase(getTransactionTableName()))
					foundTrans = true;
				else if (hasObjectIdTableName()
						&& tblName.equalsIgnoreCase(getObjectIdTableName()))
					foundObjId = true;
				else if (hasSymLinksTableName()
						&& tblName.equalsIgnoreCase(getSymLinksTableName()))
					foundSymLink = true;*/
				if (tblName.equalsIgnoreCase(StreamsTable_MYFILE))
					foundStream_myfile = true;
				else if (tblName.equalsIgnoreCase(StreamsTable_COMMFILE))
					foundStream_commfile = true;
				else if (tblName.equalsIgnoreCase(StreamsTable_SHAREFILE))
					foundStream_sharefile = true;
				
//				else if (tblName.equalsIgnoreCase(RetentionTable_MYFILE))
//					foundRetain_myfile = true;				
				else if (tblName.equalsIgnoreCase(RetentionTable_COMMFILE))
					foundRetain_commfile = true;
//				else if (tblName.equalsIgnoreCase(RetentionTable_SHAREFILE))
//					foundRetain_sharefile = true;
				
//				else if (tblName.equalsIgnoreCase(QueueTable_MYFILE))
//					foundQueue_myfile = true;				
				else if (tblName.equalsIgnoreCase(QueueTable_COMMFILE))
					foundQueue_commfile = true;
//				else if (tblName.equalsIgnoreCase(QueueTable_SHAREFILE))
//					foundQueue_sharefile = true;
				
//				else if (tblName.equalsIgnoreCase(TransactQueueTable_MYFILE))
//					foundTrans_myfile = true;				
				else if (tblName.equalsIgnoreCase(TransactQueueTable_COMMFILE))
					foundTrans_commfile = true;
//				else if (tblName.equalsIgnoreCase(TransactQueueTable_SHAREFILE))
//					foundTrans_sharefile = true;
				
//				else if (tblName.equalsIgnoreCase(DataTable_MYFILE))
//					foundData_myfile = true;				
				else if (tblName.equalsIgnoreCase(DataTable_COMMFILE))
					foundData_commfile = true;
//				else if (tblName.equalsIgnoreCase(DataTable_SHAREFILE))
//					foundData_sharefile = true;
				//others
				else if (tblName.equalsIgnoreCase(JarDataTable))
					foundJarData = true;				
				else if (tblName.equalsIgnoreCase(ObjectIdTable))
					foundObjId = true;
				else if (tblName.equalsIgnoreCase(SymLinkTable))
					foundSymLink = true;
				
				else if(tblName.equalsIgnoreCase("jweb_commonfilerevision_value"))
					foundCommonRevisionValue = true;
			}

			// Check if the file system structure table should be created

			/*if (foundStruct == false) {

				// Create the file system structure table

				Statement stmt = conn.createStatement();

				stmt
						.execute("CREATE TABLE "
								+ FileSysTableName
								+ " (FileId INTEGER AUTO_INCREMENT, DirId INTEGER, FileName VARCHAR(255) BINARY NOT NULL, FileSize BIGINT,"
								+ "CreateDate BIGINT, ModifyDate BIGINT, AccessDate BIGINT, ChangeDate BIGINT, ReadOnly BIT, Archived BIT, Directory BIT,"
								+ "SystemFile BIT, Hidden BIT, IsSymLink BIT, Uid INTEGER, Gid INTEGER, Mode INTEGER, Deleted BIT NOT NULL DEFAULT 0, "
								+ "PRIMARY KEY (FileId));");

				// Create various indexes

				stmt.execute("ALTER TABLE " + FileSysTableName
						+ " ADD UNIQUE INDEX IFileDirId (FileName,DirId);");
				stmt.execute("ALTER TABLE " + FileSysTableName
						+ " ADD INDEX IDirId (DirId);");
				stmt.execute("ALTER TABLE " + FileSysTableName
						+ " ADD INDEX IDir (DirId,Directory);");
				stmt
						.execute("ALTER TABLE "
								+ FileSysTableName
								+ " ADD UNIQUE INDEX IFileDirIdDir (FileName,DirId,Directory);");

				stmt.close();

				// DEBUG
				log4j.info("[mySQL] Created table " + FileSysTableName);
			}*/

			// Check if the file streams table should be created

			/*if (isNTFSEnabled() && foundStream == false
					&& getStreamsTableName() != null) {

				// Create the file streams table

				Statement stmt = conn.createStatement();

				stmt
						.execute("CREATE TABLE "
								+ getStreamsTableName()
								+ " (StreamId INTEGER AUTO_INCREMENT, FileId INTEGER NOT NULL, StreamName VARCHAR(255) BINARY NOT NULL, StreamSize BIGINT,"
								+ "CreateDate BIGINT, ModifyDate BIGINT, AccessDate BIGINT, PRIMARY KEY (StreamId));");

				// Create various indexes

				stmt.execute("ALTER TABLE " + getStreamsTableName()
						+ " ADD INDEX IFileId (FileId);");

				stmt.close();

				// DEBUG
				log4j.info("[mySQL] Created table " + getStreamsTableName());
			}*/
			
			if (isNTFSEnabled() ){
				
				if(foundStream_myfile==false)
				{
					// Create the file streams table

					Statement stmt = conn.createStatement();

					stmt
							.execute("CREATE TABLE "
									+ StreamsTable_MYFILE
									+ " (StreamId INTEGER AUTO_INCREMENT, FileId INTEGER NOT NULL, StreamName VARCHAR(255) BINARY NOT NULL, StreamSize BIGINT,"
									+ "CreateDate BIGINT, ModifyDate BIGINT, AccessDate BIGINT, PRIMARY KEY (StreamId));");

					// Create various indexes

					stmt.execute("ALTER TABLE " + StreamsTable_MYFILE
							+ " ADD INDEX IFileId (FileId);");

					stmt.close();

					// DEBUG
					log4j.info("[mySQL] Created table " + StreamsTable_MYFILE);
				}
				if(foundStream_commfile==false)
				{
					// Create the file streams table

					Statement stmt = conn.createStatement();

					stmt
							.execute("CREATE TABLE "
									+ StreamsTable_COMMFILE
									+ " (StreamId INTEGER AUTO_INCREMENT, FileId INTEGER NOT NULL, StreamName VARCHAR(255) BINARY NOT NULL, StreamSize BIGINT,"
									+ "CreateDate BIGINT, ModifyDate BIGINT, AccessDate BIGINT, PRIMARY KEY (StreamId));");

					// Create various indexes

					stmt.execute("ALTER TABLE " + StreamsTable_COMMFILE
							+ " ADD INDEX IFileId (FileId);");

					stmt.close();

					// DEBUG
					log4j.info("[mySQL] Created table " + StreamsTable_MYFILE);
				}
				if(foundStream_sharefile==false)
				{
					// Create the file streams table

					Statement stmt = conn.createStatement();

					stmt
							.execute("CREATE TABLE "
									+ StreamsTable_SHAREFILE
									+ " (StreamId INTEGER AUTO_INCREMENT, FileId INTEGER NOT NULL, StreamName VARCHAR(255) BINARY NOT NULL, StreamSize BIGINT,"
									+ "CreateDate BIGINT, ModifyDate BIGINT, AccessDate BIGINT, PRIMARY KEY (StreamId));");

					// Create various indexes

					stmt.execute("ALTER TABLE " + StreamsTable_SHAREFILE
							+ " ADD INDEX IFileId (FileId);");

					stmt.close();

					// DEBUG
					log4j.info("[mySQL] Created table " + StreamsTable_MYFILE);
				}
				
			}

			// Check if the retention table should be created

			/*if (isRetentionEnabled() && foundRetain == false
					&& getRetentionTableName() != null) {

				// Create the retention period data table

				Statement stmt = conn.createStatement();

				stmt
						.execute("CREATE TABLE "
								+ getRetentionTableName()
								+ " (FileId INTEGER NOT NULL, StartDate TIMESTAMP, EndDate TIMESTAMP,"
								+ "PurgeFlag TINYINT(1), PRIMARY KEY (FileId));");
				stmt.close();

				// DEBUG

				log4j.info("[mySQL] Created table " + getRetentionTableName());
			}*/
			
			if (isRetentionEnabled()) {

//				if(foundRetain_myfile==false)
//				{
//					// Create the retention period data table
//
//					Statement stmt = conn.createStatement();
//
//					stmt
//							.execute("CREATE TABLE "
//									+ RetentionTable_MYFILE
//									+ " (FileId INTEGER NOT NULL, StartDate TIMESTAMP, EndDate TIMESTAMP,"
//									+ "PurgeFlag TINYINT(1), PRIMARY KEY (FileId));");
//					stmt.close();
//
//					// DEBUG
//
//					log4j.info("[mySQL] Created table " + RetentionTable_MYFILE);
//				}
				if(foundRetain_commfile==false)
				{
					// Create the retention period data table

					Statement stmt = conn.createStatement();

					stmt
							.execute("CREATE TABLE "
									+ RetentionTable_COMMFILE
									+ " (FileId INTEGER NOT NULL, StartDate TIMESTAMP, EndDate TIMESTAMP,"
									+ "PurgeFlag TINYINT(1), PRIMARY KEY (FileId));");
					stmt.close();

					// DEBUG

					log4j.info("[mySQL] Created table " + RetentionTable_COMMFILE);
				}
//				if(foundRetain_sharefile==false)
//				{
//					// Create the retention period data table
//
//					Statement stmt = conn.createStatement();
//
//					stmt
//							.execute("CREATE TABLE "
//									+ RetentionTable_SHAREFILE
//									+ " (FileId INTEGER NOT NULL, StartDate TIMESTAMP, EndDate TIMESTAMP,"
//									+ "PurgeFlag TINYINT(1), PRIMARY KEY (FileId));");
//					stmt.close();
//
//					// DEBUG
//
//					log4j.info("[mySQL] Created table " + RetentionTable_SHAREFILE);
//				}
			}

			// Check if the file loader queue table should be created

			if (isQueueEnabled()) {

//				if(foundQueue_myfile==false)
//				{
//					// Create the request queue data table
//
//					Statement stmt = conn.createStatement();
//
//					stmt
//							.execute("CREATE TABLE "
//									+ QueueTable_MYFILE
//									+ " (FileId INTEGER NOT NULL, StreamId INTEGER NOT NULL, ReqType SMALLINT,"
//									+ "SeqNo INTEGER AUTO_INCREMENT, TempFile TEXT, VirtualPath TEXT, QueuedAt TIMESTAMP, Attribs VARCHAR(512), PRIMARY KEY (SeqNo));");
//					stmt.execute("ALTER TABLE " +QueueTable_MYFILE
//							+ " ADD INDEX IFileId (FileId);");
//					stmt.execute("ALTER TABLE " +QueueTable_MYFILE
//							+ " ADD INDEX IFileIdType (FileId, ReqType);");
//
//					stmt.close();
//
//					// DEBUG
//					log4j.info("[mySQL] Created table " + QueueTable_MYFILE);
//				}
				
				if(foundQueue_commfile==false)
				{
					// Create the request queue data table

					Statement stmt = conn.createStatement();

					stmt
							.execute("CREATE TABLE "
									+ QueueTable_COMMFILE
									+ " (FileId INTEGER NOT NULL, StreamId INTEGER NOT NULL, ReqType SMALLINT,"
									+ "SeqNo INTEGER AUTO_INCREMENT, TempFile TEXT, VirtualPath TEXT, QueuedAt TIMESTAMP, Attribs VARCHAR(512), PRIMARY KEY (SeqNo));");
					stmt.execute("ALTER TABLE " +QueueTable_COMMFILE
							+ " ADD INDEX IFileId (FileId);");
					stmt.execute("ALTER TABLE " +QueueTable_COMMFILE
							+ " ADD INDEX IFileIdType (FileId, ReqType);");

					stmt.close();

					// DEBUG
					log4j.info("[mySQL] Created table " + QueueTable_COMMFILE);
				}
				
				/*if(foundQueue_sharefile==false)
				{
					// Create the request queue data table

					Statement stmt = conn.createStatement();

					stmt
							.execute("CREATE TABLE "
									+ QueueTable_SHAREFILE
									+ " (FileId INTEGER NOT NULL, StreamId INTEGER NOT NULL, ReqType SMALLINT,"
									+ "SeqNo INTEGER AUTO_INCREMENT, TempFile TEXT, VirtualPath TEXT, QueuedAt TIMESTAMP, Attribs VARCHAR(512), PRIMARY KEY (SeqNo));");
					stmt.execute("ALTER TABLE " +QueueTable_SHAREFILE
							+ " ADD INDEX IFileId (FileId);");
					stmt.execute("ALTER TABLE " +QueueTable_SHAREFILE
							+ " ADD INDEX IFileIdType (FileId, ReqType);");

					stmt.close();

					// DEBUG
					log4j.info("[mySQL] Created table " + QueueTable_MYFILE);
				}
				*/
			}

			// Check if the file loader transaction queue table should be
			// created

			if (isQueueEnabled()) {

				/*if(foundTrans_myfile==false)
				{
					// Create the transaction request queue data table

					Statement stmt = conn.createStatement();

					stmt
							.execute("CREATE TABLE "
									+ TransactQueueTable_MYFILE
									+ " (FileId INTEGER NOT NULL, StreamId INTEGER NOT NULL,"
									+ "TranId INTEGER NOT NULL, ReqType SMALLINT, TempFile TEXT, VirtualPath TEXT, QueuedAt TIMESTAMP,"
									+ "Attribs VARCHAR(512), PRIMARY KEY (FileId,StreamId,TranId));");

					stmt.close();

					// DEBUG
					log4j
							.info("[mySQL] Created table "
									+ TransactQueueTable_MYFILE);
				}*/
				if(foundTrans_commfile==false)
				{
					// Create the transaction request queue data table

					Statement stmt = conn.createStatement();

					stmt
							.execute("CREATE TABLE "
									+ TransactQueueTable_COMMFILE
									+ " (FileId INTEGER NOT NULL, StreamId INTEGER NOT NULL,"
									+ "TranId INTEGER NOT NULL, ReqType SMALLINT, TempFile TEXT, VirtualPath TEXT, QueuedAt TIMESTAMP,"
									+ "Attribs VARCHAR(512), PRIMARY KEY (FileId,StreamId,TranId));");

					stmt.close();

					// DEBUG
					log4j
							.info("[mySQL] Created table "
									+ TransactQueueTable_COMMFILE);
				}
				/*if(foundTrans_sharefile==false)
				{
					// Create the transaction request queue data table

					Statement stmt = conn.createStatement();

					stmt
							.execute("CREATE TABLE "
									+ TransactQueueTable_SHAREFILE
									+ " (FileId INTEGER NOT NULL, StreamId INTEGER NOT NULL,"
									+ "TranId INTEGER NOT NULL, ReqType SMALLINT, TempFile TEXT, VirtualPath TEXT, QueuedAt TIMESTAMP,"
									+ "Attribs VARCHAR(512), PRIMARY KEY (FileId,StreamId,TranId));");

					stmt.close();

					// DEBUG
					log4j
							.info("[mySQL] Created table "
									+ TransactQueueTable_SHAREFILE);
				}*/
				
			}

			// Check if the file data table should be created

			if (isDataEnabled()) {

//				if(foundData_myfile==false)
//				{
//					// Create the file data table
//
//					Statement stmt = conn.createStatement();
//
//					stmt
//							.execute("CREATE TABLE "
//									+ DataTable_MYFILE
//									+ " (FileId INTEGER NOT NULL, StreamId INTEGER NOT NULL, FragNo INTEGER, FragLen INTEGER, Data LONGBLOB, JarFile BIT, JarId INTEGER);");
//
//					stmt.execute("ALTER TABLE " + DataTable_MYFILE
//							+ " ADD INDEX IFileStreamId (FileId,StreamId);");
//					stmt.execute("ALTER TABLE " + DataTable_MYFILE
//							+ " ADD INDEX IFileId (FileId);");
//					stmt.execute("ALTER TABLE " + DataTable_MYFILE
//							+ " ADD INDEX IFileIdFrag (FileId,FragNo);");
//
//					stmt.close();
//
//					// DEBUG
//					log4j.info("[mySQL] Created table " + DataTable_MYFILE);
//				}
				if(foundData_commfile==false)
				{
					// Create the file data table

					Statement stmt = conn.createStatement();

					stmt
							.execute("CREATE TABLE "
									+ DataTable_COMMFILE
									+ " (FileId INTEGER NOT NULL, StreamId INTEGER NOT NULL, FragNo INTEGER, FragLen INTEGER, Data LONGBLOB, JarFile BIT, JarId INTEGER);");

					stmt.execute("ALTER TABLE " + DataTable_COMMFILE
							+ " ADD INDEX IFileStreamId (FileId,StreamId);");
					stmt.execute("ALTER TABLE " + DataTable_COMMFILE
							+ " ADD INDEX IFileId (FileId);");
					stmt.execute("ALTER TABLE " + DataTable_COMMFILE
							+ " ADD INDEX IFileIdFrag (FileId,FragNo);");

					stmt.close();

					// DEBUG
					log4j.info("[mySQL] Created table " + DataTable_COMMFILE);
				}
//				if(foundData_sharefile==false)
//				{
//					// Create the file data table
//
//					Statement stmt = conn.createStatement();
//
//					stmt
//							.execute("CREATE TABLE "
//									+ DataTable_SHAREFILE
//									+ " (FileId INTEGER NOT NULL, StreamId INTEGER NOT NULL, FragNo INTEGER, FragLen INTEGER, Data LONGBLOB, JarFile BIT, JarId INTEGER);");
//
//					stmt.execute("ALTER TABLE " + DataTable_SHAREFILE
//							+ " ADD INDEX IFileStreamId (FileId,StreamId);");
//					stmt.execute("ALTER TABLE " + DataTable_SHAREFILE
//							+ " ADD INDEX IFileId (FileId);");
//					stmt.execute("ALTER TABLE " + DataTable_SHAREFILE
//							+ " ADD INDEX IFileIdFrag (FileId,FragNo);");
//
//					stmt.close();
//
//					// DEBUG
//					log4j.info("[mySQL] Created table " + DataTable_SHAREFILE);
//				}
				
			}

			// Check if the Jar file data table should be created

			if (isJarDataEnabled() && foundJarData == false) {

				// Create the Jar file data table

				Statement stmt = conn.createStatement();

				stmt
						.execute("CREATE TABLE "
								+ JarDataTable
								+ " (JarId INTEGER AUTO_INCREMENT, Data LONGBLOB, PRIMARY KEY (JarId));");

				stmt.close();

				// DEBUG
				log4j.info("[mySQL] Created table " + JarDataTable);
			}

			// Check if the file id/object id mapping table should be created

			if (isObjectIdEnabled() && foundObjId == false) {

				// Create the file id/object id mapping table

				Statement stmt = conn.createStatement();

				stmt
						.execute("CREATE TABLE "
								+ ObjectIdTable
								+ " (FileId INTEGER NOT NULL, StreamId INTEGER NOT NULL, ObjectId VARCHAR(128), PRIMARY KEY (FileId,StreamId))");

				stmt.close();

				// DEBUG

				log4j.info("[mySQL] Created table " + ObjectIdTable);
			}

			// Check if the symbolic links table should be created

			if (isSymbolicLinksEnabled() && foundSymLink == false) {

				// Create the symbolic links table

				Statement stmt = conn.createStatement();

				stmt
						.execute("CREATE TABLE "
								+ SymLinkTable
								+ " (FileId INTEGER NOT NULL PRIMARY KEY, SymLink VARCHAR(8192))");

				stmt.close();

				// DEBUG
				log4j.info("[mySQL] Created table " + SymLinkTable);
			}
			
			if(foundCommonRevisionValue==false)
			{
				// Create the CommonFile Revision Value mapping table

				Statement stmt = conn.createStatement();

				stmt.execute("CREATE TABLE `jweb_commonfilerevision_value` "+
						"(max_value bigint(20) NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;");
				stmt.execute("REPLACE INTO `jweb_commonfilerevision_value` select max(revision) from jweb_commonfilecache;");				
				stmt.close();
				// DEBUG

				log4j.info("[mySQL] Created table  `jweb_commonfilerevision_value`");
			}
		} catch (Exception ex) {
			log4j.error("Error: " + ex.toString());
		} finally {

			// Release the database connection

			if (conn != null)
				releaseConnection(conn);
		}
		
		initData = true;//已经初始化过
		}
	}

	/**
	 * Check if a file/folder exists
	 * 
	 * @param dirId
	 *            int
	 * @param fname
	 *            String
	 * @return FileStatus.NotExist, FileStatus.FileExists or
	 *         FileStatus.DirectoryExists
	 * @throws DBException
	 */
	public int fileExists(int dirId, String fname,String shareName) throws DBException {

		// Check if the file exists, and whether it is a file or folder
		log4j.debug("DBI#fileExists ; fname:" + fname + ", dirId:" + dirId);
		int sts = FileStatus.NotExist;
		
		//虚拟路径，直接返回存在
		if(dirId<=0 && (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_USERFILE)||shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_COMMFILE)||shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_RECIVEFILE)))
		{
			return FileStatus.DirectoryExists;
		}

		Connection conn = null;
		Statement stmt = null;

		try {

			String FileSysTableName = getFileSysTableName(shareName);
			// Get a connection to the database, create a statement for the
			// database lookup

			conn = getConnection();
			stmt = conn.createStatement();

			String existSql = "SELECT name,isFile,lastModified,status FROM "+ FileSysTableName + " WHERE status>=0 AND pid ="+ dirId + " AND lower(name) = lower('"+ checkNameForSpecialChars(fname) + "') ;";
			String delSql = "SELECT name,isFile,lastModified,status FROM "+ FileSysTableName + " WHERE status >= -1 AND pid ="+ dirId + " AND lower(name) = lower('"+ checkNameForSpecialChars(fname) + "') ;";

			// if(dirId>-1)
			if (dirId < 0)// 查用户(-1为用户层)
			{
				existSql = "SELECT username as name,false as isFile,0 as lastModified,status FROM jweb_users WHERE status>=0 AND lower(username) = lower('"+ checkNameForSpecialChars(fname) + "') ;";
				delSql = "SELECT username as name,false as isFile,0 as lastModified,status FROM jweb_users WHERE status>=0 AND lower(username) = lower('"+ checkNameForSpecialChars(fname) + "') ;";
			}

			// DEBUG
			log4j.debug("[mySQL] File exists SQL: " + existSql);

			// Search for the file/folder

			// Check if a file record exists
			synchronized (this)// 暂时改为同步锁定。 避免产生重复文件
			{
				log4j.debug("[mySQL] existSql SQL:" + existSql);
				ResultSet rs = stmt.executeQuery(existSql);
				if (rs.next()) {

					// Check if the record is for a file or folder
					// //1代表TRUE文件,0代表FALSE目录
					if (rs.getBoolean("isFile") == true)
						sts = FileStatus.FileExists;
					else
						sts = FileStatus.DirectoryExists;
				} else {
					// 删除不超过10秒(10000ms)也表示存在
					log4j.debug("[mySQL] delSql SQL:" + existSql);
					rs = stmt.executeQuery(delSql);
					if (rs.next()) {
						long lastTime = rs.getLong("lastModified");
						int status = rs.getInt("status");
						if (status >= 0 || System.currentTimeMillis() - lastTime <= delFileKeepTime) {
							// Check if the record is for a file or folder
							// //1代表TRUE文件,0代表FALSE目录
							if (rs.getBoolean("isFile") == true)
								sts = FileStatus.FileExists;
							else
								sts = FileStatus.DirectoryExists;
						} else {
							log4j.info("fname:" + fname + " 已经存在一个status=-1 的 ，但存在时间已经超过: " + (System.currentTimeMillis() - lastTime) + "ms");
						}
					}
				}
				// Close the result set
				rs.close();
			}

		} catch (Exception ex) {
			log4j.error("DBI#fileExists 异常:",ex);
		} finally {

			// Close the statement

			if (stmt != null) {
				try {
					stmt.close();
				} catch (Exception ex) {
				}
			}

			// Release the database connection

			if (conn != null)
				releaseConnection(conn);
		}

		// Return the status

		return sts;
	}

	/**
	 * Create a file record for a new file or folder
	 * 
	 * @param fname
	 *            String
	 * @param dirId
	 *            int
	 * @param params
	 *            FileOpenParams
	 * @param retain
	 *            boolean
	 * @return int
	 * @exception DBException
	 * @exception FileExistsException
	 */
	public synchronized int createFileRecord(String fname, int dirId, FileOpenParams params,
			boolean retain, String userName, String shareName, String ipAddress)
			throws DBException, FileExistsException,AccessDeniedException {
		if(shareName.equals(""))
		{
			
		}
		java.util.Date nowDate = new java.util.Date();// 时间的定义放在前面。这样即使中间操作花了较多时间。也可以用这个时间与原文件的时间比较
		long timeNow = System.currentTimeMillis();
		log4j.debug("DBI#createFileRecord time:" + nowDate.getTime() + ", fname:" + fname + ", shareName:" + shareName + "; path:" + params.getPath());
		String FileRevisionTableName = getRevisionTableName(shareName);
		// Create a new file record for a file/folder and return a unique file
		// id
		
		// Office临时重命名的文件
//		Pattern renamePattern = Pattern.compile(wpsRenameReg);
//		Matcher rMatcher = renamePattern.matcher(fname);
		boolean needCreateRevision = true;
//		if (rMatcher.find()) {
//			try {
//				needCreateRevision = false;
//				fname = fname.substring(0, fname.indexOf("~"));
//			} catch (Exception e) {
//				log4j.error("DBI#createFileRecord fname.substring ERROR:" + fname);
//			}
//		}

		Connection conn = null;
		PreparedStatement pstmt = null;
		Statement stmt = null;
		int userId = 0;
		int fileId = -1;
		long userSpaceSize = 0;// 单位M, 判断时要*1024*1024;//暂时没用起来
		long userFileSize = 0;
		int roleId = 0;
		int departmentId = 0;

		int permissionsFileId = 0;
		boolean duplicateKey = false;

		try {

			String FileSysTableName = getFileSysTableName(shareName);
			UserBean user = this.getUserByUsername(userName);
			if (null != user) {
				userId = user.getId();
				userSpaceSize = user.getUserSpaceSize();
				roleId = user.getRoleId();
				departmentId = user.getDepartmentId();
			}

			// Get a database connection
			conn = getConnection();

			// Check if the file already exists in the database
			stmt = conn.createStatement();

			// 获取组Id
			String chkFileName = checkNameForSpecialChars(fname);

			String sSql = "SELECT SUM(size)as size FROM jweb_filecache WHERE status >=0 AND userId = "
					+ userId;
			log4j.debug("[mySQL] sumSql SQL:" + sSql);
			ResultSet rs = stmt.executeQuery(sSql);
			if (rs.next()) {
				userFileSize = rs.getLong("size");
			}
			rs.close();
			/*
			 * if(userSpaceSize == 0) //暂不用，先不查 { //个人未设置空间大小，则取全局空间大小 countSql
			 * =
			 * "SELECT value FROM jweb_config WHERE name='"+USERSPACE_MAXSIZE+"'"
			 * ; log4j.debug("[mySQL] countSql SQL:"+countSql); rs =
			 * stmt.executeQuery(countSql);//取全局空间大小 if(rs.next()) { try{
			 * userSpaceSize = Long.parseLong(rs.getString("value"));
			 * }catch(Exception e) {
			 * log4j.error("\n\n ## Long.parseLong Exception ; "
			 * +e.getMessage()); } } if(userSpaceSize<=0) { userSpaceSize =
			 * 102400;//(单位M); } rs.close(); }
			 */

			// System.out.println("  查询组  SQL语句 "+gSql.toString() );

			// 如果使用的大小超过预定的百分之一 不允许上传
			// if(userFileSize > 0){
			// if(userSpaceSize-userFileSize/userSpaceSize<0.1){
			// throw new DiskFullException("个人文件磁盘已满");
			// }
			// }
			String rootPath = "";// 第一次保存时，不保存存储池的路径
			// 创建文件 查询权限
			if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_COMMFILE)) {
				// 查询当前目录权限的Id
				String PathSql = "SELECT permissionsFileId FROM jweb_commonfilecache WHERE id =" + dirId;
				log4j.debug("[mySQL] PathSql SQL:" + PathSql);
				ResultSet pathRs = stmt.executeQuery(PathSql);
				if (pathRs.next()) {
					permissionsFileId = pathRs.getInt("permissionsFileId");
				}
				pathRs.close();
				
				boolean isSuperAdmin = false;//资料库用
				boolean isFileAdmin = false;//资料库用
				
				if (roleId == 1) {// 超级管理员权限 直接查询出来
					isSuperAdmin = true;
				}
				else
				{
					// 查询是否为库管理员
					if (dirId > 0) {
						String auditorSql = "SELECT auditorId FROM jweb_commonfilecache WHERE id=(SELECT pid1 FROM jweb_commonfilecache WHERE id=" + dirId + ")";
						try {
							log4j.debug("[mySQL] auditorSql  SQL: " + auditorSql);
							ResultSet rs2 = stmt.executeQuery(auditorSql);
							String auditorId = "";
							while (rs2.next()) {
								auditorId = "," + rs2.getString("auditorId") + ",";
							}
							rs2.close();
							if (auditorId.contains("," + userId + ",")) {
								isFileAdmin = true;// 是资料库管理员，有所有权限
							}
							rs2.close();
						} catch (Exception e) {
							log4j.error("公共资料库 查询组、部门失败", e);
						}
					}
				}
				
				if(isSuperAdmin==false && isFileAdmin==false)
				{
					String groupIds = this.getGroupIdsByUserid(userId);
					// 查询部门的parentId
					String departmentPath = this.getDepartmentPathById(departmentId);
					String parentPermissions = "rw";
					if(dirId>0)
					{
						parentPermissions = this.filePermissions(permissionsFileId, userId, groupIds, departmentPath);
					}
					if (parentPermissions.contains("w") == false) {
						log4j.error("ERROR 没有写权限 ; userId:" + userId + ",dirId:" + dirId + ",fname:" + fname);
						throw new AccessDeniedException("没有权限");
					}
				}
			}

			String existsSql = null;
//			if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_COMMFILE)) {
				existsSql = "SELECT name,id ,lastModified,revisionCount,status FROM "+ FileSysTableName
						+ " WHERE status>=0 AND LOWER(name) = LOWER('"+ checkNameForSpecialChars(chkFileName)+ "') AND pid = " + dirId + " ORDER BY id DESC";
//			} else {
//				existsSql = "SELECT name,id ,lastModified,revisionCount,status FROM jweb_filecache"+ FileSysTableName
//				+ " WHERE status>=0 AND LOWER(name) = LOWER('"+ checkNameForSpecialChars(chkFileName)+ "') AND pid = "+ dirId+ " AND userId = "+ userId+ " ORDER BY id DESC";
//				existsSql = "SELECT name,id ,lastModified,revisionCount,status FROM jweb_commonfilecache"
//					+ " WHERE status>=0 AND LOWER(name) = LOWER('"+ checkNameForSpecialChars(chkFileName)+ "') AND pid = "+ dirId+" ORDER BY id DESC";
//			}
			// 查询重复文件
			int fileStatus = 0;
			int revisionCount = 1;

			conn.setAutoCommit(false);
			synchronized (this)// 暂时改为同步。 避免产生重复文件
			{
				log4j.debug("[mySQL] existsSql SQL:" + existsSql);
				rs = stmt.executeQuery(existsSql);
				if (rs.next()) {
					fileId = rs.getInt("id");
					fileStatus = rs.getInt("status");
					revisionCount = rs.getInt("revisionCount");
					long lastTime = rs.getLong("lastModified");
					if (fileStatus > 0 && fileStatus != 4)// 文件已经存在
					{
						log4j.debug("File record already exists for " + fname
								+ ", fileId=" + fileId);
						if (needCreateRevision) {
							revisionCount++;
							// 插入版本记录(staus=4，正在编辑)
							String insertSql = "insert into "
									+ FileRevisionTableName
									+ " (fileId,md5,size,lastModified,userId,revision,revisionCount,rollPath,status) values ("
									+ fileId + ",'md51',0," + nowDate.getTime() + ","
									+ userId + ",0," + revisionCount + ",'"+ rootPath + "',4)";
							log4j.debug("[mySQL] insertSql SQL:" + insertSql);
							stmt.executeUpdate(insertSql);

							String upSql = "UPDATE " +FileSysTableName+"  SET revisionCount=" + revisionCount+ ",status=4,lastModified="+ nowDate.getTime() + " WHERE id=" + fileId;
							log4j.debug("[mySQL] 更新文件状态 SQL:" + upSql);
							stmt.executeUpdate(upSql);
						}

						return fileId;
					} else {
						log4j.debug("File record already exists update for "
								+ fname + ", fileId=" + fileId);
						if (needCreateRevision) {
							revisionCount++;
							// 插入版本记录(staus=4，正在编辑)
							String insertSql = "insert into "
									+ FileRevisionTableName
									+ " (fileId,md5,size,lastModified,userId,revision,revisionCount,rollPath,status) values ("
									+ fileId + ",'md51',0," + nowDate.getTime() + ","+ userId + ",0," + revisionCount + ",'"+ rootPath + "',4)";
							log4j.debug("[mySQL] insertSql SQL:" + insertSql);
							stmt.executeUpdate(insertSql);

//							String upSql = "UPDATE jweb_filecache" + FileSysTableName+ " SET revisionCount=" + revisionCount+ ",status=4,lastModified="+ nowDate.getTime() + " WHERE id=" + fileId;
							String upSql = "UPDATE " +FileSysTableName+"  SET revisionCount=" + revisionCount+ ",status=4,lastModified="+ nowDate.getTime() + " WHERE id=" + fileId;
							log4j.debug("[mySQL] 更新文件状态 SQL:" + upSql);
							stmt.executeUpdate(upSql);
						}

						// 文件已经存在，则不用再执行下面的插入sql
						return fileId;
					}
				} else {
					// 删除不超过10秒(10000ms)也表示存在。则不新建
					log4j.debug("[mySQL] existsSql del SQL:"
							+ existsSql.replace("status>=0", "status=-1"));
					rs = stmt.executeQuery(existsSql.replace("status>=0",
							"status>=-1"));
					if (rs.next()) {
						fileId = rs.getInt("id");
						long lastTime = rs.getLong("lastModified");
						fileStatus = rs.getInt("status");
						if (fileStatus >= 0
								|| nowDate.getTime() - lastTime <= delFileKeepTime) {
							if (needCreateRevision) {
								revisionCount++;
								// 插入版本记录(staus=4，正在编辑)
								String insertSql = "insert into "
										+ FileRevisionTableName
										+ " (fileId,md5,size,lastModified,userId,revision,revisionCount,rollPath,status) values ("
										+ fileId + ",'md51',0," + nowDate.getTime()+ "," + userId + ",0," + revisionCount+ ",'" + rootPath + "',4)";
								log4j.debug("[mySQL] insertSql SQL:"+ insertSql);
								stmt.executeUpdate(insertSql);

								String upSql = "UPDATE " +FileSysTableName+"  SET revisionCount=" + revisionCount+ ",status=4,lastModified="+ nowDate.getTime() + " WHERE id="+ fileId;
								log4j.debug("[mySQL] 更新已删除文件状态 SQL:" + upSql);
								stmt.executeUpdate(upSql);
							}

							return fileId;
						} else {
							log4j.info("fname:" + fname
									+ " 已经存在一个status=-1 的 ，但存在时间已经超过: "
									+ (System.currentTimeMillis() - lastTime)
									+ "ms");
						}
					}
				}
			}
			// 查询最大版本数 及数据库中相关path值
			boolean dirRec = params.isDirectory();
			String path2 = null;
			String pathName = null;
			String realPath = null;
			int pid = 0;
			int pid1 = 0;// 一级目录id
			int pid2 = 0;// 二级目录id
			String pathIds = "";
			int revision = 1;

			String vsql = "SELECT max(revision) as revision FROM " +FileSysTableName;
			log4j.debug("[mySQL] vsql SQL:" + vsql);
			rs = stmt.executeQuery(vsql);
			if (rs.next()) {
				revision = rs.getInt("revision") + 1;
			}
			rs.close();
			// Check if a file or folder record should be created
			if (dirId == 0) {
				realPath = "/";
			} else if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_COMMFILE)) {
				// 先不查询状态 String sql =
				// "SELECT path ,name FROM jweb_commonfilecache WHERE status>=0 AND isFile=0 AND id ="+dirId;
				// isFile:1代表TRUE文件,0代表FALSE目录
				String sql = "SELECT path ,name,pid1,pid2 ,pid ,pathids,permissionsFileId FROM "+ FileSysTableName + " WHERE  isFile=0 AND id ="+ dirId;
				log4j.debug("[mySQL] old file SQL:" + sql);
				ResultSet pRs = stmt.executeQuery(sql);
				if (pRs.next()) {
					path2 = pRs.getString("path");
					pathName = pRs.getString("name");
					pid1 = pRs.getInt("pid1");
					pid2 = pRs.getInt("pid2");
					pid = pRs.getInt("pid");
					pathIds = pRs.getString("pathids");
					permissionsFileId = pRs.getInt("permissionsFileId");
				}
				pRs.close();
				if (path2.equalsIgnoreCase("/")) {
					realPath = path2 + pathName;
				} else {
					realPath = path2 + "/" + pathName;
				}
			} else {
				// isFile:1代表TRUE文件,0代表FALSE目录
				String sql = "SELECT path ,name FROM " +FileSysTableName+"  WHERE  isFile=0 AND id =" + dirId;
				log4j.debug("[mySQL] path SQL:" + sql);
				ResultSet pRs = stmt.executeQuery(sql);
				if (pRs.next()) {
					path2 = pRs.getString("path");
					pathName = pRs.getString("name");
				}
				pRs.close();
				if (path2.equalsIgnoreCase("/")) {
					realPath = path2 + pathName;
				} else {
					realPath = path2 + "/" + pathName;
				}
			}

			// 保存到数据库当中去
			if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_COMMFILE)) {
				pstmt = conn
						.prepareStatement("INSERT INTO jweb_commonfilecache"
//								+ FileSysTableName
								+ "(path,name,add_time,lastModified,pid,isFile,size,userId,revision,revisionCount,status,md5,pid1,pid2,pathids,permissionsFileId)"
								+ " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
				pstmt.setString(1, realPath);
				pstmt.setString(2, chkFileName);
				pstmt.setString(3, sf.format(new Date(timeNow)));
				pstmt.setLong(4, nowDate.getTime());
				pstmt.setInt(5, dirId);
				if (dirRec == true) {
					pstmt.setBoolean(6, false);
				} else {
					pstmt.setBoolean(6, true);
				}
				pstmt.setInt(7, 0);

				pstmt.setInt(8, userId);
				pstmt.setInt(9, revision);
				pstmt.setInt(10, revisionCount);
				if (dirRec == true) {
					pstmt.setInt(11, 0);
					pstmt.setString(12, "md5");
				} else {
					pstmt.setInt(11, 4);
					pstmt.setString(12, "md51");
				}
				pstmt.setInt(13, pid1);
				pstmt.setInt(14, pid2);
				if (StringUtils.isNotEmpty(pathIds)) {
					pstmt.setString(15, pathIds);
				} else {
					pstmt.setString(15, "" + pid);
				}
				pstmt.setInt(16, permissionsFileId);
			} else {
				if (shareName.equals(DBUtil.SHARENAME_WAS_RECIVEFILE)) {
					// 需考虑 receive type 。。。
				} else {
					
				}
				
				pstmt = conn
						.prepareStatement("INSERT INTO  "
								+ FileSysTableName
								+ "(path,name,add_time,lastModified,pid,isFile,size,userId,revision,revisionCount,status,md5)"
								+ " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)");
				pstmt.setString(1, realPath);
				pstmt.setString(2, chkFileName);
				pstmt.setString(3, sf.format(new Date(timeNow)));
				pstmt.setLong(4, nowDate.getTime());
				pstmt.setInt(5, dirId);
				if (dirRec == true) {
					pstmt.setBoolean(6, false);
				} else {
					pstmt.setBoolean(6, true);
				}
				pstmt.setInt(7, 0);

				pstmt.setInt(8, userId);
				pstmt.setInt(9, revision);
				pstmt.setInt(10, revisionCount);
				if (dirRec == true) {
					pstmt.setInt(11, 0);
					pstmt.setString(12, "md5");
				} else {
					pstmt.setInt(11, 4);
					pstmt.setString(12, "md51");
				}
			}

			log4j.debug("[mySQL] Create file SQL: " + pstmt.toString());
			if (pstmt.executeUpdate() > 0) {
				// 获取最后插入的ID值
				log4j.debug("[mySQL] SELECT LAST_INSERT_ID();");
				ResultSet rs2 = stmt.executeQuery("SELECT LAST_INSERT_ID();");

				if (rs2.next())
					fileId = rs2.getInt(1);
				rs2.close();
				// Check if the returned file id is valid
				if (fileId == -1) {
					log4j.error("Failed to get file id for " + fname);
					throw new DBException("Failed to get file id for " + fname);
				}

				if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_COMMFILE)) {
					String qStr = "insert into jweb_systemrole_commonfile (role_id,file_id,role_value,role_type,ip) values("+ userId+ ","+ fileId+ ",'brltwpdguema',"+ 0+ ",'" + ipAddress + "')";
					log4j.debug("[mySQL] insert log SQL: " + qStr);
					stmt.executeUpdate(qStr);
					// 更新pid1,pid2,pathIds 值
					if (dirId == 0)// 上传目录为根目录
					{
						pid1 = fileId;
						pid2 = 0;
						pathIds = "" + fileId;
					} else if (pid == 0)// 上级目录为一级目录
					{
						pid1 = dirId;
						pid2 = fileId;
						pathIds = dirId + "," + fileId;
					} else {
						pathIds = pathIds + "," + fileId;
					}
					String upSql = "update jweb_commonfilecache set pid1="+ pid1 + ",pid2=" + pid2 + ",pathids ='" + pathIds+ "',permissionsFileId="+permissionsFileId+" WHERE id = " + fileId;
					if(dirId==0)
					{
						upSql = "update jweb_commonfilecache set pid1="+ pid1 + ",pid2=" + pid2 + ",pathids ='" + pathIds+ "',permissionsFileId="+pid1+" WHERE id = " + fileId;
					}
					log4j.debug("[mySQL] upSql SQL:" + upSql);
					stmt.executeUpdate(upSql);
				}
				// If retention is enabled then create a retention record for
				// the new file/folder

				if (retain == true && isRetentionEnabled()) {

					// Create a retention record for the new file/directory

					Timestamp startDate = new Timestamp(System
							.currentTimeMillis());
					Timestamp endDate = new Timestamp(startDate.getTime()
							+ getRetentionPeriod());

					String rSql = "INSERT INTO " + getRetentionTableName()
							+ " (FileId,StartDate,EndDate) VALUES (" + fileId
							+ ",'" + startDate.toString() + "','"
							+ endDate.toString() + "');";

					// DEBUG

					log4j.debug("[mySQL] Add retention record SQL: " + rSql);
					// Add the retention record for the file/folder

					stmt.executeUpdate(rSql);
				}

				// Check if the new file is a symbolic link

				if (params.isSymbolicLink()) {

					// Create the symbolic link record

					String symSql = "INSERT INTO " + getSymLinksTableName()
							+ " (FileId, SymLink) VALUES (" + fileId + ",'"
							+ params.getSymbolicLinkName() + "');";

					// DEBUG
					log4j.debug("[mySQL] Create symbolic link SQL: " + symSql);

					// Add the symbolic link record

					stmt.executeUpdate(symSql);
				}

				// 插入版本记录(staus=4，正在编辑)
				String insertSql = "insert into "
						+ FileRevisionTableName
						+ " (fileId,md5,size,lastModified,userId,revision,revisionCount,rollPath,status) values ("+ fileId + ",'md51',0,"+ nowDate.getTime() + "," + userId + "," + revision+ "," + revisionCount + ",'" + rootPath + "',4)";
				log4j.debug("[mySQL] insertSql SQL:" + insertSql);
				stmt.executeUpdate(insertSql);
				log4j.debug("[mySQL] Created file name=" + fname + ", dirId="+ dirId + ", fileId=" + fileId);
			}
			conn.commit();
		} catch (SQLException ex) {
			try {
				if (conn != null) conn.rollback();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			// Check for a duplicate key error, another client may have created
			// the file

			if (ex.getErrorCode() == ErrorDuplicateEntry) {

				// Flag that a duplicate key error occurred, we can return the
				// previously allocated file id

				duplicateKey = true;
			} else {

				// Rethrow the exception
				log4j.error(ex);
				throw new DBException(ex.getMessage());
			}
		} catch (DBException ex) {

			// DEBUG

			log4j.error("[mySQL] Create file record error " + ex.getMessage());

			// Rethrow the exception

			throw new DBException(ex.getMessage());
		} finally {

			// Close the prepared statement

			if (pstmt != null) {
				try {
					pstmt.close();
				} catch (Exception ex) {
				}
			}

			// Close the query statement

			if (stmt != null) {
				try {
					stmt.close();
				} catch (Exception ex) {
				}
			}

			// Release the database connection

			if (conn != null) {
				try {
					conn.setAutoCommit(true);
				} catch (SQLException e) {
					e.printStackTrace();
				}
				releaseConnection(conn);
			}
		}

		// If a duplicate key error occurred get the previously allocated file
		// id

		if (duplicateKey == true) {

			// Get the previously allocated file id for the file record

			fileId = getFileId(dirId, fname, false, true, userId, shareName,
					userName);

			// DEBUG
			log4j.info("[mySQL] Duplicate key error, lookup file id, dirId="+ dirId + ", fname=" + fname + ", fid=" + fileId);
		}

		// Return the allocated file id

		return fileId;
	}

	/**
	 * Create a stream record for a new file stream
	 * 
	 * @param sname
	 *            String
	 * @param fid
	 *            int
	 * @return int
	 * @exception DBException
	 */
	public int createStreamRecord(String sname, int fid) throws DBException {

		// Create a new file stream attached to the specified file
		log4j.debug("DBI#createStreamRecord ; sname:" + sname + " ,fid:" + fid);
		Connection conn = null;
		PreparedStatement stmt = null;
		Statement stmt2 = null;

		int streamId = -1;

		try {
			// Get a database connection
			conn = getConnection();
			// Get a statement

			long timeNow = System.currentTimeMillis();

			stmt = conn
					.prepareStatement("INSERT INTO "
							+ getStreamsTableName()
							+ "(FileId,StreamName,CreateDate,ModifyDate,AccessDate,StreamSize) VALUES (?,?,?,?,?,?)");
			// stmt =
			// conn.prepareStatement("INSERT INTO jweb_JLANTransQueue (FileId,StreamName,CreateDate,ModifyDate,AccessDate,StreamSize) VALUES (?,?,?,?,?,?)");
			stmt.setInt(1, fid);
			stmt.setString(2, sname);
			stmt.setLong(3, timeNow);
			stmt.setLong(4, timeNow);
			stmt.setLong(5, timeNow);
			stmt.setInt(6, 0);

			// DEBUG
			log4j.debug("[mySQL] Create stream SQL: " + stmt.toString());

			// Create an entry for the new stream

			if (stmt.executeUpdate() > 0) {

				// Get the stream id for the newly created stream

				stmt2 = conn.createStatement();
				ResultSet rs2 = stmt2.executeQuery("SELECT LAST_INSERT_ID();");

				if (rs2.next())
					streamId = rs2.getInt(1);
				rs2.close();
			}
		} catch (Exception ex) {

			// DEBUG

			log4j.error("[mySQL] Create file stream error " + ex.getMessage());

			// Rethrow the exception

			throw new DBException(ex.getMessage());
		} finally {

			// Close the statements

			if (stmt != null) {
				try {
					stmt.close();
				} catch (Exception ex) {
				}
			}

			if (stmt2 != null) {
				try {
					stmt2.close();
				} catch (Exception ex) {
				}
			}

			// Release the database connection

			if (conn != null)
				releaseConnection(conn);
		}

		// Return the allocated stream id

		return streamId;
	}

	/**
	 * Delete a file or folder record
	 * 
	 * @param dirId
	 *            int
	 * @param fid
	 *            int
	 * @param markOnly
	 *            boolean
	 * @exception DBException
	 * @throws DiskOfflineException
	 * @throws AccessDeniedException
	 */
	public synchronized void deleteFileRecord(int dirId, int fid, boolean markOnly,
			String userName, String shareName, String ipAddress)
			throws DBException, AccessDeniedException {

		// Create a new file stream attached to the specified file
		log4j.error("DBI#deleteFileRecord ; dirId:" + dirId + " ,fid:" + fid + " , userName:" + userName + " ,shareName:" + shareName + " ,markOnly:" + markOnly);
		// Delete a file record FROM the database, or mark the file record as
		// deleted
		Connection conn = null;
		Statement stmt = null;
		int userId = 0;
		int roleId = 0;
		int departmentId = 0;
		java.util.Date nowDate = new java.util.Date();
		String FileSysTableName = getFileSysTableName(shareName);

		try {

			UserBean user = this.getUserByUsername(userName);
			if (null != user) {
				userId = user.getId();
				roleId = user.getRoleId();
				departmentId = user.getDepartmentId();
			}

			// Get a connection to the database
			conn = getConnection();
			// Delete the file entry FROM the database
			stmt = conn.createStatement();
			String sql = null;
			
			if(shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_COMMFILE))
			{
				long permissionsFileId = 0;
				// 查询当前目录权限的Id
				String PathSql = "SELECT permissionsFileId FROM jweb_commonfilecache WHERE id =" + fid;
				log4j.debug("[mySQL] PathSql SQL:" + PathSql);
				ResultSet pathRs = stmt.executeQuery(PathSql);
				if (pathRs.next()) {
					permissionsFileId = pathRs.getInt("permissionsFileId");
				}
				if(permissionsFileId<=0)
				{
					PathSql = "SELECT permissionsFileId FROM jweb_commonfilecache WHERE id =" + dirId;
					log4j.debug("[mySQL] PathSql SQL:" + PathSql);
					pathRs = stmt.executeQuery(PathSql);
					if (pathRs.next()) {
						permissionsFileId = pathRs.getInt("permissionsFileId");
					}
				}
				pathRs.close();
				
				boolean isSuperAdmin = false;//资料库用
				boolean isFileAdmin = false;//资料库用
				
				if (roleId == 1) {// 超级管理员权限 直接查询出来
					isSuperAdmin = true;
				}
				else
				{
					// 查询是否为库管理员
					String auditorSql = "SELECT auditorId FROM jweb_commonfilecache WHERE id=(SELECT pid1 FROM jweb_commonfilecache WHERE id=" + fid + ")";
					try {
						log4j.debug("[mySQL] auditorSql  SQL: " + auditorSql);
						ResultSet rs2 = stmt.executeQuery(auditorSql);
						String auditorId = "";
						while (rs2.next()) {
							auditorId = "," + rs2.getString("auditorId") + ",";
						}
						rs2.close();
						if (auditorId.contains("," + userId + ",")) {
							isFileAdmin = true;// 是资料库管理员，有所有权限
						}
						rs2.close();
					} catch (Exception e) {
						log4j.error("公共资料库 查询组、部门失败", e);
					}
				}
				
				if(isSuperAdmin==false && isFileAdmin==false)
				{
					String groupIds = this.getGroupIdsByUserid(userId);
					// 查询部门的parentId
					String departmentPath = this.getDepartmentPathById(departmentId);
					String parentPermissions = "rw";
					if(dirId>0)
					{
						parentPermissions = this.filePermissions(permissionsFileId, userId, groupIds, departmentPath);
					}
					if (parentPermissions.contains("w") == false) {
						log4j.error("ERROR 没有操作权限 ; userId:" + userId + ",dirId:" + dirId + ",fid:" + fid);
						throw new AccessDeniedException("没有操作权限");
					}
				}
			}
			
			conn.setAutoCommit(false);
			log4j.debug("******markOnly = " + markOnly + ", dirId:" + dirId + ",fid:" + fid + ",shareName:" + shareName);
			if (markOnly == true)// 标识性删除，可回收
			{
				sql = "UPDATE " +FileSysTableName+" SET status = -1,lastModified=" + nowDate.getTime() + " WHERE id = " + fid;
				// sql = "UPDATE " + FileSysTableName +
				// " SET status = 4 WHERE id = " + fid; //标识为正在更新中
			} else// 彻底删除，不可回收（暂不做彻底删除）
			{
				// sql = "DELETE FROM " + FileSysTableName + " WHERE id = "
				// + fid;
				sql = "UPDATE " +FileSysTableName+" SET status = -1,lastModified=" + nowDate.getTime()+ " WHERE id = " + fid;
			}

			log4j.debug("[mySQL] Delete file SQL: " + sql);

			// Delete the file/folder, or mark as deleted
			synchronized (this)// 暂时改为同步
			{
				// 正常的删除为，要先插入一条版本记录，这里以后需要改进
				int recCnt = stmt.executeUpdate(sql);
				if (recCnt == 0) {
					sql = "SELECT * FROM " +FileSysTableName+" WHERE id = " + fid;
					log4j.debug("[mySQL] SQL: " + sql);
					ResultSet rs = stmt.executeQuery(sql);
					while (rs.next()) {
						log4j.debug("Found file " + rs.getString("FileName"));
						log4j.error("Failed to delete file record for fid=" + fid);
						throw new DBException("Failed to delete file record for fid="+ fid);
					}
				}
			}
			// DEBUG
			if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_USERFILE)) {
				String sqlLog = "insert into jweb_file_log (user_id,path_id,file_id,file_revision,note,ip,create_date,agent_client,operation,content_length,read_length) values ("
						+ userId
						+ ","
						+ dirId
						+ ","
						+ fid
						+ ","
						+ 1
						+ ",'删除文件','"
						+ ipAddress
						+ "','"
						+ sf.format(nowDate)
						+ "','CIFS','d',"
						+ 0
						+ ","
						+ 0 + ")";
				log4j.debug("[mySQL] sqlLog SQL: " + sqlLog);
				stmt.executeUpdate(sqlLog);
			} else if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_COMMFILE)) {
				String sqlLog = "insert into jweb_commonfile_log (user_id,path_id,file_id,file_revision,note,ip,create_date,agent_client,operation,content_length,read_length) values ("
						+ userId
						+ ","
						+ dirId
						+ ","
						+ fid
						+ ","
						+ 1
						+ ",'删除文件','"
						+ ipAddress
						+ "','"
						+ sf.format(nowDate)
						+ "','CIFS','d',"
						+ 0
						+ ","
						+ 0 + ")";
				log4j.debug("[mySQL] sqlLog SQL: " + sqlLog);
				stmt.executeUpdate(sqlLog);
			}
			
			conn.commit();
			
			// Check if retention is enabled

			if (isRetentionEnabled()) {

				// Delete the retention record for the file

				sql = "DELETE FROM " + getRetentionTableName()
						+ " WHERE id = " + fid;

				// DEBUG

				log4j.debug("[mySQL] Delete retention SQL: " + sql);

				// Delete the file/folder retention record

				stmt.executeUpdate(sql);
			}
		} catch (SQLException ex) {
			
			try {
				if (conn != null) conn.rollback();
			} catch (SQLException e) {
				e.printStackTrace();
			}

			// DEBUG

			log4j.error("[mySQL] Delete file error " + ex.getMessage());

			// Rethrow the exception

			throw new DBException(ex.getMessage());
		} finally {

			// Close the statement

			if (stmt != null) {
				try {
					stmt.close();
				} catch (Exception ex) {
				}
			}

			// Release the database connection

			if (conn != null) {
				try {
					conn.setAutoCommit(true);
				} catch (SQLException e) {
					e.printStackTrace();
				}
				releaseConnection(conn);
			}
		}
	}

	/**
	 * Delete a file stream record
	 * 
	 * @param fid
	 *            int
	 * @param stid
	 *            int
	 * @param markOnly
	 *            boolean
	 * @exception DBException
	 */
	public void deleteStreamRecord(int fid, int stid, boolean markOnly)
			throws DBException {

		// Delete a file stream FROM the database, or mark the stream as deleted
		log4j.debug("DBI#deleteStreamRecord ; fid:" + fid + " , stid:" + stid + " ,markOnly:" + markOnly);
		Connection conn = null;
		Statement stmt = null;

		try {

			// Get a database connection

			conn = getConnection();

			// Get a statement

			stmt = conn.createStatement();
			String sql = "DELETE FROM " + getStreamsTableName()
					+ " WHERE FileId = " + fid + " AND StreamId = " + stid;

			// DEBUG
			log4j.debug("[mySQL] Delete stream SQL: " + sql);

			// Delete the stream record

			stmt.executeUpdate(sql);
		} catch (Exception ex) {

			// DEBUG

			log4j.error("[mySQL] Delete stream error: " + ex.getMessage());

			// Rethrow the exception

			throw new DBException(ex.getMessage());
		} finally {

			// Close the statement

			if (stmt != null) {
				try {
					stmt.close();
				} catch (Exception ex) {
				}
			}

			// Release the database connection

			if (conn != null)
				releaseConnection(conn);
		}
	}

	/**
	 * Set file information for a file or folder
	 * 
	 * @param dirId
	 *            int
	 * @param fid
	 *            int
	 * @param finfo
	 *            FileInfo
	 * @exception DBException
	 */
	public void setFileInformation(int dirId, int fid, FileInfo finfo,String shareName)
			throws DBException {

		// Set file information fields
		log4j.debug("DBI#setFileInformation ; fid:" + fid + " , dirId:" + dirId
				+ " ,finfo:" + finfo);
		Connection conn = null;
		Statement stmt = null;
		java.util.Date nowDate = new java.util.Date();
		try {

			// Get a connection to the database
			
			conn = getConnection();
			stmt = conn.createStatement();
			// Build the SQL statement to update the file information settings
//			String FileSysTableName = getFileSysTableName(shareName);

			StringBuffer sql = new StringBuffer(256);
			sql.append("UPDATE ");
			sql.append(TABLE_WAS_TEMPFILE);
			sql.append(" SET ");

			// Check if the file size should be set
			if (finfo.hasSetFlag(FileInfo.SetFileSize)) {
				// Update the file size

				sql.append("size = ");
				sql.append(finfo.getSize());
			}
			// Check if the modify date/time has been set

			if (finfo.hasSetFlag(FileInfo.SetModifyDate)) {
				// Add the SQL to update the modify date/time
				if (finfo.hasSetFlag(FileInfo.SetFileSize)) {
					sql.append(",");
				}
				sql.append(" last_modified = ").append(nowDate.getTime());
			}

			// Trim any trailing comma

			if (sql.charAt(sql.length() - 1) == ',')
				sql.setLength(sql.length() - 1);
			// Complete the SQL request string

			sql.append(" WHERE id = ");
			sql.append(fid);
			sql.append(";");

			// Create the SQL statement

			if (finfo.hasSetFlag(FileInfo.SetFileSize)
					|| finfo.hasSetFlag(FileInfo.SetModifyDate)) {
				// DEBUG
				log4j.debug("[mySQL] Set file info SQL: " + sql.toString());
				stmt.executeUpdate(sql.toString());
			}
		} catch (SQLException ex) {

			// DEBUG

			log4j.error("[mySQL] Set file information error " + ex.getMessage());

			// Rethrow the exception

			throw new DBException(ex.getMessage());
		} finally {

			// Close the statement

			if (stmt != null) {
				try {
					stmt.close();
				} catch (Exception ex) {
				}
			}

			// Release the database connection

			if (conn != null)
				releaseConnection(conn);
		}
	}

	/**
	 * Set information for a file stream
	 * 
	 * @param dirId
	 *            int
	 * @param fid
	 *            int
	 * @param stid
	 *            int
	 * @param sinfo
	 *            StreamInfo
	 * @exception DBException
	 */
	public void setStreamInformation(int dirId, int fid, int stid,
			StreamInfo sinfo) throws DBException {

		// Set file stream information fields
		log4j.debug("DBI#setStreamInformation ; fid:" + fid + " , dirId:" + dirId + " ,sinfo:" + sinfo);
		Connection conn = null;
		Statement stmt = null;

		try {

			// Get a connection to the database

			conn = getConnection();

			// Build the SQL statement to update the file information settings

			StringBuffer sql = new StringBuffer(256);
			sql.append("UPDATE ");
			sql.append(getStreamsTableName());
			sql.append(" SET ");

			// Check if the access date/time has been set

			if (sinfo.hasSetFlag(StreamInfo.SetAccessDate)) {

				// Add the SQL to update the access date/time

				sql.append(" AccessDate = ");
				sql.append(sinfo.getAccessDateTime());
				sql.append(",");
			}

			// Check if the modify date/time has been set

			if (sinfo.hasSetFlag(StreamInfo.SetModifyDate)) {

				// Add the SQL to update the modify date/time

				sql.append(" ModifyDate = ");
				sql.append(sinfo.getModifyDateTime());
				sql.append(",");
			}

			// Check if the stream size should be updated

			if (sinfo.hasSetFlag(StreamInfo.SetStreamSize)) {

				// Update the stream size

				sql.append(" StreamSize = ");
				sql.append(sinfo.getSize());
			}

			// Trim any trailing comma

			if (sql.charAt(sql.length() - 1) == ',')
				sql.setLength(sql.length() - 1);

			// Complete the SQL request string

			sql.append(" WHERE FileId = ");
			sql.append(fid);
			sql.append(" AND StreamId = ");
			sql.append(stid);
			sql.append(";");

			// DEBUG
			log4j.debug("[mySQL] Set stream info SQL: " + sql);

			// Create the SQL statement

			stmt = conn.createStatement();
			stmt.executeUpdate(sql.toString());
		} catch (SQLException ex) {

			// DEBUG

			log4j.error("[mySQL] Set stream information error "
					+ ex.getMessage());

			// Rethrow the exception

			throw new DBException(ex.getMessage());
		} finally {

			// Close the statement

			if (stmt != null) {
				try {
					stmt.close();
				} catch (Exception ex) {
				}
			}

			// Release the database connection

			if (conn != null)
				releaseConnection(conn);
		}
	}

	/**
	 * Get the id for a file/folder, or -1 if the file/folder does not exist.
	 * 获取文件及文件夹的ID 非常重要的方法 下面的getFileInformation同样非常重要
	 * 
	 * @param dirId
	 *            int
	 * @param fname
	 *            String
	 * @param dirOnly
	 *            boolean
	 * @param caseLess
	 *            boolean
	 * @return int
	 * @throws DBException
	 */
	public synchronized int getFileId(int dirId, String fname, boolean dirOnly,
			boolean caseLess, int userId, String shareName, String userName)
			throws DBException {

		// Get the file id for a file/folder
//		log4j.debug("DBI#getFileId ,dirId:" + dirId + ", fname:" + fname + ", userId:" + userId + " , shareName:" + shareName);
		int fileId = -1;
		//虚拟路径，直接返回存在
		if(dirId<=0 && (fname.equalsIgnoreCase(DBUtil.SHARENAME_WAS_USERFILE)||fname.equalsIgnoreCase(DBUtil.SHARENAME_WAS_COMMFILE)
				||fname.equalsIgnoreCase(DBUtil.SHARENAME_WAS_RECIVEFILE)))
		{
			return 0;// 直接返回0
		}
		
		Connection conn = null;
		Statement stmt = null;
		try {

			// Get a connection to the database, create a statement for the
			// database lookup
			conn = getConnection();
			stmt = conn.createStatement();

			if (fname.endsWith(".tmp")) {
				// Build the SQL for the file lookup

				String FileSysTableName = getFileSysTableName(shareName);
				StringBuffer sql = new StringBuffer();
				sql.append("SELECT id,lastModified,status FROM ");
				sql.append(FileSysTableName);
				sql.append(" WHERE status>=0 "); // status>=0 注意。后面要替换为 status=-1 查询
//				if (fname.equalsIgnoreCase(userName + DBUtil.SPECIAL_CHAR)) {
				if (fname.equalsIgnoreCase(userName + DBUtil.SPECIAL_CHAR) || (fname.equalsIgnoreCase(shareName) && dirId <= 0)) {
					sql.append(" AND pid = 0");
				} else {
					sql.append(" AND pid = ").append(dirId);
				}
				
//				if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_RECIVEFILE) || shareName.equalsIgnoreCase(DBUtil.SHARENAME_COMMFILE) || shareName.equalsIgnoreCase(DBUtil.SHARENAME_COMMFILE_ALIAS)) {
	//
//				} else {
//					sql.append(" AND userId=").append(userId);
//				}
				//修改增加userid查询条件的判断
				if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_USERFILE))
				{
					sql.append(" AND userId = ").append(userId);//我的文件才需要加userId,测试发现　shareName　可能会为空
				}
				// Check if the search is for a directory only
				if (dirOnly == true) {
					// Search for a directory record
					// isFile:1代表TRUE文件,0代表FALSE目录
					sql.append(" AND isFile = 0 ");
				}

				// Check if the file name search should be caseles
				if (caseLess == true) {
					// Perform a caseless search
					sql.append(" AND lower(name) = LOWER('");
					sql.append(checkNameForSpecialChars(fname));
					sql.append("')");
				} else {
					sql.append(" AND lower(name) = LOWER('");
					sql.append(checkNameForSpecialChars(fname));
					sql.append("')");
				}
				sql.append(" ORDER BY revision DESC,id ASC");
				// DEBUG
				// sql.append(" ORDER BY revision DESC ");//不考虑目录重复的问题(目录重复为非正常文件)不排序，提高效率
				log4j.debug("[mySQL] Get file id SQL: " + sql.toString());

				// Run the database search

				ResultSet rs = stmt.executeQuery(sql.toString());
				// Check if a file record exists
				if (rs.next()) {
					// Get the unique file id for the file or folder
					fileId = rs.getInt("id");
				} else if(!fname.startsWith("~$")) {
					if (!fname.equalsIgnoreCase("desktop.ini")
							&& !fname.equalsIgnoreCase("Thumbs.db")
							&& !fname.equalsIgnoreCase("folder.jpg")
							&& !fname.equalsIgnoreCase("folder.gif")
							&& !fname.equalsIgnoreCase(".lfs")) {
						// 取3秒内删除的同名的文件的id
						log4j.debug("[mySQL] del sql SQL: " + sql.toString().replace("status>=0", "status>=-1"));
						rs = stmt.executeQuery(sql.toString().replace("status>=0", "status>=-1"));// 查询删除的
						if (rs.next()) {
							long lastTime = rs.getLong("lastModified");
							int status = rs.getInt("status");
							if (status >= 0 || System.currentTimeMillis() - lastTime <= delFileKeepTime) {
								fileId = rs.getInt("id");// 删除3秒内的文件，表示存在,直接返回id
							} else {
								log4j.warn("DBI#getFileId  fname:" + fname + " 已经存在一个status=-1 的 ，但存在时间已经超过: " + (System.currentTimeMillis() - lastTime) + "ms");
							}
						}
					}
				}
				// Close the result set
				rs.close();
				if (fileId <= 0) {
					
					log4j.warn("not fond fileId ; fileId:" + fileId + " , dirId:" + dirId + ", fname:" + fname + ", userId:" + userId + " , shareName:" + shareName + " sql " + sql);
				}
			
			} else {
				// Build the SQL for the file lookup

				StringBuffer sql = new StringBuffer();
				sql.append("SELECT id,last_modified,status FROM ");
				sql.append(TABLE_WAS_TEMPFILE);
				sql.append(" WHERE status>=0 "); // status>=0 注意。后面要替换为 status=-1 查询
				if (fname.equalsIgnoreCase(DBUtil.SHARENAME_WAS)) {
					sql.append(" AND pid = 0");
				} else {
					sql.append(" AND pid = ").append(dirId);
				}
				// Check if the search is for a directory only
				if (dirOnly == true) {
					// Search for a directory record
					// isFile:1代表TRUE文件,0代表FALSE目录
					sql.append(" AND isFile = 0 ");
				}

				// Check if the file name search should be caseles
				if (caseLess == true) {
					// Perform a caseless search
					sql.append(" AND lower(fname) = LOWER('");
					sql.append(checkNameForSpecialChars(fname));
					sql.append("')");
				} else {
					sql.append(" AND lower(fname) = LOWER('");
					sql.append(checkNameForSpecialChars(fname));
					sql.append("')");
				}
				// DEBUG
				// sql.append(" ORDER BY id DESC ");//不考虑目录重复的问题(目录重复为非正常文件)不排序，提高效率
				log4j.debug("[mySQL] Get file id SQL: " + sql.toString());

				
				// Run the database search

				ResultSet rs = stmt.executeQuery(sql.toString());
				// Check if a file record exists
				if (rs.next()) {
					// Get the unique file id for the file or folder
					fileId = rs.getInt("id");
				} else {
					if (!fname.equalsIgnoreCase("desktop.ini")
							&& !fname.equalsIgnoreCase("Thumbs.db")
							&& !fname.equalsIgnoreCase("folder.jpg")
							&& !fname.equalsIgnoreCase("folder.gif")
							&& !fname.equalsIgnoreCase(".svn")
							&& !fname.equalsIgnoreCase(".lfs")) {
						// 取3秒内删除的同名的文件的id
						log4j.debug("[mySQL] del sql SQL: " + sql.toString().replace("status>=0", "status>=-1"));
						rs = stmt.executeQuery(sql.toString().replace("status>=0", "status>=-1"));// 查询删除的
						if (rs.next()) {
							long lastTime = rs.getLong("last_modified");
							int status = rs.getInt("status");
							if (status >= 0 || System.currentTimeMillis() - lastTime <= delFileKeepTime) {
								fileId = rs.getInt("id");// 删除3秒内的文件，表示存在,直接返回id
							} else {
								log4j.warn("fname:" + fname + " 已经存在一个status=-1 的 ，但存在时间已经超过: " + (System.currentTimeMillis() - lastTime) + "ms");
							}
						}
					}
				}
				// Close the result set
				rs.close();
				if (fileId <= 0) {
					log4j.warn("not fond fileId ; fileId:" + fileId + " , dirId:" + dirId + ", fname:" + fname + ", userId:" + userId + " , shareName:" + shareName);
				}
			}
			
			
		} catch (Exception ex) {
			// DEBUG
			log4j.error("[mySQL] Get file id error ", ex);
			// Rethrow the exception
			throw new DBException(ex.getMessage());
		} finally {
			// Close the statement
			if (stmt != null) {
				try {
					stmt.close();
				} catch (Exception ex) {
				}
			}
			// Release the database connection
			if (conn != null)
				releaseConnection(conn);
		}

		// Return the file id, or -1 if not found
		return fileId;
	}

	/**
	 * Get information for a file or folder
	 * 
	 * @param dirId
	 *            int
	 * @param fid
	 *            int
	 * @param infoLevel
	 *            int
	 * @return FileInfo
	 * @exception DBException
	 */
	public synchronized DBFileInfo getFileInformation(int dirId, int fid, int infoLevel,
			int userId, String shareName, String userName) throws DBException {

		// Create a SQL SELECT for the required file information
//		log4j.debug("DBI#getFileInformation ,dirId:" + dirId + ", fid:" + fid + ", userId:" + userId + " , shareName:" + shareName);

		String FileSysTableName = getFileSysTableName(shareName);
		
		if (fid == 0) {
			// Load the file record
			DBFileInfo finfo = new DBFileInfo();
			if(StringUtil.isEmpty(shareName)||"*".equals(shareName))
			{
				finfo.setFileName(userName);	
			}else{
				finfo.setFileName(shareName);
			}		
			finfo.setDirectoryId(dirId);
			finfo.setSize(0);
			finfo.setFileId(fid);
			finfo.setFileType(FileType.Directory);
			int attr = 0;
			attr += FileAttribute.Directory;
			finfo.setFileAttributes(attr);
			// finfo.setCreationDateTime(System.currentTimeMillis());
			// finfo.setModifyDateTime(System.currentTimeMillis());
			finfo.setUid(userId);
			return finfo;//
		}

		StringBuffer sql = new StringBuffer();
		sql.append("SELECT ");
		// Select fields according to the required information level
		switch (infoLevel) {
		// File name only
		case DBInterface.FileNameOnly:
			sql.append("fname");
			break;

		// File ids and name
		case DBInterface.FileIds:
			sql.append("fname,id,pid");
			break;

		// All file information
		case DBInterface.FileAll:
//			if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_RECIVEFILE)) {
//				sql.append("id,pid,fname,size,create_date,last_modified,isFile,user_id");
//			}
//			else if(shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_COMMFILE)||shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_COMMFILE))
//			{
//				sql.append("id,pid,name,size,add_time,lastModified,isFile,userId,permissionsFileId,islock,lockedByUser");
//				//sql.append("id,pid,fname,size,create_date,last_modified,isFile,user_id,operate");
//			}
//			else {
				sql.append("id,pid,fname,size,create_date,last_modified,isFile,user_id,operate");
//			}
			break;

		// Unknown information level
		default:
			throw new DBException("Invalid information level, " + infoLevel);
		}

		sql.append(" FROM ");
		sql.append(TABLE_WAS_TEMPFILE);
		sql.append(" WHERE id = ");
		sql.append(fid);

		// Load the file record
		DBFileInfo finfo = null;
		Connection conn = null;
		Statement stmt = null;
		try {

			// Get a connection to the database
			conn = getConnection();
			stmt = conn.createStatement();

			// DEBUG
			log4j.debug("[mySQL] Get file info SQL: " + sql.toString());
			// Load the file record
			ResultSet rs = stmt.executeQuery(sql.toString());
			if (rs != null && rs.next()) {

				// Create the file informaiton object
				finfo = new DBFileInfo();
				finfo.setFileId(fid);
				finfo.setFileAttributes(0);

				// Load the file information

				switch (infoLevel) {

				// File name only

				case DBInterface.FileNameOnly:
					finfo.setFileName(rs.getString("fname"));
					break;

				// File ids and name

				case DBInterface.FileIds:
					finfo.setFileName(rs.getString("fname"));
					finfo.setDirectoryId(rs.getInt("pid"));
					break;

				// All file information

				case DBInterface.FileAll:
					finfo.setFileName(rs.getString("fname"));
					finfo.setSize(rs.getLong("size"));
					finfo.setAllocationSize(finfo.getSize());
					finfo.setDirectoryId(rs.getInt("pid"));

					// Load the various file date/times
					Timestamp ts = rs.getTimestamp("create_date");
					if(null != ts &&  ts.getTime()>0)
					{
						finfo.setCreationDateTime(ts.getTime());
					}
					else
					{
						rs.getLong("last_modified");
					}
					finfo.setModifyDateTime(rs.getLong("last_modified"));
					// finfo.setAccessDateTime(rs.getLong("AccessDate"));
					// finfo.setChangeDateTime(rs.getLong("ChangeDate"));

					// Build the file attributes flags

					int attr = 0;
					String operate = rs.getString("operate");
					 if (operate != null && operate.equals("r")){
						 attr += FileAttribute.ReadOnly;
					 }

					// if ( rs.getBoolean("SystemFile") == true)
					// attr += FileAttribute.System;

					// if ( rs.getBoolean("Hidden") == true)
					// attr += FileAttribute.Hidden;

					if (rs.getBoolean("isFile") == true) {
						finfo.setFileType(FileType.RegularFile);
					} else
						attr += FileAttribute.Directory;
					finfo.setFileType(FileType.Directory);

					// if ( rs.getBoolean("Archived") == true)
					// attr += FileAttribute.Archive;

					

					// finfo.setGid(rs.getInt("Gid"));
					finfo.setUid(rs.getInt("user_id"));

					/*
					 * 暂时不考虑权限问题
					 */
//					if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_RECIVEFILE)) {
//						// 共享文件权限
//						String permissions = rs.getString("permissions");
//						if (StringUtils.isNotEmpty(permissions)
//								&& permissions.indexOf("w") > -1) {
//							// 可读写
//						} else {
//							// 只读权限
//							attr += FileAttribute.ReadOnly;
//						}
//					}
//					else if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_COMMFILE)) {
//						// 公共资料库权限设值
//						UserBean user = this.getUserByUsername(userName);
//						boolean isSuperAdmin = false;
//						boolean isFileAdmin = false;
//						boolean islock = rs.getBoolean("islock");
//						int lockedByUser = rs.getInt("lockedByUser");
//						if(islock && lockedByUser!=userId)
//						{
//							//被锁定（且非自己锁定）的文件只读
//							attr += FileAttribute.ReadOnly;
//						}
//						if(null !=user && user.getRoleId()==1)
//						{
//							isSuperAdmin = true;
//						}
//						else
//						{
//							// 查询是否为库管理员
//							if (dirId > 0) {
//								String auditorSql = "SELECT auditorId FROM jweb_commonfilecache WHERE id=(SELECT pid1 FROM jweb_commonfilecache WHERE id=" + fid + ")";
//								try {
//									log4j.debug("[mySQL] auditorSql  SQL: " + auditorSql);
//									ResultSet rs2 = stmt.executeQuery(auditorSql);
//									String auditorId = "";
//									while (rs2.next()) {
//										auditorId = "," + rs2.getString("auditorId") + ",";
//									}
//									rs2.close();
//									if (auditorId.contains("," + userId + ",")) {
//										isFileAdmin = true;// 是资料库管理员，有所有权限
//									}
//								} catch (Exception e) {
//									log4j.error("公共资料库 查询组、部门失败", e);
//								}
//							}
//						}
//						if(isSuperAdmin==false && isFileAdmin==false)
//						{
//							long parentPermFileId = 0;
//							String groupIds = this.getGroupIdsByUserid(userId);
//							String departmentPath = this.getDepartmentPathById(user.getDepartmentId());	
//							
//							String PathSql = "SELECT permissionsFileId FROM jweb_commonfilecache WHERE id =" + fid;
//							log4j.debug("[mySQL] PathSql SQL:" + PathSql);
//							ResultSet pathRs = stmt.executeQuery(PathSql);
//							if (pathRs.next()) {
//								parentPermFileId = pathRs.getInt("permissionsFileId");
//							}
//							if(parentPermFileId<=0)
//							{
//								PathSql = "SELECT permissionsFileId FROM jweb_commonfilecache WHERE id =" + dirId;
//								log4j.debug("[mySQL] PathSql SQL:" + PathSql);
//								pathRs = stmt.executeQuery(PathSql);
//								if (pathRs.next()) {
//									parentPermFileId = pathRs.getInt("permissionsFileId");
//								}
//							}
//							pathRs.close();
//							if(dirId>0)
//							{
//								String parentPermissions = this.filePermissions(parentPermFileId, userId, groupIds, departmentPath);//重新获得权限
//								if(parentPermissions.contains("w")==false)
//								{
//									// 只读权限
//									attr += FileAttribute.ReadOnly;
//								}
//							}
//						}
//					}
					finfo.setFileAttributes(attr);
					// Get the group/owner id
					// finfo.setMode(rs.getInt("Mode"));

					// Check if the file is a symbolic link

					// if ( rs.getBoolean("IsSymLink") == true)
					// finfo.setFileType(FileType.SymbolicLink);
					// break;
				}
			} else {
				String sql2="SELECT id,pid,name,size,add_time,lastModified,isFile,userId FROM " +FileSysTableName+" where id =" +fid;
//				sql.append("SELECT id,pid,name,size,add_time,lastModified,isFile,userId FROM jweb_commonfilecache where id =" +fid);
				rs = stmt.executeQuery(sql2);
				if (rs != null && rs.next()) 
				{
					// Create the file informaiton object
					finfo = new DBFileInfo();
					finfo.setFileId(fid);
					finfo.setFileAttributes(0);

					// Load the file information

					switch (infoLevel) {

					// File name only

					case DBInterface.FileNameOnly:
						finfo.setFileName(rs.getString("name"));
						break;

					// File ids and name

					case DBInterface.FileIds:
						finfo.setFileName(rs.getString("name"));
						finfo.setDirectoryId(rs.getInt("pid"));
						break;

					// All file information

					case DBInterface.FileAll:
						finfo.setFileName(rs.getString("name"));
						finfo.setSize(rs.getLong("size"));
						finfo.setAllocationSize(finfo.getSize());
						finfo.setDirectoryId(rs.getInt("pid"));

						// Load the various file date/times
						Timestamp ts = rs.getTimestamp("add_time");
						finfo.setModifyDateTime(rs.getLong("lastModified"));
						if(null != ts &&  ts.getTime()>0)
						{
							finfo.setCreationDateTime(ts.getTime());
							finfo.setModifyDateTime(ts.getTime());
							finfo.setChangeDateTime(ts.getTime());
						}
						else
						{
							finfo.setCreationDateTime(rs.getLong("lastModified"));
							finfo.setChangeDateTime(rs.getLong("lastModified"));
						}
						// finfo.setAccessDateTime(rs.getLong("AccessDate"));
						// finfo.setChangeDateTime(rs.getLong("ChangeDate"));

						// Build the file attributes flags

						int attr = 0;
						
						// if ( rs.getBoolean("ReadOnly") == true)
						// attr += FileAttribute.ReadOnly;

						// if ( rs.getBoolean("SystemFile") == true)
						// attr += FileAttribute.System;

						// if ( rs.getBoolean("Hidden") == true)
						// attr += FileAttribute.Hidden;

						if (rs.getBoolean("isFile") == true) {
							finfo.setFileType(FileType.RegularFile);
						} else
							attr += FileAttribute.Directory;
						finfo.setFileType(FileType.Directory);

						// if ( rs.getBoolean("Archived") == true)
						// attr += FileAttribute.Archive;

						

						// finfo.setGid(rs.getInt("Gid"));
						finfo.setUid(rs.getInt("userId"));
					}
				}
				else
				{
					log4j.debug("DB#getFileInformation no record: fid:" + fid);
				}
			}
			rs.close();
		} catch (Exception ex) {

			// DEBUG
			log4j.error("[mySQL] Get file information error "+ ex.getMessage());

			// Rethrow the exception

			throw new DBException(ex.getMessage());
		} finally {

			// Close the statement
			if (stmt != null) {
				try {
					stmt.close();
				} catch (Exception ex) {
				}
			}

			// Release the database connection
			if (conn != null)
				releaseConnection(conn);
		}

		// Return the file information
		return finfo;
	}

	/**
	 * Get information for a file stream
	 * 
	 * @param fid
	 *            int
	 * @param stid
	 *            int
	 * @param infoLevel
	 *            int
	 * @return StreamInfo
	 * @exception DBException
	 */
	public StreamInfo getStreamInformation(int fid, int stid, int infoLevel)
			throws DBException {

		// Create a SQL SELECT for the required stream information
		log4j.debug("DBI#getStreamInformation ; fid:" + fid + ", stid:" + stid
				+ " , infoLevel:" + infoLevel);
		StringBuffer sql = new StringBuffer(128);

		sql.append("SELECT ");

		// Select fields according to the required information level

		switch (infoLevel) {

		// Stream name only.
		//
		// Also used if ids are requested as we already have the ids

		case DBInterface.StreamNameOnly:
		case DBInterface.StreamIds:
			sql.append("StreamName");
			break;

		// All file information

		case DBInterface.StreamAll:
			sql.append("*");
			break;

		// Unknown information level

		default:
			throw new DBException("Invalid information level, " + infoLevel);
		}

		sql.append(" FROM ");
		sql.append(getStreamsTableName());
		sql.append(" WHERE FileId = ");
		sql.append(fid);
		sql.append(" AND StreamId = ");
		sql.append(stid);

		// Load the stream record

		Connection conn = null;
		Statement stmt = null;

		StreamInfo sinfo = null;

		try {

			// Get a connection to the database

			conn = getConnection();
			stmt = conn.createStatement();
			// DEBUG
			log4j.debug("[mySQL] Get stream info SQL: " + sql.toString());
			// Load the stream record

			ResultSet rs = stmt.executeQuery(sql.toString());

			if (rs != null && rs.next()) {

				// Create the stream informaiton object

				sinfo = new StreamInfo("", fid, stid);

				// Load the file information

				switch (infoLevel) {

				// Stream name only (or name and ids)

				case DBInterface.StreamNameOnly:
				case DBInterface.StreamIds:
					sinfo.setName(rs.getString("StreamName"));
					break;

				// All stream information

				case DBInterface.FileAll:
					sinfo.setName(rs.getString("StreamName"));
					sinfo.setSize(rs.getLong("StreamSize"));

					// Load the various file date/times

					sinfo.setCreationDateTime(rs.getLong("CreateDate"));
					sinfo.setModifyDateTime(rs.getLong("ModifyDate"));
					sinfo.setAccessDateTime(rs.getLong("AccessDate"));
					break;
				}
			}
		} catch (Exception ex) {

			// DEBUG

			log4j.error("[mySQL] Get stream information error "
					+ ex.getMessage());

			// Rethrow the exception

			throw new DBException(ex.getMessage());
		} finally {

			// Close the statement

			if (stmt != null) {
				try {
					stmt.close();
				} catch (Exception ex) {
				}
			}

			// Release the database connection

			if (conn != null)
				releaseConnection(conn);
		}

		// Return the stream information

		return sinfo;
	}

	/**
	 * Return the list of streams for the specified file
	 * 
	 * @param fid
	 *            int
	 * @param infoLevel
	 *            int
	 * @return StreamInfoList
	 * @exception DBException
	 */
	public StreamInfoList getStreamsList(int fid, int infoLevel)
			throws DBException {

		// Create a SQL SELECT for the required stream information
		log4j.debug("DBI#getStreamsList; fid:" + fid + ", infoLevel:"
				+ infoLevel);
		StringBuffer sql = new StringBuffer(128);

		sql.append("SELECT ");

		// Select fields according to the required information level

		switch (infoLevel) {

		// Stream name only.

		case DBInterface.StreamNameOnly:
			sql.append("StreamName");
			break;

		// Stream name and ids

		case DBInterface.StreamIds:
			sql.append("StreamName,FileId,StreamId");
			break;

		// All file information

		case DBInterface.StreamAll:
			sql.append("*");
			break;

		// Unknown information level

		default:
			throw new DBException("Invalid information level, " + infoLevel);
		}

		sql.append(" FROM ");
		sql.append(getStreamsTableName());
		sql.append(" WHERE FileId = ");
		sql.append(fid);

		// Load the stream record

		Connection conn = null;
		Statement stmt = null;

		StreamInfoList sList = null;

		try {

			// Get a connection to the database

			conn = getConnection();
			stmt = conn.createStatement();
			// DEBUG
			log4j.debug("[mySQL] Get stream list SQL: " + sql.toString());
			// Load the stream records

			ResultSet rs = stmt.executeQuery(sql.toString());
			sList = new StreamInfoList();

			while (rs.next()) {

				// Create the stream informaiton object

				StreamInfo sinfo = new StreamInfo("", fid, -1);

				// Load the file information

				switch (infoLevel) {

				// Stream name only

				case DBInterface.StreamNameOnly:
					sinfo.setName(rs.getString("StreamName"));
					break;

				// Stream name and id

				case DBInterface.StreamIds:
					sinfo.setName(rs.getString("StreamName"));
					sinfo.setStreamId(rs.getInt("StreamId"));
					break;

				// All stream information

				case DBInterface.FileAll:
					sinfo.setName(rs.getString("StreamName"));
					sinfo.setStreamId(rs.getInt("StreamId"));
					sinfo.setSize(rs.getLong("StreamSize"));

					// Load the various file date/times

					sinfo.setCreationDateTime(rs.getLong("CreateDate"));
					sinfo.setModifyDateTime(rs.getLong("ModifyDate"));
					sinfo.setAccessDateTime(rs.getLong("AccessDate"));
					break;
				}

				// Add the stream information to the list

				sList.addStream(sinfo);
			}
		} catch (Exception ex) {

			// DEBUG

			log4j.error("[mySQL] Get stream list error " + ex.getMessage());

			// Rethrow the exception

			throw new DBException(ex.getMessage());
		} finally {

			// Close the statement

			if (stmt != null) {
				try {
					stmt.close();
				} catch (Exception ex) {
				}
			}

			// Release the database connection

			if (conn != null)
				releaseConnection(conn);
		}

		// Return the streams list

		return sList;
	}

	/**
	 * Rename a file or folder, may also change the parent directory.
	 * 
	 * @param dirId
	 *            int
	 * @param fid
	 *            int
	 * @param newName
	 *            String
	 * @param newDir
	 *            int
	 * @exception DBException
	 * @exception FileNotFoundException
	 */
	
	public synchronized int renameFileRecord(int dirId, int fid, String newName, int newDir,String shareName, String ipAddress)
		throws DBException, FileNotFoundException {
			// Rename a file/folder
		Connection conn = null;
		Statement stmt = null;
		log4j.error("DBI#renameFileRecord ; time:" + System.currentTimeMillis()
				+ ", fileId:" + fid + ", newName:" + newName + ", newDir:"
				+ newDir);

		long existFid = 0;
		if(newName.contains("_") && newName.length()>=10)
		{
			existFid = Long.parseLong(newName.substring(0, newName.indexOf("_")));
			newName = newName.substring(newName.indexOf("_") +1 );
		}
		String FileSysTableName = getFileSysTableName(shareName);
		String FileRevisionTableName = getRevisionTableName(shareName);
		try {
			synchronized (this)// 暂时改为同步。 避免产生重复文件
			{
				// //因DBI#deleteFileRecord ; DBI#renameFileRecord 时间间隔太短，延时100ms
				// 再执行改名操作
				// Get a connection to the database
				conn = getConnection();
				stmt = conn.createStatement();
				// Update the file record
		
				String findOldSql = "SELECT id,pid,revision,revisionCount,lastModified,status,description,md5 FROM " + FileSysTableName
						+ " WHERE id=" + fid;
				log4j.debug("[mySQL] findOldSql SQL: " + findOldSql);
				ResultSet rRs = stmt.executeQuery(findOldSql);
				int pid = 0;
				int existStatus = 0;
				String description = "";
				String md5 = "";
				if (rRs.next()) {
					pid = rRs.getInt("pid");
					existStatus = rRs.getInt("status");
					description = rRs.getString("description");
					md5 = rRs.getString("md5");
				} else {
					log4j.debug("存在已经删除的记录，但已经超过10秒; fileId:" + rRs.getLong("id"));
				}
				rRs.close();
				

				String findOldSql2 = "SELECT id,pid,revision,revisionCount,lastModified,status FROM " + FileSysTableName
						+ " WHERE id=" + existFid;
				log4j.debug("[mySQL] findOldSql SQL: " + findOldSql2);
				rRs = stmt.executeQuery(findOldSql2);
				long revisionCount = 1;
				if (rRs.next()) {
					revisionCount = rRs.getLong("revisionCount");
				} else {
					log4j.debug("存在已经删除的记录，但已经超过10秒; fileId:" + rRs.getLong("id"));
				}
				
				rRs.close();
				if (existFid > 0 && existFid != fid) {
					
					
					// 改名重复的情况
					revisionCount = revisionCount + 1;
					long revision = 0;// 当前这个文件的版本
					// long size = 0;
					// String md5 = "md5";
					findOldSql = "SELECT revision,size,md5,status FROM "+ FileSysTableName +" WHERE id=" + fid;
					log4j.debug("[mySQL] findOldSql SQL: " + findOldSql);
					rRs = stmt.executeQuery(findOldSql);
					if (rRs.next()) {
						revision = rRs.getLong("revision");
						// size = rRs.getLong("size");
						// md5 = rRs.getString("md5");
					}
					rRs.close();
					
					conn.setAutoCommit(false);
		
					// 已经存在同名的文件，将旧文件作为当前文件的新版本。不插入重新值
		
		//			String rRevisionSql = "UPDATE " + FileRevisionTableName
		//					+ " SET fileId=" + existFid + ",revisionCount=" + revisionCount + ", lastModified = " + System.currentTimeMillis()
		//					+ " WHERE fileId = " + fid;
					String rOldRevisionSql =  "UPDATE " + FileRevisionTableName + " SET fileId = '" + existFid + "',revisionCount=" + revisionCount + ", lastModified = " + System.currentTimeMillis()+ " WHERE fileId = " + fid;
					stmt.executeUpdate(rOldRevisionSql);//改旧的版本文件名
					
//					String rRevisionSql ="INSERT INTO "+FileRevisionTableName+" (fileid,md5,size,lastModified,userId,revision,revisionCount,status,rollPath,storePath)" +
//							" SELECT "+existFid+" AS fileId, md5,size,lastModified,userId,revision,revisionCount, status,rollPath,storePath from "+FileRevisionTableName+" WHERE fileId = " + fid;
					
					String renameFileSql = "UPDATE " + FileSysTableName +" SET status=-2,name = '" + checkNameForSpecialChars(newName)
							+ "',revisionCount= revisionCount + 1,latestFileId=" + existFid + ",lastModified = " + System.currentTimeMillis() + " WHERE id = " + fid;// 改名并删除
					
					String updateFileSql = "";
					if (StringUtil.isEmpty(md5)) {
						updateFileSql = "UPDATE  " + FileSysTableName 
								+ " SET status=0,revision="+ revision 
								+ ",revisionCount= revisionCount+1" 
								+ ",lastModified = " 
								+ System.currentTimeMillis()
								+ " WHERE id = " + existFid;
					} else {
						updateFileSql = "UPDATE  " + FileSysTableName 
						+ " SET status=0,revision=" + revision + ",revisionCount= revisionCount+1" 
						+ ",md5 = '" + md5
						+ "',description = '" + description
						+ "',lastModified = " 
						+ System.currentTimeMillis()
						+ " WHERE id = " + existFid;
					}
					
//					log4j.debug("[mySQL] Rename Revion1  SQL: " + rRevisionSql);
//					if (stmt.executeUpdate(rRevisionSql) == 0) {// 文件版本改名
//						log4j.error("DBI#renameFileRecord  修改新版本对应的旧文件id失败! fid:" + fid + " ,existFid:" + existFid);
//					}
					log4j.debug("[mySQL] Rename SQL: " + renameFileSql.toString());
					// Rename the file/folder
					if (stmt.executeUpdate(renameFileSql) == 0) {// 文件改名
						// Original file not found
						log4j.error("DBI#renameFileRecord 修改当前文件名称失败，FileNotFoundException  fid:" + fid);
						throw new FileNotFoundException("" + fid);
					}
					log4j.debug("[mySQL] updateFileSql SQL: " + updateFileSql);
					if (stmt.executeUpdate(updateFileSql) == 0) {// 文件版本改名
						log4j.error("DBI#renameFileRecord  修改旧文件版本等属性失败! fid:" + fid + " ,existFid:" + existFid);
					}
		
		//			String upSql = "UPDATE " + FileSysTableName + " SET status=0,revisionCount=" + revisionCount + ",lastModified=" + System.currentTimeMillis() + " WHERE status<0 AND id=" + existFid;
		//			log4j.debug("[mySQL] 更新文件状态 SQL:" + upSql);
		//			stmt.executeUpdate(upSql);
					
					fid = (int) existFid;
					
				} else {
					conn.setAutoCommit(false);
					// 改名不重复
					String renameFileSql = "UPDATE " + FileSysTableName +" SET name = '"
							+ checkNameForSpecialChars(newName) + "', pid = " + pid + ", lastModified = " + System.currentTimeMillis() + " WHERE id = " + fid;
//					String renameRevisionSql = "UPDATE " 	+ FileRevisionTableName + " SET name = '" + checkNameForSpecialChars(newName)
//							+ "', lastModified = " + System.currentTimeMillis() + " WHERE fileId = " + fid;
		
					log4j.debug("[mySQL] Rename SQL: " + renameFileSql.toString());
					// Rename the file/folder
					if (stmt.executeUpdate(renameFileSql) == 0) {// 文件改名
						// Original file not found
						log4j.error("DBI#renameFileRecord FileNotFoundException , fid:" + fid);
						throw new FileNotFoundException("" + fid);
					}
		//			String maxRevisionSql = "SELECT MAX(id) as id FROM " + FileRevisionTableName + " WHERE fileId=" + fid;
					String  maxRevisionSql = "SELECT MAX(revision) as revision FROM " + FileRevisionTableName + " WHERE fileId=" + fid;
					long maxRevision = 0;
					log4j.debug("[mySQL] maxRevisionSql SQL: " + maxRevisionSql);
					rRs = stmt.executeQuery(maxRevisionSql);
					if (rRs.next()) {
						maxRevision = rRs.getInt("revision");
					}
					rRs.close();
//					if (maxRevision > 0) {
//						renameRevisionSql += " AND revision=" + maxRevision;// 只改最后一个版本的名称。前面的版本的名称不能变，要保留
//					}
//					log4j.debug("[mySQL] Rename Revision  SQL: " + renameRevisionSql.toString());
//					if (stmt.executeUpdate(renameRevisionSql) == 0) {// 文件版本改名
//						log4j .error("DBI#renameFileRecord 修改当前文件版本名称失败! fileId=" + fid);
//					}
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			try {
				if (conn != null) conn.rollback();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			// DEBUG
			log4j.error("[mySQL] Rename file error " + ex.getMessage());
			// Rethrow the exception
			throw new DBException(ex.getMessage());
		} finally {
		
			// Close the statement
			if (stmt != null) {
				try {
					stmt.close();
				} catch (Exception ex) {
				}
			}
			// Release the database connection
			if (conn != null) {
				try {
					conn.setAutoCommit(true);
				} catch (SQLException e) {
					e.printStackTrace();
				}
				releaseConnection(conn);
			}
		}
		
		return fid;
		}
	
	/**
	 * 修改未完成上传的文件的临时地址
	 */
	public void modifyFileTemporaryFile(int fid, FileSegmentInfo fileSegInfo,String shareName) throws DBException{
		// Rename a file/folder
		Connection conn = null;
		Statement stmt = null;
		String FileSysTableName = getFileSysTableName(shareName);
		String FileRevisionTableName = getRevisionTableName(shareName);
		if(null !=fileSegInfo)
		{
			log4j.debug("DBI#modifyFileTemporaryFile ; time:" + System.currentTimeMillis()
					+ ", fileId:" + fid + ", temporaryFile:" + fileSegInfo.getTemporaryFile());
			try {
				synchronized (this)// 暂时改为同步。 避免产生重复文件
				{
					// //因DBI#deleteFileRecord ; DBI#renameFileRecord 时间间隔太短，延时100ms
					// 再执行改名操作
					// Get a connection to the database
					conn = getConnection();
					stmt = conn.createStatement();
					
					String sql = "SELECT id,storePath,rollPath,lastModified,size FROM "
						+ FileRevisionTableName
						+ " WHERE status>=0 AND fileId = "
						+ fid + " ORDER BY revisionCount DESC";// 只查有效的版本

					String rollPath = null;
					String storePath = null;
					long revisionId = 0;
					// Find the data fragments for the file, check if the file is stored
					// in a Jar
					log4j.debug("[mySQL] Load modify data SQL: " + sql);
					ResultSet rs = stmt.executeQuery(sql);

					// Load the file data from the main file record(s)

					if (rs.next()) {
						// Access the file data
						rollPath = rs.getString("rollPath");
						storePath = rs.getString("storePath");
						revisionId = rs.getLong("id");
					}
					File temFile = new File(fileSegInfo.getTemporaryFile());
					if (StringUtils.isEmpty(storePath) && temFile.exists() && temFile.lastModified()>0) {
						String updatePathSql ="UPDATE  " + FileRevisionTableName + " SET rollPath = '/' ,storePath='"+fileSegInfo.getTemporaryFile()+"',size="+fileSegInfo.getFileLength()+"   WHERE id = " + revisionId+" AND storePath is null ";// 改
//						String updatePathSql ="UPDATE  " + FileRevisionTableName + " SET storePath='"+fileSegInfo.getTemporaryFile()+"'  WHERE id = " + revisionId+" AND storePath is null ";// 改
						log4j.debug("[mySQL] updatePathSql  SQL: " + updatePathSql);
						if (stmt.executeUpdate(updatePathSql)> 0) {// 文件版本改名
							//修改主文件大小值(不修改office2010会因，下载的大小不一致，提示恢复)
							String upSizeSql = "UPDATE "+ FileSysTableName +" SET size="+fileSegInfo.getFileLength()+" WHERE id="+fid;
							log4j.debug("[mySQL] upSizeSql  SQL: " + upSizeSql);
							stmt.executeUpdate(upSizeSql);
						}
						else
						{
							log4j.error("DBI#renameFileRecord  修改临时路径失败!，无对应路径为空的 fid:"+fid);
						}
						if ( fileSegInfo != null) {
							fileSegInfo.setQueued(false);
							fileSegInfo.setUpdated(false);
							fileSegInfo.setStatus(FileSegmentInfo.Saved);
						}
					}  
					else
					{
						log4j.debug("BDI#modifyFileTemporaryFile 不符合修改条件");
					}
				}
			} catch (SQLException ex) {
				// DEBUG
				log4j.error("BDI#modifyFileTemporaryFile error " + ex.getMessage());
				// Rethrow the exception
				throw new DBException(ex.getMessage());
			} catch (IOException ex) {
				log4j.error("BDI#modifyFileTemporaryFile error " + ex.getMessage());
			} finally {

				// Close the statement
				if (stmt != null) {
					try {
						stmt.close();
					} catch (Exception ex) {
					}
				}
				// Release the database connection
				if (conn != null)
					releaseConnection(conn);
			}
		}
	}

	/**
	 * Rename a file stream
	 * 
	 * @param dirId
	 *            int
	 * @param fid
	 *            int
	 * @param stid
	 *            int
	 * @param newName
	 * @exception DBException
	 */
	public void renameStreamRecord(int dirId, int fid, int stid, String newName)
			throws DBException {
		// TODO Auto-generated method stub

	}

	/**
	 * Return the retention period expiry date/time for the specified file, or
	 * zero if the file/folder is not under retention.
	 * 
	 * @param dirId
	 *            int
	 * @param fid
	 *            int
	 * @return RetentionDetails
	 * @exception DBException
	 */
	public RetentionDetails getFileRetentionDetails(int dirId, int fid)
			throws DBException {

		// Check if retention is enabled
		log4j.debug("DBI#getFileRetentionDetails; fid:" + fid + ", dirId:"
				+ dirId);
		if (isRetentionEnabled() == false)
			return null;

		// Get the retention record for the file/folder

		Connection conn = null;
		Statement stmt = null;

		RetentionDetails retDetails = null;

		try {

			// Get a connection to the database

			conn = getConnection();
			stmt = conn.createStatement();

			// Get the retention record, if any

			retDetails = getRetentionExpiryDateTime(conn, stmt, fid);
		} catch (SQLException ex) {

			// DEBUG
			log4j.error("[mySQL] Get retention error " + ex.getMessage());

			// Rethrow the exception

			throw new DBException(ex.getMessage());
		} finally {

			// Close the statement

			if (stmt != null) {
				try {
					stmt.close();
				} catch (Exception ex) {
				}
			}

			// Release the database connection

			if (conn != null)
				releaseConnection(conn);
		}

		// Return the retention expiry date/time

		return retDetails;
	}

	/**
	 * Start a directory search
	 * 
	 * @param dirId
	 *            int
	 * @param searchPath
	 *            String
	 * @param attrib
	 *            int
	 * @param infoLevel
	 *            int
	 * @param maxRecords
	 *            int
	 * @return DBSearchContext
	 * @exception DBException
	 */
	public DBSearchContext startSearch(int dirId, String searchPath,
			int attrib, int infoLevel, int maxRecords, String userName,
			String shareName) throws DBException {
		log4j.debug("DBI#startSearch dirId=" + dirId + ",path=" + searchPath 	+ ",userName=" + userName + ",shareName=" + shareName + " \n\n\n");
		if(dirId <= 0 && searchPath.indexOf("\\", 2)==-1)
		{
			log4j.debug("MySQLDBWasLInterface.startSearch()");
			return startSearchShares(dirId, searchPath, attrib, infoLevel, maxRecords, userName, shareName);
		}
		else
		{
			int endpos = searchPath.indexOf("\\", 2);
			if(endpos>0)
			{
				shareName = searchPath.substring(1, endpos);
			}
			else
			{
				shareName = searchPath.substring(1);
			}
//			log4j.debug("DBI#startSearch截取后shareName=" + shareName + " \n\n\n");
		}
		// Search for files/folders in the specified folder
		int userId = 0;
		ResultSet rs = null;
		Connection conn = null;
		Statement stmt = null;
		WildCard wildCard = null;
		int roleId = 0;
		int departmentId = 0;
		String noRightIds = ",";//无权限的fileId
		String readonlyIds = ",";//只读的fildId
		boolean isSuperAdmin = false;//资料库用
		boolean isFileAdmin = false;//资料库用
		long parentPermFileId = dirId;//资料库用
		String parentPermissions = "";//资料库用
		String groupIds = "";
		String departmentPath ="";
		try {
			UserBean user = this.getUserByUsername(userName);
			if (null != user) {
				userId = user.getId();
				roleId = user.getRoleId();
				departmentId = user.getDepartmentId();
			}

			conn = getConnection();
			stmt = conn.createStatement();

			StringBuffer sql = new StringBuffer();
			//sql.append("SELECT id,pid,name,size,add_time,lastModified,isFile,userId,permissions FROM ");
//			sql.append("SELECT fid as id,pid, fname as name,size,create_date as add_time,last_modified as lastModified,isFile, " +
//					"user_id as userId, ftype as type FROM ");

			sql.append("SELECT id,pid, fname,size,create_date,last_modified,isFile, user_id FROM ");
			
			sql.append(TABLE_WAS_TEMPFILE);
			sql.append(" WHERE status>=0 AND pid = ").append(dirId);
			
			//jweb_was_tempfile type:    0:公共资料库文件;1:个人网盘文件;2:共享文件
			if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_COMMFILE)) {
				if (roleId == 1) {// 超级管理员权限 直接查询出来
					isSuperAdmin = true;
				}
				else
				{
					// 查询是否为库管理员
					if (dirId > 0) {
						String auditorSql = "SELECT auditorId FROM jweb_commonfilecache WHERE id=(SELECT pid1 FROM jweb_commonfilecache WHERE id=" + dirId + ")";
						try {
							log4j.debug("[mySQL] auditorSql  SQL: " + auditorSql);
							ResultSet rs2 = stmt.executeQuery(auditorSql);
							String auditorId = "";
							while (rs2.next()) {
								auditorId = "," + rs2.getString("auditorId") + ",";
							}
							rs2.close();
							if (auditorId.contains("," + userId + ",")) {
								isFileAdmin = true;// 是资料库管理员，有所有权限
							}
						} catch (Exception e) {
							log4j.error("公共资料库 查询组、部门失败", e);
						}
					}
				}
				if(isSuperAdmin==false && isFileAdmin==false)
				{
					groupIds = this.getGroupIdsByUserid(userId);
					departmentPath = this.getDepartmentPathById(departmentId);	
					
					String PathSql = "SELECT permissionsFileId FROM jweb_commonfilecache WHERE id =" + dirId;
					log4j.debug("[mySQL] PathSql SQL:" + PathSql);
					ResultSet pathRs = stmt.executeQuery(PathSql);
					if (pathRs.next()) {
						parentPermFileId = pathRs.getInt("permissionsFileId");
					}
					pathRs.close();
					 
					if(dirId>0)
					{
						parentPermissions = this.filePermissions(parentPermFileId, userId, groupIds, departmentPath);//重新获得权限
					}
				}
				
				// 查询语句
//				sql = new StringBuffer();
//				sql.append("SELECT id,pid,name,size,add_time,lastModified,isFile,userId,permissionsFileId,islock,lockedByUser FROM ");
//				sql.append(FileSysTableName);
//				sql.append(" WHERE  status >= 0 AND pid = ").append(dirId);
//				if (dirId == 0) {
//					sql.append(" AND type IN(0,2)");
//				}
				sql.append(" AND ftype=0");
			} else 
				if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_USERFILE)) {
				// 查询语句
				sql.append(" AND user_id=").append(userId);
				sql.append(" AND ftype=1");
			} else if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_RECIVEFILE)) {
				String fileIds = "";
				if (dirId == 0) {
					try {
						// Check if the file already exists in the database
						groupIds = this.getGroupIdsByUserid(userId);
						departmentPath = this.getDepartmentPathById(departmentId);

						StringBuffer recSql = new StringBuffer();
						recSql
								.append("SELECT DISTINCT fileId FROM jweb_sharefilereceive WHERE status>=0");
						recSql.append(" AND ( (type=0 AND receiver_id=")
								.append(userId).append(")");
						if (StringUtils.isNotEmpty(departmentPath)) {
							recSql.append(" OR (type=1 AND receiver_id IN(")
									.append(departmentPath).append("))");
						}
						if (StringUtils.isNotEmpty(groupIds)) {
							recSql.append(" OR (type=2 AND receiver_id IN(")
									.append(groupIds).append("))");
						}
						recSql.append(")");
						log4j.debug("[mySQL] recSql  SQL: " + recSql);
						ResultSet rs2 = stmt.executeQuery(recSql.toString());
						while (rs2.next()) {
							int sFileId = rs2.getInt("fileId");
							if (fileIds.length() > 0) {
								fileIds += ",";
							}
							fileIds += sFileId;
						}
						rs2.close();
					} catch (SQLException e) {
						log4j.error("[mySQL] startSearch get shareFile error "
								+ e.getMessage());
					}
				}
				// 查询语句
//				sql = new StringBuffer();
//				//sql.append("SELECT id,pid,name,size,add_time,lastModified,isFile,userId,permissions FROM ");
//				sql.append("SELECT id,pid, fname,size,create_date,last_modified,isFile, user_id FROM ");
//				sql.append(TABLE_WAS_TEMPFILE);
//				sql.append(" WHERE status>=-1 AND pid = ").append(dirId);
				if (dirId == 0) {
					if (StringUtils.isNotEmpty(fileIds)) {
						//sql.append(" AND id in (").append(fileIds).append(")");
					} else {
						sql.append(" AND id in (0)");
					}
				}
				sql.append(" AND user_id=").append(userId);
				sql.append(" AND ftype=2");
			}
			
			
			String[] paths = FileName.splitPath(searchPath);
			// Check if the file name contains wildcard characters

			if (WildCard.containsWildcards(searchPath)) {

				// For the '*.*' and '*' wildcards the SELECT will already
				// return all files/directories
				// that are attached to the
				// parent directory. For 'name.*' and '*.ext' type wildcards we
				// can use the LIKE clause
				// to filter the required
				// records, for more complex wildcards we will post-process the
				// search using the
				// WildCard class to match the
				// file names.

				if (searchPath.endsWith("\\*.*") == false
						&& searchPath.endsWith("\\*") == false) {

					// Create a wildcard search pattern

					wildCard = new WildCard(paths[1], true);

					// Check for a 'name.*' type wildcard

					if (wildCard.isType() == WildCard.WILDCARD_EXT) {

						// Add the wildcard file extension SELECTion clause to
						// the SELECT

						sql.append(" AND lower(fname) LIKE lower('");
						sql.append(checkNameForSpecialChars(wildCard
								.getMatchPart()));
						sql.append("%')");

						// Clear the wildcard object, we do not want it to
						// filter the search results

						wildCard = null;
					} else if (wildCard.isType() == WildCard.WILDCARD_NAME) {

						// Add the wildcard file name SELECTion clause to the
						// SELECT

						// sql.append(" AND lower(name) LIKE (lower('%");
						// sql.append(checkNameForSpecialChars(wildCard.getMatchPart()));
						// sql.append("'))");

						sql.append(" AND lower(fname) LIKE lower('");
						sql.append(checkNameForSpecialChars(wildCard
								.getMatchPart()));
						sql.append("%')");

						// Clear the wildcard object, we do not want it to
						// filter the search results

						wildCard = null;
					}
				}
			} else {

				// Search for a specific file/directory
				// sql.append(" AND lower(name) = lower('");
				// sql.append(checkNameForSpecialChars(paths[1]));
				// sql.append("')");
				if (StringUtils.isNotEmpty(paths[1]) && !(DBUtil.SHARENAME_WAS).equals(paths[1]))// 空时不执行
				{
					sql.append(" AND lower(fname) LIKE lower('");
					sql.append(checkNameForSpecialChars(paths[1]));
					sql.append("%')");
				}
			}

			// Return directories first

//			sql.append(" ORDER BY isFile DESC");

			
			//遍历权限
			if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_COMMFILE))
			{
				if(isSuperAdmin==false && isFileAdmin==false)
				{
					ArrayList<java.util.Map> fileList = new ArrayList<java.util.Map>();
					java.util.Map fmap = null;
					while(rs.next())
					{
						fmap = new java.util.HashMap();
						fmap.put("id", rs.getLong("id"));
						if(dirId>0)
						{
							fmap.put("permissionsFileId", rs.getLong("permissionsFileId"));
						}
						else
						{
							fmap.put("permissionsFileId", rs.getLong("id"));
						}
						fileList.add(fmap);
					}
					
					if(null != fileList && fileList.size()>0)
					{
						long permissionsFileId = 0;
						String permissions = "";
						long fileId = 0;
						for(java.util.Map map:fileList)
						{
							fileId = Long.parseLong(map.get("id").toString());
							if(StringUtils.isEmpty(parentPermissions) && dirId>0)
							{
								//无权限
								noRightIds +=fileId+",";//上级无权限，则全部无权限
							}
							else
							{
								permissionsFileId = Long.parseLong(map.get("permissionsFileId").toString());
								if(permissionsFileId<=0)
								{
									permissionsFileId = parentPermFileId;//解决permissionsFileId为0无权限问题
								}
								if(parentPermFileId!=permissionsFileId)
								{
									//与上级权限不一致
									permissions = this.filePermissions(permissionsFileId, userId, groupIds, departmentPath);//重新获得权限
									if(StringUtils.isNotEmpty(permissions) && permissions.contains("w"))
									{
										//有读写权限
									}
									else if(StringUtils.isNotEmpty(permissions) && permissions.contains("r"))
									{
										//只读权限
										readonlyIds += fileId+",";
									}
									else
									{
										//无权限
										noRightIds += fileId+",";
									}
								}
								else
								{
									if(parentPermissions.contains("w")==false)//上级为只读权限
									{
										//只读权限
										readonlyIds += fileId+",";
									}
								}
							}
							log4j.debug(" \n"+fileId+" ,permissionsFileId:"+permissionsFileId+" , permissions:"+permissions);
						}
					}
					fileList = null;//清空对象
					if(StringUtils.isNotEmpty(noRightIds) && noRightIds.length()>1)
					{
						String noIds = StringUtil.dealSQLInStatementId(noRightIds);
						sql.append(" AND id NOT IN(").append(noIds).append(") ");
					}
				}
			}
			// Get a connection to the database
			log4j.debug("[mySQL] Start search SQL: " + sql.toString());
			// Start the folder search
			rs = stmt.executeQuery(sql.toString());
		} catch (Exception ex) {

			// DEBUG
			log4j.error("[mySQL] Start search error " + ex.getMessage());

			// Rethrow the exception

			throw new DBException(ex.getMessage());
		} finally {

			// Release the database connection

			if (conn != null)
				releaseConnection(conn);
		}

		// Create the search context, and return
		if(readonlyIds.length()>1 || noRightIds.length()>1)
		{
			log4j.debug("readonlyIds:"+readonlyIds+" ,noRightIds:"+noRightIds);
			return new MySQLWasSearchContext(rs, wildCard, shareName,userId,readonlyIds,noRightIds);
		}
		return new MySQLWasSearchContext(rs, wildCard, shareName,userId);
	}

	/**
	 * Start a directory search
	 * 
	 * @param dirId
	 *            int
	 * @param searchPath
	 *            String
	 * @param attrib
	 *            int
	 * @param infoLevel
	 *            int
	 * @param maxRecords
	 *            int
	 * @return DBSearchContext
	 * @exception DBException
	 */
	public DBSearchContext startSearchShares(int dirId, String searchPath,
			int attrib, int infoLevel, int maxRecords, String userName,
			String shareName) throws DBException {
//		log4j.debug("DBI#startSearchU ; dirId=" + dirId + ",path=" + searchPath + ",userName=" + userName + ",shareName=" + shareName);
		// Search for files/folders in the specified folder
		ResultSet rs = null;
		Connection conn = null;
		Statement stmt = null;
		DBSearchContext context = null;
		WildCard wildCard = null;		
		try {		
			//最上层不用uid,提高效率
			conn = getConnection();
			stmt = conn.createStatement();
			log4j.debug("[mySQL] Start search SQL: " +searchShares_sql);
			rs = stmt.executeQuery(searchShares_sql);
			context =new MySQLWasSearchContext(rs, wildCard, shareName, 0);
		} catch (Exception ex) {
			log4j.error("[mySQL] Start SearchSharesCache error ",ex);
			throw new DBException(ex.getMessage());
		} finally {
			if (conn != null)
				releaseConnection(conn);
		}
		// Create the search context, and return
		return context;
	}
	

	/**
	 * Return the used file space, or -1 if not supported.
	 * 
	 * @return long
	 */
	public long getUsedFileSpace() {

		// Calculate the total used file space

		Connection conn = null;
		Statement stmt = null;

		long usedSpace = -1L;

		try {

			// Get a database connection and statement

			conn = getConnection();
			stmt = conn.createStatement();
			
			String sql = "SELECT SUM(CAST(size as BIGINT)) FROM "
					+ TABLE_WAS_TEMPFILE;

			// DEBUG
			log4j.debug("[mySQL] Get filespace SQL: " + sql);

			// Calculate the currently used disk space

			ResultSet rs = stmt.executeQuery(sql);

			if (rs.next())
				usedSpace = rs.getLong(1);
			log4j.debug(usedSpace + "  usedSpace  -- SUM(CAST(SIZE))");
		} catch (SQLException ex) {

			// DEBUG
			log4j.error("[mySQL] Get used file space error " + ex.getMessage());
		} finally {

			// Close the prepared statement

			if (stmt != null) {
				try {
					stmt.close();
				} catch (Exception ex) {
				}
			}

			// Release the database connection

			if (conn != null)
				releaseConnection(conn);
		}
		log4j.debug("DBI#getUsedFileSpace ;usedSpace=" + usedSpace);
		// Return the used file space

		return usedSpace;
	}

	/**
	 * Queue a file request.
	 * 
	 * @param req
	 *            FileRequest
	 * @exception DBException
	 */
	public void queueFileRequest(FileRequest req) throws DBException {

		// Make sure the associated file state stays in memory for a short time,
		// if the queue is
		// small
		// the request may get processed soon.
		if (req instanceof SingleFileRequest) {

			// Get the request details

			SingleFileRequest fileReq = (SingleFileRequest) req;

			try {

				// Check if the file request queue database connection is valid

				if (m_dbConn == null || m_dbConn.isClosed()
						|| m_reqStmt == null
						|| m_reqStmt.getConnection().isClosed())
					createQueueStatements();

				// Check if the request is part of a transaction, or a
				// standalone request

				if (fileReq.isTransaction() == false) {

					// Write the file request record

					int recCnt = 0;

					synchronized (m_reqStmt) {

						// Write the file request to the queue database

						m_reqStmt.clearParameters();

						m_reqStmt.setInt(1, fileReq.getFileId());
						m_reqStmt.setInt(2, fileReq.getStreamId());
						m_reqStmt.setInt(3, fileReq.isType());
						m_reqStmt.setString(4, fileReq.getTemporaryFile());
						m_reqStmt.setString(5, fileReq.getVirtualPath());
						m_reqStmt.setString(6, fileReq.getAttributesString());

						recCnt = m_reqStmt.executeUpdate();

						// Retrieve the allocated sequence number

						if (recCnt > 0) {

							// Get the last insert id

							ResultSet rs2 = m_reqStmt
									.executeQuery("SELECT LAST_INSERT_ID();");

							if (rs2.next())
								fileReq.setSequenceNumber(rs2.getInt(1));
						}
					}
				} else {

					// Check if the transaction prepared statement is valid, we
					// may have lost the
					// connection to the
					// database.

					if (m_tranStmt == null
							|| m_tranStmt.getConnection().isClosed())
						createQueueStatements();

					// Write the transaction file request to the database

					synchronized (m_tranStmt) {

						// Write the request record to the database

						m_tranStmt.clearParameters();

						m_tranStmt.setInt(1, fileReq.getFileId());
						m_tranStmt.setInt(2, fileReq.getStreamId());
						m_tranStmt.setInt(3, fileReq.isType());
						m_tranStmt.setInt(4, fileReq.getTransactionId());
						m_tranStmt.setString(5, fileReq.getTemporaryFile());
						m_tranStmt.setString(6, fileReq.getVirtualPath());
						m_tranStmt.setString(7, fileReq.getAttributesString());

						m_tranStmt.executeUpdate();
					}
				}

				// File request was queued successfully, check for any offline
				// file requests

				if (hasOfflineFileRequests())
					databaseOnlineStatus(true);
			} catch (SQLException ex) {

				// If the request is a save then add to a pending queue to retry
				// when the database
				// is back online

				if (fileReq.isType() == FileRequest.SAVE
						|| fileReq.isType() == FileRequest.TRANSSAVE)
					queueOfflineSaveRequest(fileReq);

				// DEBUG

				log4j.error(ex);

				// Rethrow the exception

				throw new DBException(ex.getMessage());
			}
		}
	}

	/**
	 * Perform a queue cleanup deleting temporary cache files that do not have
	 * an associated save or transaction request.
	 * 
	 * @param tempDir
	 *            File
	 * @param tempDirPrefix
	 *            String
	 * @param tempFilePrefix
	 *            String
	 * @param jarFilePrefix
	 *            String
	 * @return FileRequestQueue
	 * @throws DBException
	 */
	public FileRequestQueue performQueueCleanup(File tempDir,
			String tempDirPrefix, String tempFilePrefix, String jarFilePrefix)
			throws DBException {

		// Get a connection to the database

		Connection conn = null;
		PreparedStatement pstmt = null;
		Statement stmt = null;

		FileRequestQueue reqQueue = new FileRequestQueue();

		try {

			String FileSysTableName = FileSysTable_COMMFILE;
			String shareName = DBUtil.SHARENAME_WAS_COMMFILE;
			if(tempDir.getName().equalsIgnoreCase("smb"))//个人文件　
			{
				FileSysTableName = FileSysTable_MYFILE;
				shareName = DBUtil.SHARENAME_WAS_USERFILE;
			}
			else if(tempDir.getName().equalsIgnoreCase("smbshare"))//资料库
			{
				FileSysTableName = FileSysTable_SHAREFILE;
				shareName = DBUtil.SHARENAME_WAS_RECIVEFILE;
			}
			FileSysTableName = getFileSysTableName(shareName);
//			
			
			// Get a connection to the database

			conn = getConnection(DBConnectionPool.PermanentLease);

			// Delete all load requests FROM the queue

			stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("SELECT * FROM "
					+ getQueueTableName() + " WHERE ReqType = "
					+ FileRequest.LOAD + ";");

			while (rs.next()) {

				// Get the path to the cache file

				String tempPath = rs.getString("TempFile");

				// Check if the cache file exists, the file load may have been
				// in progress

				File tempFile = new File(tempPath);
				if (tempFile.exists()) {

					// Delete the cache file for the load request

					tempFile.delete();

					// DEBUG
					log4j
							.debug("[mySQL] Deleted load request file "
									+ tempPath);
				}
			}

			// Check if the lock file exists, if so then the server did not
			// shutdown cleanly

			File lockFile = new File(tempDir, LockFileName);
			setLockFile(lockFile.getAbsolutePath());

			boolean cleanShutdown = lockFile.exists() == false;

			// Create a crash recovery folder if the server did not shutdown
			// clean

			File crashFolder = null;

			if (cleanShutdown == false && hasCrashRecovery()) {

				// Create a unique crash recovery sub-folder in the temp area

				SimpleDateFormat dateFmt = new SimpleDateFormat(
						"yyyyMMMdd_HHmmss");
				crashFolder = new File(tempDir, "CrashRecovery_"
						+ dateFmt.format(new Date(System.currentTimeMillis())));
				if (crashFolder.mkdir() == true) {

					// DEBUG
					log4j.debug("[mySQL] Created crash recovery folder - "
							+ crashFolder.getAbsolutePath());
				} else {

					// Use the top level temp area for the crash recovery files

					crashFolder = tempDir;

					// DEBUG
					log4j
							.debug("[mySQL] Failed to created crash recovery folder, using folder - "
									+ crashFolder.getAbsolutePath());
				}
			}

			// Delete the file load request records

			stmt.execute("DELETE FROM " + getQueueTableName()
					+ " WHERE ReqType = " + FileRequest.LOAD + ";");

			// Create a statement to check if a temporary file is part of a save
			// request

			pstmt = conn.prepareStatement("SELECT FileId,SeqNo FROM "
					+ getQueueTableName() + " WHERE TempFile = ?;");

			// Scan all files/sub-directories within the temporary area looking
			// for files that have
			// been saved but not
			// deleted due to a server shutdown or crash.

			File[] tempFiles = tempDir.listFiles();

			if (tempFiles != null && tempFiles.length > 0) {

				// Scan the file loader sub-directories for temporary files

				for (int i = 0; i < tempFiles.length; i++) {

					// Get the current file/sub-directory

					File curFile = tempFiles[i];

					if (curFile.isDirectory()
							&& curFile.getName().startsWith(tempDirPrefix)) {

						// Check if the sub-directory has any loader temporary
						// files

						File[] subFiles = curFile.listFiles();

						if (subFiles != null && subFiles.length > 0) {

							// Check each file to see if it has a pending save
							// request in the file
							// request database

							for (int j = 0; j < subFiles.length; j++) {

								// Get the current file FROM the list

								File ldrFile = subFiles[j];

								if (ldrFile.isFile()
										&& ldrFile.getName().startsWith(
												tempFilePrefix)) {

									try {

										// Get the file details FROM the file
										// system table

										pstmt.clearParameters();
										pstmt.setString(1, ldrFile
												.getAbsolutePath());

										rs = pstmt.executeQuery();

										if (rs.next()) {

											// File save request exists for temp
											// file, nothing to do

										} else {

											// Check if the modified date
											// indicates the file may
											// have been updated

											if (ldrFile.lastModified() != 0L) {

												// Get the file id FROM the
												// cache file name

												String fname = ldrFile
														.getName();
												int dotPos = fname.indexOf('.');
												String fidStr = fname
														.substring(
																tempFilePrefix
																		.length(),
																dotPos);

												if (fidStr.indexOf('_') == -1) {

													// Convert the file id

													int fid = -1;

													try {
														fid = Integer
																.parseInt(fidStr);
													} catch (NumberFormatException e) {
														log4j
																.error("[mySQL] startSearch performQueueCleanup error "
																		+ e
																				.getMessage());
													}

													// Get the file details FROM
													// the database

													if (fid != -1) {

														// Get the file details
														// for the temp file
														// using the file id

														rs = stmt
																.executeQuery("SELECT * FROM "
																		+ FileSysTableName
																		+ " WHERE id = "
																		+ fid
																		+ ";");

														// If the previous
														// server shutdown was
														// clean
														// then we may be able
														// to queue the file
														// save

														if (cleanShutdown == true) {

															if (rs.next()) {

																// Get the
																// currently
																// stored
																// modifed
																// date and file
																// size for the
																// associated
																// file

																long dbModDate = rs
																		.getLong("lastModified");
																long dbFileSize = rs
																		.getLong("size");

																// Check if the
																// temp file
																// requires
																// saving

																if (ldrFile
																		.length() != dbFileSize
																		|| ldrFile
																				.lastModified() > dbModDate) {

																	// Build the
																	// filesystem
																	// path to
																	// the file
																	// TODO
																	String filesysPath = buildPathForFileId(
																			fid,
																			stmt);

																	if (filesysPath != null) {

																		// Create
																		// a
																		// file
																		// state
																		// for
																		// the
																		// file

																		FileState fstate = m_dbCtx
																				.getStateCache()
																				.findFileState(
																						filesysPath,
																						true);

																		FileSegmentInfo fileSegInfo = (FileSegmentInfo) fstate
																				.findAttribute(ObjectIdFileLoader.DBFileSegmentInfo);
																		FileSegment fileSeg = null;

																		if (fileSegInfo == null) {

																			// Create
																			// a
																			// new
																			// file
																			// segment

																			fileSegInfo = new FileSegmentInfo();
																			fileSegInfo
																					.setTemporaryFile(ldrFile
																							.getAbsolutePath());

																			fileSeg = new FileSegment(
																					fileSegInfo,
																					true);
																			fileSeg
																					.setStatus(
																							FileSegmentInfo.SaveWait,
																							true);

																			// Add
																			// the
																			// segment
																			// to
																			// the
																			// file
																			// state
																			// cache

																			fstate
																					.addAttribute(
																							ObjectIdFileLoader.DBFileSegmentInfo,
																							fileSegInfo);

																			// Add
																			// a
																			// file
																			// save
																			// request
																			// for
																			// the
																			// temp
																			// file
																			// to
																			// the
																			// recovery
																			// queue

																			reqQueue
																					.addRequest(new SingleFileRequest(
																							FileRequest.SAVE,
																							fid,
																							0,
																							ldrFile
																									.getAbsolutePath(),
																							filesysPath,
																							fstate));

																			// Update
																			// the
																			// file
																			// size
																			// and
																			// modified
																			// date/time
																			// in
																			// the
																			// filesystem
																			// database

																			stmt
																					.execute("UPDATE "
																							+ FileSysTableName
																							+ " SET size = "
																							+ ldrFile
																									.length()
																							+ ", lastModified = "
																							+ ldrFile
																									.lastModified()
																							+ " WHERE id = "
																							+ fid
																							+ ";");

																			// DEBUG

																			log4j
																					.debug("[mySQL] Queued save request for "
																							+ ldrFile
																									.getName()
																							+ ", path="
																							+ filesysPath
																							+ ", fid="
																							+ fid);
																		}
																	} else {

																		// Delete
																		// the
																		// temp
																		// file,
																		// cannot
																		// resolve
																		// the
																		// path

																		ldrFile
																				.delete();

																		// DEBUG
																		log4j
																				.debug("[mySQL] Cannot resolve filesystem path for FID "
																						+ fid
																						+ ", deleted file "
																						+ ldrFile
																								.getName());
																	}
																}
															} else {

																// Delete the
																// temp file,
																// file does
																// not exist in
																// the
																// filesystem
																// table

																ldrFile
																		.delete();

																// DEBUG
																log4j
																		.debug("[mySQL] No matching file record for FID "
																				+ fid
																				+ ", deleted file "
																				+ ldrFile
																						.getName());
															}
														} else {

															// File server did
															// not shutdown
															// cleanly
															// so move any
															// modified files to
															// a
															// holding area as
															// they may be
															// corrupt

															if (rs.next()
																	&& hasCrashRecovery()) {

																// Get the
																// filesystem
																// file name

																String extName = rs
																		.getString("name");

																// Generate a
																// file name to
																// rename
																// the cache
																// file into a
																// crash
																// recovery
																// folder

																File crashFile = new File(
																		crashFolder,
																		""
																				+ fid
																				+ "_"
																				+ extName);

																// Rename the
																// cache file
																// into the
																// crash
																// recovery
																// folder

																if (ldrFile
																		.renameTo(crashFile)) {

																	// DEBUG
																	log4j
																			.debug("[mySQL] Crash recovery file - "
																					+ crashFile
																							.getAbsolutePath());
																}
															} else {

																// DEBUG
																log4j
																		.debug("[mySQL] Deleted incomplete cache file - "
																				+ ldrFile
																						.getAbsolutePath());

																// Delete the
																// incomplete
																// cache file

																ldrFile
																		.delete();
															}
														}
													} else {

														// Invalid file id
														// format, delete the
														// temp
														// file

														ldrFile.delete();

														// DEBUG

														log4j
																.debug("[mySQL] Bad file id format, deleted file, "
																		+ ldrFile
																				.getName());
													}
												} else {

													// Delete the temp file as
													// it is for an NTFS
													// stream

													ldrFile.delete();

													// DEBUG
													log4j
															.debug("[mySQL] Deleted NTFS stream temp file, "
																	+ ldrFile
																			.getName());
												}
											} else {

												// Delete the temp file as it
												// has not been modified
												// since it was loaded

												ldrFile.delete();

												// DEBUG
												log4j
														.debug("[mySQL] Deleted unmodified temp file, "
																+ ldrFile
																		.getName());
											}
										}
									} catch (SQLException ex) {
										log4j
												.error("[mySQL] startSearch performQueueCleanup Deleted error "
														+ ex.getMessage());
									}
								} else {

									// DEBUG
									log4j
											.debug("[mySQL] Deleted temporary file "
													+ ldrFile.getName());

									// Delete the temporary file

									ldrFile.delete();
								}
							}
						}
					}
				}
			}

			// Create the lock file, delete any existing lock file

			if (lockFile.exists())
				lockFile.delete();

			try {
				lockFile.createNewFile();
			} catch (IOException ex) {

				// DEBUG
				log4j.debug("[mySQL] Failed to create lock file - "
						+ lockFile.getAbsolutePath());
			}
		} catch (SQLException ex) {

			log4j.error(ex);
		} finally {

			// Close the load request statement

			if (stmt != null) {
				try {
					stmt.close();
				} catch (Exception ex) {
				}
			}

			// Close the prepared statement

			if (pstmt != null) {
				try {
					pstmt.close();
				} catch (Exception ex) {
				}
			}

			// Release the database connection

			if (conn != null)
				releaseConnection(conn);
		}

		// DEBUG
		log4j.debug("[mySQL] Cleanup recovered " + reqQueue.numberOfRequests()
				+ " file saves FROM previous run");

		// Return the recovery file request queue

		return reqQueue;
	}

	/**
	 * Check if the specified temporary file has a queued request.
	 * 
	 * @param tempFile
	 *            String
	 * @param lastFile
	 *            boolean
	 * @return boolean
	 * @exception DBException
	 */
	public boolean hasQueuedRequest(String tempFile, boolean lastFile)
			throws DBException {

		Connection conn = null;
		Statement stmt = null;

		boolean queued = false;

		try {

			// Get a connection to the database

			conn = getConnection();
			stmt = conn.createStatement();

			String sql = "SELECT FileId FROM " + getQueueTableName()
					+ " WHERE TempFile = '" + tempFile + "';";

			// DEBUG
			log4j.debug("[mySQL] Has queued req SQL: " + sql);

			// Check if there is a queued request using the temporary file

			ResultSet rs = stmt.executeQuery(sql);
			if (rs.next())
				queued = true;
			else {

				// Check if there is a transaction using the temporary file

				sql = "SELECT FileId FROM " + getTransactionTableName()
						+ " WHERE TempFile = '" + tempFile + "';";

				// DEBUG
				log4j.debug("[mySQL] Has queued req SQL: " + sql);

				// Check the transaction table

				rs = stmt.executeQuery(sql);
				if (rs.next())
					queued = true;
			}
		} catch (SQLException ex) {

			log4j.error(ex);

			// Rethrow the exception

			throw new DBException(ex.getMessage());
		} finally {

			// Close the statement

			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException ex) {
				}
			}

			// Release the database connection

			if (conn != null)
				releaseConnection(conn);
		}

		// Return the queued status

		return queued;
	}

	/**
	 * Delete a file request from the pending queue.
	 * 
	 * @param fileReq
	 *            FileRequest
	 * @exception DBException
	 */
	public void deleteFileRequest(FileRequest fileReq) throws DBException {

		Connection conn = null;
		Statement stmt = null;

		try {

			// Get a connection to the database

			conn = getConnection();
			stmt = conn.createStatement();

			// Delete the file request queue entry from the request table or
			// multiple records from
			// the
			// transaction table

			if (fileReq instanceof SingleFileRequest) {

				// Get the single file request details

				SingleFileRequest singleReq = (SingleFileRequest) fileReq;

				// Delete the request record

				stmt.executeUpdate("DELETE FROM " + getQueueTableName()
						+ " WHERE SeqNo = " + singleReq.getSequenceNumber());
			} else {

				// Delete the transaction records

				stmt.executeUpdate("DELETE FROM " + getTransactionTableName()
						+ " WHERE TranId = " + fileReq.getTransactionId());
			}
		} catch (SQLException ex) {

			log4j.error(ex);

			// Rethrow the exception

			throw new DBException(ex.getMessage());
		} finally {

			// Close the statement

			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException ex) {
				}
			}

			// Release the database connection

			if (conn != null)
				releaseConnection(conn);
		}
	}

	/**
	 * Load a block of file request from the database into the specified queue.
	 * 
	 * @param fromSeqNo
	 *            int
	 * @param reqType
	 *            int
	 * @param reqQueue
	 *            FileRequestQueue
	 * @param recLimit
	 *            int
	 * @return int
	 * @exception DBException
	 */
	public int loadFileRequests(int fromSeqNo, int reqType,
			FileRequestQueue reqQueue, int recLimit) throws DBException {
		// Load a block of file requests from the loader queue

		Connection conn = null;
		Statement stmt = null;

		int recCnt = 0;

		try {

			// Get a connection to the database

			conn = getConnection();
			stmt = conn.createStatement();

			// Build the SQL to load the queue records

			String sql = "SELECT * FROM " + getQueueTableName()
					+ " WHERE SeqNo > " + fromSeqNo + " AND ReqType = "
					+ reqType + " ORDER BY SeqNo LIMIT " + recLimit + ";";

			// DEBUG
			log4j.debug("[mySQL] Load file requests - " + sql);

			// Get a block of file request records

			ResultSet rs = stmt.executeQuery(sql);

			while (rs.next()) {

				// Get the file request details

				int fid = rs.getInt("FileId");
				int stid = rs.getInt("StreamId");
				int reqTyp = rs.getInt("ReqType");
				int seqNo = rs.getInt("SeqNo");
				String tempPath = rs.getString("TempFile");
				String virtPath = rs.getString("VirtualPath");
				String attribs = rs.getString("Attribs");
				// debug
				log4j.debug("DBI#loadFileRequests ; fromSeqNo=" + fromSeqNo
						+ ",fid=" + fid + ",stid=" + stid + ",tempPath="
						+ tempPath + ",virtPath:" + virtPath);
				// Recreate the file request for the in-memory queue

				SingleFileRequest fileReq = new SingleFileRequest(reqTyp, fid,
						stid, tempPath, virtPath, seqNo, null);
				fileReq.setAttributes(attribs);

				// Add the request to the callers queue

				reqQueue.addRequest(fileReq);

				// Update the count of loaded requests

				recCnt++;
			}
		} catch (SQLException ex) {

			log4j.error(ex);

			// Rethrow the exception

			throw new DBException(ex.getMessage());
		} finally {

			// Close the statement

			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException ex) {
				}
			}

			// Release the database connection

			if (conn != null)
				releaseConnection(conn);
		}

		// Return the count of file requests loaded

		return recCnt;
	}

	/**
	 * Load a transaction request from the queue.
	 * 
	 * @param tranReq
	 *            MultiplFileRequest
	 * @return MultipleFileRequest
	 * @exception DBException
	 */
	public MultipleFileRequest loadTransactionRequest(
			MultipleFileRequest tranReq) throws DBException {

		// Load a transaction request from the transaction loader queue

		Connection conn = null;
		Statement stmt = null;

		try {

			// Get a connection to the database

			conn = getConnection();
			stmt = conn.createStatement();

			String sql = "SELECT * FROM " + getTransactionTableName()
					+ " WHERE TranId = " + tranReq.getTransactionId() + ";";

			// DEBUG
			log4j.debug("[mySQL] Load trans request - " + sql);

			// Get the block of file request records for the current transaction

			ResultSet rs = stmt.executeQuery(sql);

			while (rs.next()) {

				// Get the file request details

				int fid = rs.getInt("FileId");
				int stid = rs.getInt("StreamId");
				String tempPath = rs.getString("TempFile");
				String virtPath = rs.getString("VirtualPath");

				// debug
				log4j.debug("DBI#loadTransactionRequest; fid=" + fid + ",stid="
						+ stid + ",tempPath=" + tempPath + ",virtPath:"
						+ virtPath);
				// Create the cached file information and add to the request

				CachedFileInfo finfo = new CachedFileInfo(fid, stid, tempPath,
						virtPath);
				tranReq.addFileInfo(finfo);
			}
		} catch (SQLException ex) {

			// DEBUG
			log4j.error(ex);

			// Rethrow the exception

			throw new DBException(ex.getMessage());
		} finally {

			// Close the statement

			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException ex) {
				}
			}

			// Release the database connection

			if (conn != null)
				releaseConnection(conn);
		}

		// Return the updated file request

		return tranReq;
	}

	/**
	 * Shutdown the database interface, release resources.
	 * 
	 * @param context
	 *            DBDeviceContext
	 */
	public void shutdownDatabase(DBDeviceContext context) {

		// Call the base class

		super.shutdownDatabase(context);
	}

	/**
	 * Get the retention expiry date/time for a file/folder
	 * 
	 * @param conn
	 *            Connection
	 * @param stmt
	 *            Statement
	 * @param fid
	 *            int
	 * @return RetentionDetails
	 * @exception SQLException
	 */
	private final RetentionDetails getRetentionExpiryDateTime(Connection conn,
			Statement stmt, int fid) throws SQLException {

		// Get the retention expiry date/time for the specified file/folder

		RetentionDetails retDetails = null;
		String sql = "SELECT StartDate,EndDate FROM " + getRetentionTableName()
				+ " WHERE FileId = " + fid + ";";

		// DEBUG
		log4j.debug("[mySQL] Get retention expiry SQL: " + sql);

		// Get the retention record, if any

		ResultSet rs = stmt.executeQuery(sql);

		if (rs.next()) {

			// Get the retention expiry date

			Timestamp startDate = rs.getTimestamp("StartDate");
			Timestamp endDate = rs.getTimestamp("EndDate");

			retDetails = new RetentionDetails(fid,
					startDate != null ? startDate.getTime() : -1L, endDate
							.getTime());
		}

		// Return the retention expiry date/time

		return retDetails;
	}

	/**
	 * Determine if the specified file/folder is still within an active
	 * retention period
	 * 
	 * @param conn
	 *            Connection
	 * @param stmt
	 *            Statement
	 * @param fid
	 *            int
	 * @return boolean
	 * @exception SQLException
	 */
	private final boolean fileHasActiveRetention(Connection conn,
			Statement stmt, int fid) throws SQLException {

		// Check if retention is enabled

		if (isRetentionEnabled() == false)
			return false;

		// Check if the file/folder is within the retention period

		RetentionDetails retDetails = getRetentionExpiryDateTime(conn, stmt,
				fid);
		if (retDetails == null)
			return false;

		// File/folder is within the retention period

		return retDetails.isWithinRetentionPeriod(System.currentTimeMillis());
	}

	/**
	 * Create the prepared statements used by the file request queueing database
	 * 
	 * @exception SQLException
	 */
	protected final void createQueueStatements() throws SQLException {

		// Check if the database connection is valid

		if (m_dbConn != null) {

			// Close the existing statements

			if (m_reqStmt != null)
				m_reqStmt.close();

			if (m_tranStmt != null)
				m_tranStmt.close();

			// Release the current database connection

			releaseConnection(m_dbConn);
			m_dbConn = null;

		}

		if (m_dbConn == null)
			m_dbConn = getConnection(DBConnectionPool.PermanentLease);

		// Create the prepared statements for accessing the file request queue
		// database

		m_reqStmt = m_dbConn
				.prepareStatement("INSERT INTO "
						+ getQueueTableName()
						+ "(FileId,StreamId,ReqType,TempFile,VirtualPath,Attribs) VALUES (?,?,?,?,?,?);");

		// Create the prepared statements for accessing the transaction request
		// queue database

		m_tranStmt = m_dbConn
				.prepareStatement("REPLACE INTO  "
						+ getTransactionTableName()
						+ "(FileId,StreamId,ReqType,TranId,TempFile,VirtualPath,Attribs) VALUES (?,?,?,?,?,?,?);");
	}

	/**
	 * Return the file data details for the specified file or stream.
	 * 
	 * @param fileId
	 * @param streamId
	 * @return DBDataDetails
	 * @throws DBException
	 */
	public DBDataDetails getFileDataDetails(int fileId, int streamId)
			throws DBException {

		// Load the file details from the data table

		Connection conn = null;
		Statement stmt = null;

		DBDataDetails dbDetails = null;

		try {

			// Get a connection to the database

			conn = getConnection();
			stmt = conn.createStatement();

			String sql = "SELECT * FROM " + getDataTableName()
					+ " WHERE FileId = " + fileId + " AND StreamId = "
					+ streamId + " AND FragNo = 1;";

			// DEBUG
			log4j.debug("[mySQL] Get file data details SQL: " + sql);

			// Load the file details

			ResultSet rs = stmt.executeQuery(sql);

			if (rs.next()) {

				// Create the file details

				dbDetails = new DBDataDetails(fileId, streamId);

				if (rs.getBoolean("JarFile") == true)
					dbDetails.setJarId(rs.getInt("JarId"));
			}
		} catch (SQLException ex) {

			// DEBUG
			log4j.error(ex);

			// Rethrow the exception

			throw new DBException(ex.getMessage());
		} finally {

			// Close the statement

			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException ex) {
				}
			}

			// Release the database connection

			if (conn != null)
				releaseConnection(conn);
		}

		// If the file details are not valid throw an exception

		if (dbDetails == null)
			throw new DBException("Failed to load file details for " + fileId
					+ ":" + streamId);

		// Return the file data details

		return dbDetails;
	}

	/**
	 * Return the maximum data fragment size supported
	 * 
	 * @return long
	 */
	public long getMaximumFragmentSize() {
		return 20 * MemorySize.MEGABYTE;
	}

	/**
	 * Load file data from the database into a temporary/local file 下载文件及打开文件的方法
	 * 
	 * @param fileId
	 *            int
	 * @param streamId
	 *            int
	 * @param fileSeg
	 *            FileSegment
	 * @throws DBException
	 * @throws IOException
	 */
	public int loadFileData(int fileId, int streamId, FileSegment fileSeg,String shareName)
			throws DBException, IOException {
		log4j.debug("DBI#loadFileData time:" + System.currentTimeMillis()
				+ " fileId:" + fileId + ", streamId:" + streamId
				+ ", fileSeq tmpFile:" + fileSeg.getTemporaryFile());
		String FileRevisionTableName = getRevisionTableName(shareName);
		// Open the temporary file
//		FileOutputStream fileOut = new FileOutputStream(fileSeg .getTemporaryFile());
		// Update the segment status
		fileSeg.setStatus(FileSegmentInfo.Loading);
		// DEBUG
		long startTime = System.currentTimeMillis();
		// Load the file data fragments
//"(select fid from "+FileSysTableName  +" whre )"
		Connection conn = null;
		Statement stmt = null;
		int was_file_id = 0;
		try {
			// Get a connection to the database, create a statement
			conn = getConnection();
			stmt = conn.createStatement();
			/**
			 * 查询FileId的可用性 具体是哪张表里的数据
			 */
			String userIdsql= "SELECT fid,user_id FROM " + TABLE_WAS_TEMPFILE + " WHERE id= " + fileId;
			ResultSet rs1 = stmt.executeQuery(userIdsql);
			if(rs1.next())
			{
				was_file_id = rs1.getInt("fid");
				fileId = was_file_id;
			}
			else
			{
			if(was_file_id == 0 && fileId >0)
				{
					fileId = fileId ;
				}	
			}
			
		/*	String sql = "SELECT id,storePath,rollPath,lastModified,size FROM "
				+ FileRevisionTableName
				+ " WHERE status>=0 AND fileId = "
				+ fileId + " ORDER BY revisionCount DESC";// 只查有效的版本
*/			String sql = "SELECT id,storePath,rollPath,lastModified,size FROM "
				+ FileRevisionTableName
//				+ " WHERE status>=0 AND fileId = (SELECT fid FROM  "+FileSysTableName+" WHERE status >=0 and  id ="+ fileId 
				+ " WHERE status>=0 AND fileId = "+ fileId 
				+ "  ORDER BY revisionCount DESC";// 只查有效的版本

			String rollPath = null;
			String storePath = null;
			long revisionId = 0;
			// Find the data fragments for the file, check if the file is stored
			// in a Jar
			log4j.debug("[mySQL] Load file data SQL: " + sql);
			ResultSet rs = stmt.executeQuery(sql);

			// Load the file data from the main file record(s)

			if (rs.next()) {
				// Access the file data
				rollPath = rs.getString("rollPath");
				storePath = rs.getString("storePath");
				revisionId = rs.getLong("id");
			}
			String filePath = rollPath + storePath;// not storePath+rollPath
			File rollFile = new File(filePath);
			File uFile = new File(fileSeg.getTemporaryFile());
			log4j.debug("DBI#加载文件内容 fileId:" + fileId + ", revisionId:"
					+ revisionId + ", filePath:" + filePath
					+ ", fileSeq tmpFile:" + fileSeg.getTemporaryFile()+" ,rollFile.exists():"+rollFile.exists());
			if (rollFile.exists() == false) {
				log4j.error("DBI#loadFileData File not exists , fileId:" +", shareName :"+ shareName
						+ fileId + ",filePath: " + filePath+" , uFile.exists():"+uFile.exists()+", uFile.length():"+uFile.length());
//				return DBFileLoader.StsError;// 失败
//				throw new IOException("文件内容不存在!"); 
				if(uFile.exists() && uFile.length()>0)
				{
					//用存在的临时文件
				}
				else
				{
					log4j.debug("DBI#加载文件内容失败! 文件已经删除:"+fileSeg.getTemporaryFile());
					return DBFileLoader.StsError;// 失败
				}
			}
			else if(storePath.endsWith(".LFS") || storePath.endsWith(".LFN"))//如果是.LFS或.LFN表示是LFS存储文件
			{
				AESUtil.decryptFile(rollFile, uFile);
				log4j.debug("加密文件转换完成; uFile:" + uFile.exists() + " , length:"
							+ uFile.length() + " , fileSeg.fileExists:"
							+ fileSeg.fileExists() + " ,isDataAvailable:"
							+ fileSeg.isDataAvailable() + ", sfileLength:"
							+ fileSeg.getFileLength());
			}
			else
			{
				log4j.debug("加载未处理完的缓存文件");
				DiskUtil.copySingeFile(rollFile, uFile);
			}
			log4j.debug("fileSeg.flush(); fileSeg.fileExists:"
					+ fileSeg.fileExists() + " ,isDataAvailable:"
					+ fileSeg.isDataAvailable() + ", sfileLength:"
					+ fileSeg.getFileLength());
			
			long endTime = System.currentTimeMillis();
			log4j.debug("[mySQL] Loaded fid=" + fileId + ", stream=" + streamId
					+ ", frags=" + ", time=" + (endTime - startTime) + "ms");
			

			// Signal that the file data is available
			fileSeg.setReadableLength(uFile.length());//设置大小
			fileSeg.signalDataAvailable();//设置为可用

			return DBFileLoader.StsSuccess;// 成功
			
		} catch (SQLException ex) {
			// DEBUG
			log4j.error(ex);
			// Rethrow the exception
			throw new DBException(ex.getMessage());
		} catch (Exception e) {
			log4j.error("DBI#loadFileData*****解密加载文件异常");
			e.printStackTrace();
		}
		finally {
			// Check if a statement was allocated
			if (stmt != null) {
				try {
					stmt.close();
				} catch (Exception ex) {
				}
			}

			// Release the database connection
			if (conn != null)
				releaseConnection(conn);
			// Close the output file
//			if (fileOut != null) {
//				try {
//					fileOut.close();
//				} catch (Exception ex) {
//					log4j.error(ex);
//				}
//			}
		}

		// // Signal that the file data is available
		// fileSeg.signalDataAvailable();

		return DBFileLoader.StsError;// 失败
	}

	/**
	 * Load Jar file data from the database into a temporary file
	 * 
	 * @param jarId
	 *            int
	 * @param jarSeg
	 *            FileSegment
	 * @throws DBException
	 * @throws IOException
	 */
	public void loadJarData(int jarId, FileSegment jarSeg) throws DBException,
			IOException {

		// Load the Jar file data

		Connection conn = null;
		Statement stmt = null;

		FileOutputStream outJar = null;

		try {

			// Get a connection to the database, create a statement

			conn = getConnection();
			stmt = conn.createStatement();

			String sql = "SELECT * FROM " + getJarDataTableName()
					+ " WHERE JarId = " + jarId;

			// DEBUG
			log4j.debug("[mySQL] Load Jar data SQL: " + sql);

			// Create the temporary Jar file

			outJar = new FileOutputStream(jarSeg.getTemporaryFile());

			// Get the Jar data record

			ResultSet rs = stmt.executeQuery(sql);

			if (rs.next()) {

				// Access the Jar file data

				Blob dataBlob = rs.getBlob("Data");
				InputStream dataFrag = dataBlob.getBinaryStream();

				// Allocate the read buffer

				byte[] inbuf = new byte[(int) Math.min(dataBlob.length(),
						MaxMemoryBuffer)];

				// Read the Jar data from the database record and write to the
				// output file

				int rdLen = dataFrag.read(inbuf, 0, inbuf.length);
				long totLen = 0L;

				while (rdLen > 0) {

					// Write a block of data to the temporary file segment

					outJar.write(inbuf, 0, rdLen);
					totLen += rdLen;

					// Read another block of data

					rdLen = dataFrag.read(inbuf, 0, inbuf.length);
				}
			}

			// Close the output Jar file

			outJar.close();

			// Set the Jar file segment status to indicate that the data has
			// been loaded

			jarSeg.setStatus(FileSegmentInfo.Available, false);
		} catch (SQLException ex) {

			// DEBUG
			log4j.error(ex);

			// Rethrow the exception

			throw new DBException(ex.getMessage());
		} finally {

			// Check if a statement was allocated

			if (stmt != null) {
				try {
					stmt.close();
				} catch (Exception ex) {
				}
			}

			// Release the database connection

			if (conn != null)
				releaseConnection(conn);

			// Close the output file

			if (outJar != null) {
				try {
					outJar.close();
				} catch (Exception ex) {
					log4j.error(ex);
				}
			}
		}
	}

	/**
	 * Save the file data from the temporary/local file to the database
	 * 
	 * @param fileId
	 *            int
	 * @param streamId
	 *            int
	 * @param fileSeg
	 *            FileSegment
	 * @return int
	 * @throws DBException
	 * @throws IOException
	 */

	public int saveFileData(int fileId, int streamId, FileSegment fileSeg)
			throws DBException, IOException {

		// Determine if we can use an in memory buffer to copy the file
		// fragments

		boolean useMem = false;
		byte[] memBuf = null;

		if (getDataFragmentSize() <= MaxMemoryBuffer) {

			// Use a memory buffer to copy the file data fragments

			useMem = true;
			memBuf = new byte[(int) getDataFragmentSize()];
		}

		// Get the temporary file size

		File tempFile = new File(fileSeg.getTemporaryFile());

		// Save the file data

		Connection conn = null;
		Statement delStmt = null;
		PreparedStatement stmt = null;
		int fragNo = 1;

		FileInputStream inFile = null;

		try {

			// Open the temporary file

			inFile = new FileInputStream(tempFile);

			// Get a connection to the database

			conn = getConnection();
			delStmt = conn.createStatement();

			String sql = "DELETE FROM " + getDataTableName()
					+ " WHERE FileId = " + fileId + " AND StreamId = "
					+ streamId;

			// DEBUG
			log4j.debug("[mySQL] Save file data SQL: " + sql);

			// Delete any existing file data records for this file

			int recCnt = delStmt.executeUpdate(sql);
			delStmt.close();
			delStmt = null;

			// Add the file data to the database

			stmt = conn
					.prepareStatement("INSERT INTO "
							+ getDataTableName()
							+ " (FileId,StreamId,FragNo,FragLen,Data) VALUES (?,?,?,?,?)");

			// DEBUG
			log4j.debug("[mySQL] Save file data SQL: " + stmt.toString());

			long saveSize = tempFile.length();

			while (saveSize > 0) {

				// Determine the current fragment size to store

				long fragSize = Math.min(saveSize, getDataFragmentSize());

				// Determine if the data fragment should be copied to a memory
				// buffer or a seperate
				// temporary file

				InputStream fragStream = null;

				if (saveSize == fragSize) {

					// Just copy the data from the temporary file, only one
					// fragment

					fragStream = inFile;
				} else if (useMem == true) {

					// Copy a block of data to the memory buffer

					fragSize = inFile.read(memBuf);
					fragStream = new ByteArrayInputStream(memBuf);
				} else {

					// Need to create a temporary file and copy the fragment of
					// data to it

					throw new DBException("File data copy not implemented yet");
				}

				// Store the current fragment

				stmt.clearParameters();
				stmt.setInt(1, fileId);
				stmt.setInt(2, streamId);
				stmt.setInt(3, fragNo++);
				stmt.setInt(4, (int) fragSize);
				stmt.setBinaryStream(5, fragStream, (int) fragSize);

				if (stmt.executeUpdate() < 1 && hasDebug())
					log4j.debug("## mySQL Failed to update file data, fid="
							+ fileId + ", stream=" + streamId + ", fragNo="
							+ (fragNo - 1));

				// Update the remaining data size to be saved

				saveSize -= fragSize;

				// Renew the lease on the database connection so that it does
				// not expire

				getConnectionPool().renewLease(conn);
			}
		} catch (SQLException ex) {

			// DEBUG
			log4j.error(ex);

			// Rethrow the exception

			throw new DBException(ex.getMessage());
		} finally {

			// Close the delete statement

			if (delStmt != null) {
				try {
					delStmt.close();
				} catch (Exception ex) {
				}
			}

			// Close the insert statement

			if (stmt != null) {
				try {
					stmt.close();
				} catch (Exception ex) {
				}
			}

			// Release the database connection

			if (conn != null)
				releaseConnection(conn);

			// Close the input file

			if (inFile != null) {
				try {
					inFile.close();
				} catch (Exception ex) {
				}
			}
		}

		// Return the number of data fragments used to save the file data

		return fragNo;
	}

	/**
	 * Save the file data from a Jar file to the database
	 * 
	 * @param jarPath
	 *            String
	 * @param fileList
	 *            DBDataDetailsList
	 * @return int
	 * @throws DBException
	 * @throws IOException
	 */
	public int saveJarData(String jarPath, DBDataDetailsList fileList)
			throws DBException, IOException {

		// Write the Jar file to the blob field in the Jar data table

		Connection conn = null;
		PreparedStatement istmt = null;
		Statement stmt = null;

		int jarId = -1;

		try {

			// Get a connection to the database

			conn = getConnection();

			// Open the Jar file

			File jarFile = new File(jarPath);
			FileInputStream inJar = new FileInputStream(jarFile);

			// Add the Jar file data to the database

			istmt = conn.prepareStatement("INSERT INTO "
					+ getJarDataTableName() + " (Data) VALUES (?)");

			// DEBUG
			log4j.debug("[mySQL] Save Jar data SQL: " + istmt.toString());

			// Set the Jar data field

			istmt.setBinaryStream(1, inJar, (int) jarFile.length());

			if (istmt.executeUpdate() < 1 && hasDebug())
				log4j.debug("## mySQL Failed to store Jar data");

			// Get the unique jar id allocated to the new Jar record

			stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("SELECT LAST_INSERT_ID();");

			if (rs.next())
				jarId = rs.getInt(1);

			// Update the jar id record for each file in the Jar

			for (int i = 0; i < fileList.numberOfFiles(); i++) {

				// Get the current file details

				DBDataDetails dbDetails = fileList.getFileAt(i);

				// Add the file data record(s) to the database

				stmt.executeUpdate("DELETE FROM " + getDataTableName()
						+ " WHERE FileId = " + dbDetails.getFileId()
						+ " AND StreamId = " + dbDetails.getStreamId());
				stmt.executeUpdate("INSERT INTO " + getDataTableName()
						+ " (FileId,StreamId,FragNo,JarId,JarFile) VALUES ("
						+ dbDetails.getFileId() + "," + dbDetails.getStreamId()
						+ ", 1," + jarId + ",1);");
			}
		} catch (SQLException ex) {

			// DEBUG
			log4j.error(ex);

			// Rethrow the exception

			throw new DBException(ex.getMessage());
		} finally {

			// Close the statement

			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException ex) {
				}
			}

			// Close the insert statement

			if (istmt != null) {
				try {
					istmt.close();
				} catch (Exception ex) {
				}
			}

			// Release the database connection

			if (conn != null)
				releaseConnection(conn);
		}

		// Return the allocated Jar id

		return jarId;
	}

	/**
	 * Delete the file data for the specified file/stream
	 * 
	 * @param fileId
	 *            int
	 * @param streamId
	 *            int
	 * @throws DBException
	 * @throws IOException
	 */
	public void deleteFileData(int fileId, int streamId) throws DBException,
			IOException {

		// Delete the file data records for the file or stream

		Connection conn = null;
		Statement delStmt = null;

		try {

			// Get a connection to the database

			conn = getConnection();

			// Need to delete the existing data

			delStmt = conn.createStatement();
			String sql = null;

			// Check if the main file stream is being deleted, if so then delete
			// all stream data too

			if (streamId == 0)
				sql = "DELETE FROM " + getDataTableName() + " WHERE FileId = "
						+ fileId;
			else
				sql = "DELETE FROM " + getDataTableName() + " WHERE FileId = "
						+ fileId + " AND StreamId = " + streamId;

			// DEBUG

			log4j.debug("[mySQL] Delete file data SQL: " + sql);

			// Delete the file data records

			int recCnt = delStmt.executeUpdate(sql);

			// Debug
			log4j.debug("[mySQL] Deleted file data fid=" + fileId + ", stream="
					+ streamId + ", records=" + recCnt);
		} catch (SQLException ex) {

			// DEBUG
			log4j.error(ex);
		} finally {

			// Close the delete statement

			if (delStmt != null) {
				try {
					delStmt.close();
				} catch (Exception ex) {
				}
			}

			// Release the database connection

			if (conn != null)
				releaseConnection(conn);
		}
	}

	/**
	 * Delete the file data for the specified Jar file
	 * 
	 * @param jarId
	 *            int
	 * @throws DBException
	 * @throws IOException
	 */
	public void deleteJarData(int jarId) throws DBException, IOException {

		// Delete the data records for the Jar file data

		Connection conn = null;
		Statement delStmt = null;

		try {

			// Get a connection to the database

			conn = getConnection();

			// Need to delete the existing data

			delStmt = conn.createStatement();
			String sql = "DELETE FROM " + getJarDataTableName()
					+ " WHERE JarId = " + jarId + ";";

			// DEBUG
			log4j.debug("[mySQL] Delete Jar data SQL: " + sql);

			// Delete the Jar data records

			int recCnt = delStmt.executeUpdate(sql);

			// Debug
			log4j.debug("[mySQL] Deleted Jar data jarId=" + jarId
					+ ", records=" + recCnt);
		} catch (SQLException ex) {

			// DEBUG
			log4j.error(ex);
		} finally {

			// Close the delete statement

			if (delStmt != null) {
				try {
					delStmt.close();
				} catch (Exception ex) {
				}
			}

			// Release the database connection

			if (conn != null)
				releaseConnection(conn);
		}
	}

	// ***** DBObjectIdInterface Methods *****

	/**
	 * Create a file id to object id mapping
	 * 
	 * @param fileId
	 *            int
	 * @param streamId
	 *            int
	 * @param objectId
	 *            String
	 * @exception DBException
	 */
	public void saveObjectId(int fileId, int streamId, String objectId)
			throws DBException {

		// Create a new file id/object id mapping record

		Connection conn = null;
		Statement stmt = null;

		try {

			// Get a connection to the database

			conn = getConnection();
			stmt = conn.createStatement();

			// Delete any current mapping record for the object

			String sql = "DELETE FROM " + getObjectIdTableName()
					+ " WHERE FileId = " + fileId + " AND StreamId = "
					+ streamId;

			// DEBUG
			log4j.debug("[mySQL] Save object id SQL: " + sql);

			// Delete any current mapping record

			stmt.executeUpdate(sql);

			// Insert the new mapping record

			sql = "INSERT INTO " + getObjectIdTableName()
					+ " (FileId,StreamId,ObjectID) VALUES(" + fileId + ","
					+ streamId + ",'" + objectId + "')";

			// DEBUG
			log4j.debug("[mySQL] Save object id SQL: " + sql);

			// Create the mapping record

			if (stmt.executeUpdate(sql) == 0) {
				log4j.error("Failed to add object id record, fid=" + fileId
						+ ", objId=" + objectId);
				throw new DBException("Failed to add object id record, fid="
						+ fileId + ", objId=" + objectId);
			}
		} catch (SQLException ex) {

			// DEBUG
			log4j.error(ex);

			// Rethrow the exception

			throw new DBException(ex.getMessage());
		} finally {

			// Close the statement

			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException ex) {
				}
			}

			// Release the database connection

			if (conn != null)
				releaseConnection(conn);
		}
	}

	/**
	 * Load the object id for the specified file id
	 * 
	 * @param fileId
	 *            int
	 * @param streamId
	 *            int
	 * @return String
	 * @exception DBException
	 */
	public String loadObjectId(int fileId, int streamId) throws DBException {

		// Load the object id for the specified file id

		Connection conn = null;
		Statement stmt = null;

		String objectId = null;

		try {

			// Get a connection to the database

			conn = getConnection();
			stmt = conn.createStatement();

			String sql = "SELECT ObjectId FROM " + getObjectIdTableName()
					+ " WHERE FileId = " + fileId + " AND StreamId = "
					+ streamId;

			// DEBUG
			log4j.debug("[mySQL] Load object id SQL: " + sql);

			// Load the mapping record

			ResultSet rs = stmt.executeQuery(sql);

			if (rs.next())
				objectId = rs.getString("ObjectId");
		} catch (SQLException ex) {

			// DEBUG
			log4j.error(ex);

			// Rethrow the exception

			throw new DBException(ex.getMessage());
		} finally {

			// Close the statement

			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException ex) {
				}
			}

			// Release the database connection

			if (conn != null)
				releaseConnection(conn);
		}

		// Return the object id

		return objectId;
	}

	/**
	 * Delete a file id/object id mapping
	 * 
	 * @param fileId
	 *            int
	 * @param streamId
	 *            int
	 * @param objectId
	 *            String
	 * @exception DBException
	 */
	public void deleteObjectId(int fileId, int streamId, String objectId)
			throws DBException {

		// Delete a file id/object id mapping record

		Connection conn = null;
		Statement stmt = null;

		try {

			// Get a connection to the database

			conn = getConnection();
			stmt = conn.createStatement();

			String sql = "DELETE FROM " + getObjectIdTableName()
					+ " WHERE FileId = " + fileId + " AND StreamId = "
					+ streamId;

			// DEBUG
			log4j.debug("[mySQL] Delete object id SQL: " + sql);

			// Delete the mapping record

			stmt.executeUpdate(sql);
		} catch (SQLException ex) {

			// DEBUG
			log4j.error(ex);

			// Rethrow the exception

			throw new DBException(ex.getMessage());
		} finally {

			// Close the statement

			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException ex) {
				}
			}

			// Release the database connection

			if (conn != null)
				releaseConnection(conn);
		}
	}

	/**
	 * Return the data for a symbolic link
	 * 
	 * @param dirId
	 *            int
	 * @param fid
	 *            int
	 * @return String
	 * @exception DBException
	 */
	public String readSymbolicLink(int dirId, int fid) throws DBException {

		// Delete a file id/object id mapping record

		Connection conn = null;
		Statement stmt = null;

		String symLink = null;

		try {

			// Get a connection to the database

			conn = getConnection();
			stmt = conn.createStatement();

			String sql = "SELECT SymLink FROM " + getSymLinksTableName()
					+ " WHERE FileId = " + fid;

			// DEBUG
			log4j.debug("[mySQL] Read symbolic link: " + sql);

			// Load the mapping record

			ResultSet rs = stmt.executeQuery(sql);

			if (rs.next())
				symLink = rs.getString("SymLink");
			else
				throw new DBException("Failed to load symbolic link data for "
						+ fid);
		} catch (SQLException ex) {

			// DEBUG
			log4j.error(ex);

			// Rethrow the exception

			throw new DBException(ex.getMessage());
		} finally {

			// Close the statement

			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException ex) {
				}
			}

			// Release the database connection

			if (conn != null)
				releaseConnection(conn);
		}

		// Return the symbolic link data

		return symLink;
	}

	/**
	 * Delete a symbolic link record
	 * 
	 * @param dirId
	 *            int
	 * @param fid
	 *            int
	 * @exception DBException
	 */
	public void deleteSymbolicLinkRecord(int dirId, int fid) throws DBException {

		// Delete the symbolic link record for a file

		Connection conn = null;
		Statement delStmt = null;

		try {

			// Get a connection to the database

			conn = getConnection();
			delStmt = conn.createStatement();

			String sql = "DELETE FROM " + getSymLinksTableName()
					+ " WHERE FileId = " + fid;

			// DEBUG
			log4j.debug("[mySQL] Delete symbolic link SQL: " + sql);

			// Delete the symbolic link record

			int recCnt = delStmt.executeUpdate(sql);

			// Debug
			log4j.debug("[mySQL] Deleted symbolic link fid=" + fid);
		} catch (SQLException ex) {
			log4j.error(ex);
		} finally {

			// Close the delete statement

			if (delStmt != null) {
				try {
					delStmt.close();
				} catch (Exception ex) {
				}
			}

			// Release the database connection

			if (conn != null)
				releaseConnection(conn);
		}
	}

	/**
	 * Convert a file id to a share relative path
	 * 
	 * @param fileid
	 *            int
	 * @param stmt
	 *            Statement
	 * @return String
	 */
	private String buildPathForFileId(int fileid, Statement stmt) {

		// Build an array of folder names working back from the files id

		StringList names = new StringList();

		try {

			// Loop, walking backwards up the tree until we hit root

			int curFid = fileid;
//			String FileSysTableName = getFileSysTableName(shareName);

			do {

				// Search for the current file record in the database
				String sql = "SELECT pid,name  FROM " + TABLE_WAS_TEMPFILE
						+ " WHERE id = " + curFid + ";";
				log4j.debug("[mySQL] build Path SQL: " + sql);
				ResultSet rs = stmt.executeQuery(sql);
				if (rs.next()) {

					// Get the filename

					names.addString(rs.getString("name"));

					// The directory id becomes the next file id to search for

					curFid = rs.getInt("pid");

					// Close the resultset

					rs.close();
				} else
					return null;

			} while (curFid > 0);
		} catch (SQLException ex) {

			// DEBUG
			log4j.error(ex);
			return null;
		}

		// Build the path string

		StringBuffer pathStr = new StringBuffer(256);
		pathStr.append(FileName.DOS_SEPERATOR_STR);

		for (int i = names.numberOfStrings() - 1; i >= 0; i--) {
			pathStr.append(names.getStringAt(i));
			pathStr.append(FileName.DOS_SEPERATOR_STR);
		}

		// Remove the trailing slash from the path

		if (pathStr.length() > 0)
			pathStr.setLength(pathStr.length() - 1);

		// Return the path string

		return pathStr.toString();
	}

	// 获得存储池卷路径
	private String getRollPath(long fileId, long fileSize, String shareName)
			throws DBException, DiskFullException {
		String rollPath = null;// 卷路径(绝对)

		String equipmentPath = null;// 设备路径
		int poolId = 1;// 默认存储池id
		String poolPath = null;// 存储池路径
		Connection conn = null;
		Statement stmt = null;
		try {
			// Get a connection to the database
			conn = getConnection();
			stmt = conn.createStatement();
			String FileSysTableName = getFileSysTableName(shareName);

			String sql = "SELECT value FROM jweb_config WHERE name = '"
					+ DBUtil.NETDISK_USERFILE_STOREDIR_DEFAULT + "'";
			if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_COMMFILE)) {
				sql = "SELECT value FROM jweb_config WHERE name = '"
						+ DBUtil.NETDISK_COMMFILE_STOREDIR_DEFAULT + "'";
			}
			// Load the file details
			log4j.debug("[mySQL] Get Roll Path SQL: " + sql);
			ResultSet rs = stmt.executeQuery(sql);
			if (rs.next()) {
				poolId = Integer.parseInt(rs.getString("value"));
			}
			rs.close();

			if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_COMMFILE) && fileId > 0) {
				// 查询对应的资料库存储池id
				sql = "SELECT pid1 FROM " + FileSysTableName
						+ " WHERE id = " + fileId;
				log4j.debug("[mySQL] Get pid1 Path SQL: " + sql);
				rs = stmt.executeQuery(sql);
				long pid1 = 0;
				if (rs.next()) {
					pid1 = rs.getLong("pid1");
				}
				if (pid1 > 0) {
					sql = "SELECT pool_id FROM jweb_commonfile_pool t WHERE t.file_id="
							+ pid1;
					log4j.debug("[mySQL] Get pool id SQL: " + sql);
					rs = stmt.executeQuery(sql);
					if (rs.next()) {
						poolId = rs.getInt("pool_id");
					}
				}
			}

			CacheManager cacheManager = CacheManagerUtil.getCacheManager();
			Cache equipmentPathCache = null;
			if (null != cacheManager) {
				equipmentPathCache = cacheManager
						.getCache("EquipmentPathCache");
				Element element = equipmentPathCache.get(poolId);
				if (null != element && element.getObjectValue() != null) {
//					log4j.debug("@@ Cache hit - EquipmentPathCache :" + poolId);
					equipmentPath = element.getObjectValue().toString();
				}
			}
			if (StringUtils.isEmpty(equipmentPath)) {
				sql = "SELECT path FROM jweb_backupequipment WHERE id =(SELECT equipment_id FROM jweb_backupmediapool WHERE id ="
						+ poolId + ")";
				// 设备路径
				log4j.debug("[mySQL] equipmentPath SQL: " + sql);
				ResultSet rs1 = stmt.executeQuery(sql);
				if (rs1.next()) {
					equipmentPath = rs1.getString("path");
					// 添加到缓存
					Element element = new Element(poolId, equipmentPath);
					if (null != equipmentPathCache) {
						equipmentPathCache.put(element);
					}
				}
				rs1.close();
			}

			Cache poolPathCache = null;
			if (null != cacheManager) {
				poolPathCache = cacheManager.getCache("PoolPathCache");
				Element element = poolPathCache.get(poolId);
				if (null != element && element.getObjectValue() != null) {
//					log4j.debug("@@ Cache hit - PoolPathCache :" + poolId);
					poolPath = element.getObjectValue().toString();
				}
			}
			if (StringUtils.isEmpty(poolPath)) {
				sql = "SELECT path as path1 FROM jweb_backupmediapool WHERE id ="
						+ poolId;
				// 池路径
				log4j.debug("[mySQL] poolPath SQL: " + sql);
				ResultSet rs2 = stmt.executeQuery(sql);
				if (rs2.next()) {
					poolPath = rs2.getString("path1");// 返回的可能是多个(poolpath1,poolpath2)
					// 添加到缓存
					Element element = new Element(poolId, poolPath);
					if (null != poolPathCache) {
						poolPathCache.put(element);
					}
				}
				rs2.close();
			}

			if (null == poolPath || null == equipmentPath) {
				log4j
						.error("Failed to load path  for  equipmentPath or poolPath is null; equipmentPath:"
								+ equipmentPath + ", poolPath:" + poolPath);
				throw new DBException(
						"Failed to load path  for  equipmentPath or poolPath is null; equipmentPath:"
								+ equipmentPath + ", poolPath:" + poolPath);
			}

			// 此处还需要修改，判断空间是否充足
			if (poolPath.contains(",")) {
				String[] poolPath1 = poolPath.split(",");
				for (int i = 0; i < poolPath1.length; i++) {
					poolPath = poolPath1[0].trim();// 直接取第一个是不太正确的。第一个空间不够还得按策略继续向后取
					// 并判断逻辑空间
					// ...
				}
			}
			// 判断池空间
			long totalSpace = 0;
			long hasSpace = 0;
			String fSql = "SELECT totalSpace,hasSpace FROM jweb_backupmediaroll WHERE name = '"
					+ poolPath + "'  AND pool_id = " + poolId;
			log4j.debug("[mySQL] roll space SQL: " + fSql);
			ResultSet rs5 = stmt.executeQuery(fSql);
			if (rs5.next()) {
				totalSpace = rs5.getLong("totalSpace");
				hasSpace = rs5.getLong("hasSpace");
			}
			// 当前存储池空间大于 0.1 时候 才可以上传
			if (totalSpace > 0) {
				if (totalSpace - hasSpace / totalSpace < 0.1) {
					log4j.error("ERROR: Roll Space is Full ,poolPath : "
							+ poolPath);
					throw new DiskFullException(
							"ERROR: Roll Space is Full ,poolPath : " + poolPath);
				}
			}
			rollPath = equipmentPath + "/" + poolPath;
			// 判断物理空间
			File path = new File(rollPath);
			if (path.exists()) {
				long usableSpace = path.getUsableSpace();// 可用空间
				if (usableSpace > (fileSize * 2 + 1073741824))// 空间大于文件的三倍+预警空间;//保留空间(临时设置默认1G=1024*1024*1024)
				{

				} else {
					log4j.error("ERROR: Roll Disk is Full ,rollPath : "
							+ rollPath);
					throw new DiskFullException(
							"ERROR: Roll Disk is Full ,rollPath : " + rollPath);
				}
			}
		} catch (SQLException ex) {
			// DEBUG
			log4j.error(ex);
			// Rethrow the exception
			throw new DBException(ex.getMessage());
		} finally {
			// Close the statement
			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException ex) {
				}
			}
			// Release the database connection
			if (conn != null)
				releaseConnection(conn);
		}
		return rollPath;
	}

	/**
	 * 按用户名获得　用户信息
	 * 
	 * @param userName
	 * @return
	 * @throws DBException
	 */
	public UserBean getUserByUsername(String userName) throws DBException {
		if (StringUtils.isEmpty(userName)) {
			log4j.error("参数错误：getUserByUsername userName:" + userName);
			return null;// 用户名为空
		}
		UserBean user = null;
		Connection conn = null;
		Statement stmt = null;
		try {
			CacheManager cacheManager = CacheManagerUtil.getCacheManager();
			Cache cache = null;
			if (null != cacheManager) {
				cache = cacheManager.getCache("UserCache");
				Element element = cache.get(userName);
				if (null != element && element.getObjectValue() != null) {
//					log4j.debug("@@ Cache hit - getUserByUsername :"+ userName);
					return (UserBean) element.getObjectValue();
				}
			}

			// Get a connection to the database
			conn = getConnection();
			stmt = conn.createStatement();

			String sql = "SELECT id,role_id ,department_id,name,password,security_hash,userSpaceSize FROM jweb_users WHERE status>=0 AND lower(username)=lower('"
					+ userName + "')";
			// Load the file details
			log4j.debug("[mySQL] get user SQL: " + sql);
			ResultSet rs = stmt.executeQuery(sql);
			if (rs.next()) {
				user = new UserBean();
				user.setId(rs.getInt("id"));// id
				user.setUsername(userName);// 用户名
				user.setRoleId(rs.getInt("role_id"));// 角色
				user.setDepartmentId(rs.getInt("department_id"));// 部门
				user.setName(rs.getString("name"));// 姓名
				user.setPassword(rs.getString("password"));
				user.setSecurityHash(rs.getString("security_hash"));
				user.setUserSpaceSize(rs.getInt("userSpaceSize"));
			} else {
				log4j.error(" getUserByUsername is not exists ; userName:"
						+ userName);
			}
			rs.close();

			if (null != user) {
				// 添加到缓存
				Element element = new Element(userName, user);
				if (null != cache) {
					cache.put(element);
				}
			}
		} catch (SQLException ex) {
			// DEBUG
			log4j.error(ex);
			// Rethrow the exception
			throw new DBException(ex.getMessage());
		} finally {
			// Close the statement
			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException ex) {
				}
			}
			// Release the database connection
			if (conn != null)
				releaseConnection(conn);
		}
		return user;
	}

	/**
	 * 根据用户id获得工作Ids
	 * 
	 * @param userName
	 * @return
	 * @throws DBException
	 */
	public String getGroupIdsByUserid(int userId) throws DBException {
		String groupIds = "";
		Connection conn = null;
		Statement stmt = null;
		try {
			CacheManager cacheManager = CacheManagerUtil.getCacheManager();
			Cache cache = null;
			if (null != cacheManager) {
				cache = cacheManager.getCache("GroupIdsCache");
				Element element = cache.get(userId);
				if (null != element && element.getObjectValue() != null) {
//					log4j.debug("@@ Cache hit - getGroupIdsByUserid :"+ userId);
					return element.getObjectValue().toString();
				}
			}

			// Get a connection to the database
			conn = getConnection();
			stmt = conn.createStatement();

			String gSql = "SELECT group_id FROM jweb_user_groups WHERE user_id = "
					+ userId;
			log4j.debug("[mySQL] get group_id SQL: " + gSql);
			ResultSet rsGroup = stmt.executeQuery(gSql);
			while (rsGroup.next()) {
				if (groupIds.length() > 0) {
					groupIds += ",";
				}
				groupIds += rsGroup.getInt("group_id");
			}
			rsGroup.close();

			if (StringUtils.isNotEmpty(groupIds)) {
				// 添加到缓存
				Element element = new Element(userId, groupIds);
				if (null != cache) {
					cache.put(element);
				}
			}
		} catch (SQLException ex) {
			// DEBUG
			log4j.error(ex);
			// Rethrow the exception
			throw new DBException(ex.getMessage());
		} catch (Exception ex) {
			log4j.error(ex);
		} finally {
			// Close the statement
			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException ex) {
				}
			}
			// Release the database connection
			if (conn != null)
				releaseConnection(conn);
		}
		return groupIds;
	}

	/**
	 * 根据部门id获得部门ids树
	 * 
	 * @param userName
	 * @return
	 * @throws DBException
	 */
	public String getDepartmentPathById(int departmentId) throws DBException {
		String departmentPath = "";

		Connection conn = null;
		Statement stmt = null;
		try {
			CacheManager cacheManager = CacheManagerUtil.getCacheManager();
			Cache cache = null;
			if (null != cacheManager) {
				cache = cacheManager.getCache("DepartmentPathCache");
				Element element = cache.get(departmentId);
				if (null != element && element.getObjectValue() != null) {
					log4j.debug("@@ Cache hit - getDepartmentPathById :"
							+ departmentId + ", " + element.getObjectValue());
					return element.getObjectValue().toString();
				}
			}

			// Get a connection to the database
			conn = getConnection();
			stmt = conn.createStatement();

			// 查询部门的parentId
			String dqSql = "SELECT path FROM jweb_department WHERE department_id ="
					+ departmentId;
			log4j.debug("[mySQL] department path SQL: " + dqSql);
			ResultSet rsDepart = stmt.executeQuery(dqSql);
			if (rsDepart.next()) {
				departmentPath = rsDepart.getString("path");
			}
			rsDepart.close();

			if (StringUtils.isNotEmpty(departmentPath)) {
				// 添加到缓存
				Element element = new Element(departmentId, departmentPath);
				if (null != cache) {
					cache.put(element);
				}
			}
		} catch (SQLException ex) {
			// DEBUG
			log4j.error(ex);
			// Rethrow the exception
			throw new DBException(ex.getMessage());
		} catch (Exception ex) {
			log4j.error(ex);
		} finally {
			// Close the statement
			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException ex) {
				}
			}
			// Release the database connection
			if (conn != null)
				releaseConnection(conn);
		}
		return departmentPath;
	}

	/**
	 * rootPath_bak 值暂不用
	 */
	public synchronized int saveFileArchive(String rootPath_bak, String tempDir, int fileId,
			File uFile, String shareName, FileSegment fileSeg, String ipAddress)
			throws SQLException, DBException, IOException {

		String fileName = uFile.getName();
		log4j.error("DBI#saveFileArchive, time:" + System.currentTimeMillis()
				+ ", fname:" + fileName + ", fileId:" + fileId);
		
		String FileRevisionTableName = getRevisionTableName(shareName);
		String FileSysTableName = getFileSysTableName(shareName);
		/*
		 * //判断如果是Office临时文件，则不创建索引到数据库 Pattern temporaryPattern =
		 * Pattern.compile(temporaryReg); Matcher fMatcher =
		 * temporaryPattern.matcher(fileName); if(fMatcher.find()) {
		 * log4j.debug("saveFileArchive不创建Word临时文件到数据库索引 fname:" + fileName);
		 * return -1; }
		 */
		String rootPath = "";
		String description = "";
		int userId = 0;
		Connection conn = null;
		Statement stmt = null;
		java.util.Date nowDate = new java.util.Date();
		String sql= "";
		int move_id = fileId;
		String ext = DiskUtil.getExt(fileName);
		
		int officeId = 0;
		String path ="";
		
        
		try {
			
			// Get a connection to the database
			conn = getConnection();
			stmt = conn.createStatement();
			ResultSet rs;
			ResultSet rRs;
			boolean uploadFileExist = false;
			
			Map needDelMap=new HashMap();                
			
				// Get the current file details
				int oldFileId = 0;
				int reallyFileId = 0;
				if(null != uFile && uFile.exists())
				{
					uploadFileExist = true;//文件存在
					fileName = uFile.getName();
					if(DBUtil.SUPPORT_EXT.contains(ext)||DBUtil.CAD_EXT.contains(ext)||DBUtil.PHOTO_EXT.contains(ext)||"txt".equals(ext))
					{
						if(fileName.contains("_")&&fileName.length()>=10)
						{
							String name = fileName.substring(0,fileName.indexOf("_"));
							if(StringUtils.isNumeric(name))
							{
								fileId = Integer.parseInt(name);
							}
						}
					}
					else if(DBUtil.pre_Move.contains(ext))
					{
						if(fileName.contains("_")&&fileName.length()>=10)
						{
							String name = fileName.substring(0,fileName.indexOf("_"));
							if(fileName.startsWith("~$"))
							{
								fileId = 0;
							}
							else if(StringUtils.isNumeric(name))
							{
								fileId = Integer.parseInt(name);
							}
						}
					}
					else
					{
						
					}
		
					String fileMD5 = MD5Util.getFileMD5(uFile);// 上传文件的MD5值
					boolean fileExist = false;
//					int revisionCount = 1;
					long pid = 0;
					
					conn.setAutoCommit(false);
					synchronized (this)// 暂时改为同步。 避免产生重复文件
					{
						// 如果已经存在且内容相同则不更新，直接成功
						String existMd5 ="";
						long existSize = 0;
						int existStatus = 0;
						//(select fid from  " + FileSysTableName + " where id=
						sql = "SELECT userId,id,pid,name,path ,status,revisionCount,revision,md5,size FROM " +FileSysTableName+" WHERE id ="+ fileId;
						log4j.debug("[mySQL] select status SQL: " + sql);
						rs = stmt.executeQuery(sql);
						if (rs.next()) {
							userId = rs.getInt("userId");
							int status = rs.getInt("status");
							String fname = rs.getString("name");
							pid = rs.getLong("pid");
							fileName = fname;
							existMd5 = rs.getString("md5");
							existSize = rs.getLong("size");
							existStatus = rs.getInt("status");
							reallyFileId = rs.getInt("id");
							path = rs.getString("path");
//							pid1 = rs.getInt("pid1");
//							pid2 = rs.getInt("pid2");
							
							String ext1 = DiskUtil.getExt(fname);
							if(DBUtil.pre_Move.contains(ext)&&DBUtil.pre_Move.contains(ext1)&&ext1.equalsIgnoreCase(ext)==false)
							{
								String hql = "SELECT userId,id,pid,name,path,status,revisionCount,revision,md5,size FROM " +FileSysTableName+"  WHERE id ="+ move_id;
								rRs = stmt.executeQuery(hql);
								if(rRs.next())
								{
								userId = rRs.getInt("userId");
								status = rRs.getInt("status");
								existMd5 = rRs.getString("md5");
								existSize = rRs.getLong("size");
								reallyFileId = rRs.getInt("id");
								fname = rRs.getString("name");
//								pid2 = rRs.getInt("id");
								fileName = fname.substring(fname.indexOf("_")+1,fname.length());
							    }
							}
							else if(DBUtil.SUPPORT_EXT.contains(ext1)||DBUtil.CAD_EXT.contains(ext1)||DBUtil.PHOTO_EXT.contains(ext1))
							{ 
								if(fileName.contains("_")&&fileName.length()>=10)
								{
									// 文件名可能包含_,从而出现多个_,id应该取第一个_d的部分
									String name = fileName.substring(0,fileName.indexOf("_"));
									if(StringUtils.isNumeric(name)){
										officeId = Integer.parseInt(name);
										if(officeId >0)
										{
											String hql = "SELECT userId,id,pid,name,path,status,revisionCount,revision,md5,size FROM " +FileSysTableName+"  WHERE id ="+ officeId;

//											if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_RECIVEFILE)) {
//												hql += "permissions,fileId,";
//											}
//											hql += ",revision,md5,size FROM " +FileSysTableName+"  WHERE id ="+ officeId;
											rs = stmt.executeQuery(hql);
											if(rs.next()){
												userId = rs.getInt("userId");
												status = rs.getInt("status");
												fname = rs.getString("name");
												pid = rs.getLong("pid");
												existMd5 = rs.getString("md5");
												existSize = rs.getLong("size");
												existStatus = rs.getInt("status");
												existStatus = rs.getInt("status");

//												if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_RECIVEFILE)) {
//													permissions = rs.getString("permissions");
//													srcFileId = rs.getString("fileId");
//												}
												path = rs.getString("path");
//												pid1 = rs.getInt("pid1");
//												pid2 = rs.getInt("pid2");
												fileName = fname;
												reallyFileId = rs.getInt("id");
											}
										}
									}
								}
								
							}
							// 如果已经存在且内容相同则不更新，直接成功
							if (status < 0) {
								// 已经删除掉新的，使用旧的fileId，则获得旧的id
								sql = "SELECT id,pid,name,status,revisionCount,revision,md5,size FROM " +FileSysTableName+" WHERE status>=0 AND pid = " + pid + " AND LOWER(name)=LOWER('" + fileName  + "') ORDER BY revisionCount DESC,id ASC";
								log4j.debug("[mySQL] old file id SQL: " + sql);
								rs = stmt.executeQuery(sql);
								
								if (rs.next()) {
									oldFileId = rs.getInt("id");
									
									// 如果已经存在且内容相同则不更新，直接成功
									existMd5 = rs.getString("md5");
									existSize = rs.getLong("size");
									existStatus = rs.getInt("status");
									log4j.info("重新查询到的fileId:" + fileId);
								}
								int seconds = 0;// 时间限制3秒
								while (oldFileId == 0 && seconds < 3)// 延时１秒取状态，最多重复5次
								{
									try {
										Thread.sleep(500);
										log4j.debug("[mySQL] old file id[" + seconds + "] SQL: " + sql);
										rs = stmt.executeQuery(sql.replace("status>=0", "status>=-1"));
										if (rs.next()) {
											oldFileId = rs.getInt("id");
											
											// 如果已经存在且内容相同则不更新，直接成功
											existMd5 = rs.getString("md5");
											existSize = rs.getLong("size");
											existStatus = rs.getInt("status");
										}
										seconds++;
										if (oldFileId > 0) {
											break;
										} else {
											if (seconds > 4) {
												log4j.error("DBI#saveJarData,fileList xx 重新查询到的fileId，等待时间过长!");
											}
										}
									} catch (Exception e) {
										e.printStackTrace();
									}
								}
								log4j.debug("DBI#saveJarData,fileList xx 本文件已经删除,fileId:" + fileId + "，重新查询正确的文件ID:" + oldFileId);
								if (oldFileId > 0) {
									log4j.debug("DBI#saveJarData,fileList xx "+"########:");
									fileId = oldFileId;
								} else {
									log4j.error("DBI#saveJarData,fileList xxx 已经删除状态的,fileId:" + fileId + "，未得到正确的旧文件ID:" + oldFileId);
								}
							}
						}
						rs.close();
						
						if (existStatus > -1 && existSize == uFile.length() && fileMD5.length()==32 && fileMD5.equalsIgnoreCase(existMd5) && uFile.length() > 0) {
							String fSql = "update " +FileSysTableName+" set lastModified=" + nowDate.getTime() + " WHERE id =" + reallyFileId;
							log4j.debug("[mySQL] 更新存在的文件 SQL: " + fSql);
							stmt.executeUpdate(fSql);
							// DEBUG
							log4j .debug("DBI#saveJarData,fileList xx 文件已经存在且内容相同 ,filename= " + fileName 	+ ",size=" 	+ uFile.length() + ",fileMD5=" + fileMD5);
							fileExist = true;
							// 不能直接返回，否则后续队列会跳过执行
						} else {
							log4j.info("DBI#saveJarData,fileList xx 文件存在但内容不等 ,filename= " + fileName + ",existStatus:" + existStatus + ",size:" + existSize + "=" + uFile.length() 	+ ",fileMD5:" + existMd5 + "=" + fileMD5);
							fileExist = false;
//							revisionCount =revisionCount+1;
							// 因提前插入了版本，所以不用更新此值
						}
						
						if (!fileExist && uploadFileExist) {
							String archiveFilePath = DiskUtil.getArchiveFilePath(fileMD5);
							if(StringUtils.isEmpty(rootPath))
							{
								//重新获得rollPath;
								try {
									rootPath = this.getRollPath(pid, uFile.length(), shareName);// 获得存储池卷目录
								} catch (DBException e) {
									log4j.error("Error 获得存储池卷目录失败 rootPath= " + rootPath);
									throw new DBException(e.getMessage());
								} catch (DiskFullException e) {
									log4j.error("Roll Disk is Full ,rootPath : " + rootPath);
									throw new DiskFullException( "Roll Disk is Full ,rootPath : " + rootPath);
								}
							}
							File saveFile = new File(rootPath + archiveFilePath); // 目标存储文件
							try {
								AESUtil.encryptFile(uFile, saveFile);
							} catch (Exception e) {
								log4j.error("DBI#saveJarData,fileList xx ; fileId:" 	+ fileId + ", ufile:" + uFile.getAbsolutePath() + "  ; " + e.getMessage());
							}
							
							try{
								FileParser fp = new FileParser(uFile, fileName);// 提取内容
								description = fp.getDescription();// -- 此处 desc竟然是空   
								description = checkNameForSpecialChars(description);//替换特殊符
							}catch(Exception e){
								e.printStackTrace();
							}
							
							
//							String maxRevisionSql = "SELECT max(id) as id FROM " + FileRevisionTableName + " WHERE status>=0 AND fileId=" + fileId;
							String maxRevisionSql = "SELECT max(revision) as revision FROM " + FileRevisionTableName + " WHERE status>=0 AND fileId=" + reallyFileId;
							long maxRevision = 0;
							log4j.debug("[mySQL] max maxRevision SQL: " + maxRevisionSql);
							rRs = stmt.executeQuery(maxRevisionSql);
							if (rRs.next()) {
								maxRevision = rRs.getLong("revision");
							}
//							rRs.close();
							if (StringUtils.isEmpty(fileMD5)) {
								fileMD5 = "md51";
							}
							log4j.debug("\n***********************上传/更新文件***********************fileId:"+fileId+" ;revision:"+maxRevision);
							log4j.debug(description);
							log4j.debug("\n***********************");
							String fSql = "";
							try {
								if (maxRevision > 0) {
									// 更新
//									sql = "UPDATE " + FileRevisionTableName + " set status=0, storePath='" + archiveFilePath + "',md5='" + fileMD5 + "',size=" + uFile.length()
//											+ ",lastModified=" + nowDate.getTime() + ",revision=" + revision + ",revisionCount="
//											+ revisionCount + ",rollPath='" + rootPath + "' WHERE revision=" + maxRevision;
									if(existMd5.length()==32 && oldFileId==-10) //  
									{
											//复制
											sql ="INSERT INTO "+FileRevisionTableName+" (fileid,md5,size,lastModified,userId,revision,revisionCount,status,rollPath,storePath)" +
											" SELECT distinct fileid,md5,size,lastModified,userId,revision+1 as revision,revisionCount+1 as revisionCount, status,rollPath,storePath from "+FileRevisionTableName+" WHERE fileId = " + reallyFileId+" AND revision=" + maxRevision;
											log4j.debug("[mySQL]  UPDATE file 复制版本 :\n" + sql);
											stmt.executeUpdate(sql);
											
											maxRevision +=1;
											fSql = "update " +FileSysTableName+" set revision="+maxRevision+",revisionCount=revisionCount+1 WHERE id =" + reallyFileId;
											log4j.debug("[mySQL] UPDATE file 更新版本号 :\n" + fSql);
											stmt.executeUpdate(fSql);
									}
									sql = "UPDATE " + FileRevisionTableName + " set status=0, storePath='" + archiveFilePath + "',md5='" + fileMD5 + "',size=" + uFile.length()
									+ ",lastModified=" + nowDate.getTime() + ",rollPath='" + rootPath + "' WHERE fileId=" + reallyFileId;
									
									if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_USERFILE) || shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_RECIVEFILE)) {
										sql += " AND revision=" + (maxRevision);// 编辑我的文件的时候需要做revision判断
									}
									log4j.debug("[mySQL] UPDATE revision :\n" + sql);
									if (stmt.executeUpdate(sql) > 0) {
//										sql = "DELETE FROM " + FileRevisionTableName + " WHERE size=0 AND length(md5)<10 AND fileId=" + fileId + " AND id<" + maxRevisionId;
//										log4j.debug("[mySQL] DELETE null revision :" + sql);
//										stmt.executeUpdate(sql);
										needDelMap.put(fileId, maxRevision);//操作完再统一删除
									}
									if(oldFileId>0 && reallyFileId!=oldFileId)
									{
										sql = "UPDATE " + FileRevisionTableName + " set status=0, storePath='" + archiveFilePath + "',md5='" + fileMD5 + "',size=" + uFile.length()
										+ ",rollPath='" + rootPath + "' WHERE fileId=" + oldFileId;// +" AND revision=" + maxRevision;
										log4j.debug("[mySQL] UPDATE old File revision :\n" + sql);
										stmt.executeUpdate(sql);
									}
//									fSql = "update " + FileSysTableName + " set md5 ='" + fileMD5 + "',size = " + uFile.length() + ",revision = " + revision + ",revisionCount =" + revisionCount
//											+ ",description ='" + description + "',lastModified=" + nowDate.getTime() + ",status =0 WHERE id =" + fileId;
									fSql = "update " +FileSysTableName+" set name ='"+fileName+"',path='"+path+"',md5 ='" + fileMD5 + "',size = " + uFile.length() + ",description ='" + checkNameForSpecialChars(description) + "',lastModified=" + nowDate.getTime() + ",status =0 WHERE id =" + reallyFileId;
									if(oldFileId>0 && fileId!=oldFileId)
									{
										fSql = "update " +FileSysTableName+" set md5 ='" + fileMD5 + "',size = " + uFile.length() + ",description ='" + checkNameForSpecialChars(description) + "',lastModified=" + nowDate.getTime() + " WHERE id =" + reallyFileId;
									}

									log4j.debug("[mySQL] UPDATE file :\n" + fSql);
									
									int update = stmt.executeUpdate(fSql);
									
									
									if (update > 0) {
										/**
										 * 如果是共享文件的修改, 则需要同时考虑来源文件的修改.
										 */
//										if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_RECIVEFILE)) {
//											/**
//											 * 来源文件应该固定为 FileSysTable_MYFILE 吧
//											 */
//											if (permissions!=null && permissions.contains("w")) {
//												fSql = "update " +FileSysTable_MYFILE+" set name ='"+fileName+"',pid="+pid+",path='"+path+"',md5 ='" + fileMD5 + "',size = " + uFile.length() + ",description ='" + checkNameForSpecialChars(description) + "',lastModified=" + nowDate.getTime() + ",status =0 WHERE id =" + srcFileId;
//												stmt.executeUpdate(fSql);
//											}
//											
//											sql = "UPDATE " + FileRevisionTable_MYFILE + " set status=0, storePath='" + archiveFilePath + "',name='"+fileName+"',md5='" + fileMD5 + "',size=" + uFile.length()
//													+ ",lastModified=" + nowDate.getTime() + ",description='" + checkNameForSpecialChars(description) + "',rollPath='" + rootPath + "' WHERE fileId=" + srcFileId;// +" AND revision=" + maxRevision;
//													log4j.debug("[mySQL] UPDATE revision :\n" + sql);
//											if (stmt.executeUpdate(sql) > 0) {
////														sql = "DELETE FROM " + FileRevisionTableName + " WHERE size=0 AND length(md5)<10 AND fileId=" + fileId + " AND id<" + maxRevisionId;
////														log4j.debug("[mySQL] DELETE null revision :" + sql);
////														stmt.executeUpdate(sql);
//												needDelMap.put(fileId, maxRevision);//操作完再统一删除
//											}
////											if(oldFileId>0 && reallyFileId!=oldFileId)
////											{
////												sql = "UPDATE " + FileRevisionTable_MYFILE + " set status=0, storePath='" + archiveFilePath + "',md5='" + fileMD5 + "',size=" + uFile.length()
////												+ ",description='" + checkNameForSpecialChars(description) + "',rollPath='" + rootPath + "' WHERE fileId=" + oldFileId;// +" AND revision=" + maxRevision;
////												log4j.debug("[mySQL] UPDATE old File revision :\n" + sql);
////												stmt.executeUpdate(sql);
////											}
//											
//										}
									}
									
									// 日志
									if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_RECIVEFILE)) {
										String sqlLog = "insert into jweb_file_log (user_id,path_id,file_id,file_revision,note,ip,create_date,agent_client,operation,content_length,read_length) values ("
												+ userId + "," + pid + "," + fileId + "," + maxRevision + ",'CIFS更新文件','','" + sf.format(nowDate) + "','CIFS','w'," + 0 + "," + 0 + ")";
										log4j.debug("[mySQL] sqlLog SQL:" + sqlLog);
										stmt.executeUpdate(sqlLog);
									} else if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_COMMFILE)) {
										String sqlLog = "insert into jweb_commonfile_log (user_id,path_id,file_id,file_revision,note,ip,create_date,agent_client,operation,content_length,read_length) values ("
												+ userId + "," + pid + "," + fileId + "," + maxRevision + ",'CIFS更新文件','','" + sf.format(nowDate) + "','CIFS','w'," + 0 + "," + 0 + ")";
										log4j.debug("[mySQL] sqlLog SQL:" + sqlLog);
										stmt.executeUpdate(sqlLog);
									}
									
//									alreadyIds +=","+reallyFileId+",";//已经更新的fileId
									if(reallyFileId != move_id){
										 sql ="DELETE FROM " +FileSysTableName+" WHERE id = "+move_id;
										stmt.executeUpdate(sql);
									}
								} else {
									sql = "UPDATE " + FileRevisionTableName + " set status=-1, storePath='" + archiveFilePath + "',md5='" + fileMD5 + "',size=" + uFile.length()
									+ ",lastModified=" + nowDate.getTime() + ",rollPath='" + rootPath + "' WHERE fileId=" + reallyFileId;
									stmt.executeUpdate(sql);
									log4j.error("DBI#saveJarData,fileList xx ERROR 被更新的版本记录已经不存在，不执行更新; fileId:" + fileId);
								}
							} catch (SQLException e) {
								if (conn != null) conn.rollback();
								log4j.error("DBI#saveJarData,xxx SQLException error ; \nsql:"+ sql+ "\nfSql:"+ fSql+ "  ; "+ e.getMessage());
							}
						} else if(uploadFileExist==false) {
							log4j.error("DBI#saveJarData,xxx ERROR to store  update file not exists fileId:" + fileId + ", fileExist:" + fileExist + ", uploadFileExist:" + uploadFileExist);
						} else
						{
							log4j.warn("DBI#saveJarData,xxx ERROR to store  file exists fileId:" + fileId + ", fileExist:" + fileExist + ", uploadFileExist:" + uploadFileExist);
						}
					}
					conn.commit();
				}
				else
				{
					//删除不必要的文件
//					if(!alreadyIds.contains(","+reallyFileId+","))
//					{
						sql = "UPDATE " +FileSysTableName+" SET status = -2,lastModified=" + nowDate.getTime() + " WHERE status>-1 AND id = " + reallyFileId;
						log4j.debug("[mySQL] DELETE uploadFile not exists :" + sql);
						stmt.executeUpdate(sql);
//					}
					log4j.error("DBI#saveJarData,xxx uploadFile not exists : fileId:" +  fileId + ", file path:" + (null != uFile?uFile.getAbsolutePath():"null"));
				}
			
			//删除不需要的
			if(null != needDelMap && needDelMap.size()>0)
			{
				String[] delArr = null;
				Iterator iterator = needDelMap.keySet().iterator();                
	            while (iterator.hasNext()) {    
	            	Object key = iterator.next();    
	            	sql = "UPDATE " + FileRevisionTableName + " SET status=-2 WHERE size=0 AND length(md5)<10 AND fileId=" + key + " AND revision<" +needDelMap.get(key);
					log4j.debug("[mySQL] DELETE null revision :" + sql);
					stmt.executeUpdate(sql);
	            }
			}
		
			
		} catch (SQLException ex) {
			if (conn != null) conn.rollback();
			// DEBUG
			log4j.error(ex);
			// Rethrow the exception
			throw new DBException(ex.getMessage());
		} finally {
			// Close the statement
			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException ex) {
				}
			}
			// Release the database connection
			if (conn != null) {
				try {
					conn.setAutoCommit(true);
				} catch (SQLException e) {
					e.printStackTrace();
				}
				releaseConnection(conn);
			}
		}
		return StsSuccess;
	}
	
	
	/**
	 * 获得文件的权限
	 */
	private String filePermissions(long permFileId,int userId,String groupIds,String departmentPath) throws DBException {
		String permissions = "";
		Connection conn = null;
		Statement stmt = null;

		try {
			conn = getConnection();
			stmt = conn.createStatement();			
//			//用户权限
//			String psql_u = "SELECT * FROM jweb_systemrole_commonfile WHERE role_type=0 AND file_id ="+permFileId+" AND role_id="+userId;
//			//工作组权限
//			if (StringUtils.isNotEmpty(groupIds)) {
//				String psql_g = "SELECT * FROM jweb_systemrole_commonfile WHERE role_type=1 AND file_id ="+permFileId+" AND role_id in("+groupIds+")";
//			}
//			//部门权限
//			if (StringUtils.isNotEmpty(departmentPath)) {
//				String psql_d = "SELECT * FROM jweb_systemrole_commonfile WHERE role_type=2 AND file_id ="+permFileId+" AND role_id in("+departmentPath+")";
//			}			
			StringBuffer Dstr = new StringBuffer();
			Dstr.append("SELECT * FROM jweb_systemrole_commonfile WHERE ");
			//用户权限
			Dstr.append("  (role_type=0 AND file_id =").append(permFileId).append(" AND role_id=").append(userId).append(") ");
			//工作组权限
			if (StringUtils.isNotEmpty(groupIds)) {
			Dstr.append(" OR (role_type=1 AND file_id =").append(permFileId).append(" AND role_id in(").append(groupIds).append(") )");
			}
			//部门权限
			if (StringUtils.isNotEmpty(departmentPath)) {
			Dstr.append(" OR (role_type=2 AND file_id =").append(permFileId).append(" AND role_id IN ( "+ departmentPath + ") )");
			}
			log4j.debug("[mySQL] Dstr SQL:" + Dstr);
			ResultSet rsPerm = stmt.executeQuery(Dstr.toString());
			String value = "";
			while (rsPerm.next()) {
				value = rsPerm.getString("role_value");
				permissions = StringUtil.combineChar(permissions, value);//集合权限
			}
			rsPerm.close();
		} catch (Exception ex) {
			log4j.error("DBI#fileExists 异常:",ex);
		} finally {
			// Close the statement

			if (stmt != null) {
				try {
					stmt.close();
				} catch (Exception ex) {
				}
			}

			// Release the database connection

			if (conn != null)
				releaseConnection(conn);
		}

		// Return the permissions

		return permissions;
	}

	public synchronized int saveJarData(ArrayList<File> jarFileList,
			DBDataDetailsList fileList, String shareName) throws DBException,
			IOException {
		log4j.debug("DBI#saveJarData, time:" + System.currentTimeMillis() +" ; jarFileList:"+jarFileList+" ;fileList:"+fileList);
		// Write the Jar file to the blob field in the Jar data table
		String FileSysTableName = getFileSysTableName(shareName);
		String FileRevisionTableName = getRevisionTableName(shareName);
		Connection conn = null;
		PreparedStatement istmt = null;
		Statement stmt = null;

		int jarId = -1;
		String rootPath = "";
		String description = "";
		int userId = 0;
		java.util.Date nowDate = new java.util.Date();
		String sql= "";
		int fileId = -1;
		String fileName = "";
		String alreadyIds = "";
		int officeId = 0;
//		int pid1 = 0;
//		int pid2 = 0;
		String path ="";
		int move_id =0;
		try {
			// Get a connection to the database
			conn = getConnection();
			conn.setAutoCommit(false);
			stmt = conn.createStatement();
			ResultSet rs;
			ResultSet rRs;
			boolean uploadFileExist = false;
			
			Map needDelMap=new HashMap();                
			
			for (int i = 0; i < fileList.numberOfFiles(); i++) {

				// Get the current file details
				DBDataDetails dbDetails = fileList.getFileAt(i);
				move_id=dbDetails.getFileId();
				fileId = dbDetails.getFileId();
				int oldFileId = 0;
				int reallyFileId = 0;
				File uFile = jarFileList.get(i);
				if(null != uFile && uFile.exists())
				{
					uploadFileExist = true;//文件存在
					fileName = uFile.getName();
					String ext = DiskUtil.getExt(fileName);
					if(DBUtil.SUPPORT_EXT.contains(ext)||DBUtil.CAD_EXT.contains(ext)||DBUtil.PHOTO_EXT.contains(ext)||"txt".equals(ext))
					{
						if(fileName.contains("_")&&fileName.length()>=10)
						{
							String name = fileName.substring(0,fileName.indexOf("_"));
							if(StringUtils.isNumeric(name))
							{
								fileId = Integer.parseInt(name);
							}
						}
					}
					else if(DBUtil.pre_Move.contains(ext))
					{
						if(fileName.contains("_")&&fileName.length()>=10)
						{
							String name = fileName.substring(0,fileName.indexOf("_"));
							if(fileName.startsWith("~$"))
							{
								fileId = 0;
							}
							else if(StringUtils.isNumeric(name))
							{
								fileId = Integer.parseInt(name);
							}
						}
					}
					else
					{
						
					}
		
					String fileMD5 = MD5Util.getFileMD5(uFile);// 上传文件的MD5值
					boolean fileExist = false;
					int revisionCount = 1;
					long pid = 0;
					String permissions = "r";
					String srcFileId = ""; // -- 如何获取？？？
				
					synchronized (this)// 暂时改为同步。 避免产生重复文件
					{
						// 如果已经存在且内容相同则不更新，直接成功
						String existMd5 ="";
						long existSize = 0;
						int existStatus = 0;
						
						if(ext.equalsIgnoreCase("tmp")) {
							sql = "SELECT latestFileId FROM " +FileSysTableName+" WHERE status = -2 AND id ="+ fileId;
							log4j.debug("[mySQL] RRRRRRRRRRRRRRRRReselect status SQL: " + sql);
							rs = stmt.executeQuery(sql);
							if (rs.next()) {
								fileId = rs.getInt("latestFileId");
							}
						}
						//(select fid from  " + FileSysTableName + " where id=
						sql = "SELECT userId,id,pid,name,path ,status,revisionCount,revision,md5,size FROM " +FileSysTableName+" WHERE id ="+ fileId;
						log4j.debug("[mySQL] select status SQL: " + sql);
						rs = stmt.executeQuery(sql);
						if (rs.next()) {
							userId = rs.getInt("userId");
							int status = rs.getInt("status");
							String fname = rs.getString("name");
							pid = rs.getLong("pid");
							fileName = fname;
							existMd5 = rs.getString("md5");
							existSize = rs.getLong("size");
							existStatus = rs.getInt("status");
							reallyFileId = rs.getInt("id");
							revisionCount = rs.getInt("revisionCount");
							path = rs.getString("path");
							
//							pid1 = rs.getInt("pid1");
//							pid2 = rs.getInt("pid2");
							
							String ext1 = DiskUtil.getExt(fname);
							if(DBUtil.pre_Move.contains(ext)&&DBUtil.pre_Move.contains(ext1)&&ext1.equalsIgnoreCase(ext)==false)
							{
								String hql = "SELECT userId,id,pid,name,path,status,revisionCount,revision,md5,size FROM " +FileSysTableName+"  WHERE id ="+ move_id;
								rRs = stmt.executeQuery(hql);
								if(rRs.next())
								{
								userId = rRs.getInt("userId");
								status = rRs.getInt("status");
								existMd5 = rRs.getString("md5");
								existSize = rRs.getLong("size");
								reallyFileId = rRs.getInt("id");
								fname = rRs.getString("name");
								revisionCount = rs.getInt("revisionCount");
//								pid2 = rRs.getInt("id");
								fileName = fname.substring(fname.indexOf("_")+1,fname.length());
							    }
							}
							else if(DBUtil.SUPPORT_EXT.contains(ext1)||DBUtil.CAD_EXT.contains(ext1)||DBUtil.PHOTO_EXT.contains(ext1))
							{ 
								if(fileName.contains("_")&&fileName.length()>=10)
								{
									// 文件名可能包含_,从而出现多个_,id应该取第一个_的部分
									String name = fileName.substring(0,fileName.indexOf("_"));
									if(StringUtils.isNumeric(name)){
										officeId = Integer.parseInt(name);
										if(officeId >0)
										{
											String hql = "SELECT userId,id,pid,name,path,status,revisionCount,revision,md5,size FROM " +FileSysTableName+"  WHERE id ="+ officeId;

//											if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_RECIVEFILE)) {
//												hql += "permissions,fileId,";
//											}
//											hql += ",revision,md5,size FROM " +FileSysTableName+"  WHERE id ="+ officeId;
											rs = stmt.executeQuery(hql);
											if(rs.next()){
												userId = rs.getInt("userId");
												status = rs.getInt("status");
												fname = rs.getString("name");
												pid = rs.getLong("pid");
												existMd5 = rs.getString("md5");
												existSize = rs.getLong("size");
												existStatus = rs.getInt("status");
												existStatus = rs.getInt("status");
												revisionCount = rs.getInt("revisionCount");

//												if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_RECIVEFILE)) {
//													permissions = rs.getString("permissions");
//													srcFileId = rs.getString("fileId");
//												}
												path = rs.getString("path");
//												pid1 = rs.getInt("pid1");
//												pid2 = rs.getInt("pid2");
												fileName = fname;
												reallyFileId = rs.getInt("id");
											}
										}
									}
								}
								
							}
							// 如果已经存在且内容相同则不更新，直接成功
						}
						rs.close();
						
						if (existStatus > -1 && existSize == uFile.length() && fileMD5.length()==32 && fileMD5.equalsIgnoreCase(existMd5) && uFile.length() > 0) {
							String fSql = "update " +FileSysTableName+" set lastModified=" + nowDate.getTime() + " WHERE id =" + reallyFileId;
							log4j.debug("[mySQL] 更新存在的文件 SQL: " + fSql);
							stmt.executeUpdate(fSql);
							// DEBUG
							log4j .debug("DBI#saveJarData,fileList["+i+"] 文件已经存在且内容相同 ,filename= " + fileName 	+ ",size=" 	+ uFile.length() + ",fileMD5=" + fileMD5);
							fileExist = true;
							// 不能直接返回，否则后续队列会跳过执行
						} else {
							log4j.info("DBI#saveJarData,fileList["+i+"] 文件存在但内容不等 ,filename= " + fileName + ",existStatus:" + existStatus + ",size:" + existSize + "=" + uFile.length() 	+ ",fileMD5:" + existMd5 + "=" + fileMD5);
							fileExist = false;
//							revisionCount =revisionCount+1;
							// 因提前插入了版本，所以不用更新此值
						}
						
						if (!fileExist && uploadFileExist) {
							String archiveFilePath = DiskUtil.getArchiveFilePath(fileMD5);
							if(StringUtils.isEmpty(rootPath))
							{
								//重新获得rollPath;
								try {
									rootPath = this.getRollPath(pid, uFile.length(), shareName);// 获得存储池卷目录
								} catch (DBException e) {
									log4j.error("Error 获得存储池卷目录失败 rootPath= " + rootPath);
									throw new DBException(e.getMessage());
								} catch (DiskFullException e) {
									log4j.error("Roll Disk is Full ,rootPath : " + rootPath);
									throw new DiskFullException( "Roll Disk is Full ,rootPath : " + rootPath);
								}
							}
							File saveFile = new File(rootPath + archiveFilePath); // 目标存储文件
							try {
								AESUtil.encryptFile(uFile, saveFile);
							} catch (Exception e) {
								log4j.error("DBI#saveJarData,fileList["+i+"] ; fileId:" 	+ fileId + ", ufile:" + uFile.getAbsolutePath() + "  ; " + e.getMessage());
							}
							
							try{
								FileParser fp = new FileParser(uFile, fileName);// 提取内容
								description = fp.getDescription();
								description = checkNameForSpecialChars(description);//替换特殊符
							}catch(Exception e){
								e.printStackTrace();
							}
							
							
//							String maxRevisionSql = "SELECT max(id) as id FROM " + FileRevisionTableName + " WHERE status>=0 AND fileId=" + fileId;
							String maxRevisionSql = "SELECT max(revision) as revision FROM " + FileRevisionTableName + " WHERE status>=0 AND fileId=" + reallyFileId;
							long maxRevision = 0;
							log4j.debug("[mySQL] max maxRevision SQL: " + maxRevisionSql);
							rRs = stmt.executeQuery(maxRevisionSql);
							if (rRs.next()) {
								maxRevision = rRs.getLong("revision");
							}
//							rRs.close();
							if (StringUtils.isEmpty(fileMD5)) {
								fileMD5 = "md51";
							}
							log4j.debug("\n***********************上传/更新文件***********************fileId:"+fileId+" ;revision:"+maxRevision);
							log4j.debug(description);
							log4j.debug("\n***********************");
							String fSql = "";
							try {
								if (maxRevision > 0) {
									// 更新
//									sql = "UPDATE " + FileRevisionTableName + " set status=0, storePath='" + archiveFilePath + "',md5='" + fileMD5 + "',size=" + uFile.length()
//											+ ",lastModified=" + nowDate.getTime() + ",revision=" + revision + ",revisionCount="
//											+ revisionCount + ",rollPath='" + rootPath + "' WHERE revision=" + maxRevision;
									if(oldFileId==0) // existMd5.length()==32 && 
									{
										if(alreadyIds.contains(","+oldFileId+","))
										{
											//更新过则不更新，否则旧文件可能会替换新文件
										}
										else
										{
											//复制
//											sql ="INSERT INTO "+FileRevisionTableName+" (fileid,md5,size,lastModified,userId,revision,revisionCount,status,rollPath,storePath)" +
//											" SELECT distinct fileid,md5,size,lastModified,userId,revision+1 as revision,revisionCount+1 as revisionCount, status,rollPath,storePath from "+FileRevisionTableName+" WHERE fileId = " + reallyFileId+" AND revision=" + maxRevision;
//											log4j.debug("[mySQL]  UPDATE file 复制版本 :\n" + sql);
//											stmt.executeUpdate(sql);
//											
//											maxRevision +=1;
//											fSql = "update " +FileSysTableName+" set revision="+maxRevision+",revisionCount=revisionCount+1 WHERE id =" + reallyFileId;
//											log4j.debug("[mySQL] UPDATE file 更新版本号 :\n" + fSql);
//											stmt.executeUpdate(fSql);
										}
									}
									sql = "UPDATE " + FileRevisionTableName + " set status=0, storePath='" + archiveFilePath + "',md5='" + fileMD5 + "',size=" + uFile.length()
									+ ",lastModified=" + nowDate.getTime() + ",rollPath='" + rootPath + "' WHERE fileId=" + reallyFileId + " AND  revisionCount = " + revisionCount;
									
									if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_USERFILE) || shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_RECIVEFILE)) {
										sql += " AND revision=" + (maxRevision);// 编辑我的文件的时候需要做revision判断
									}
									log4j.debug("[mySQL] UPDATE revision :\n" + sql);
									if (stmt.executeUpdate(sql) > 0) {
//										sql = "DELETE FROM " + FileRevisionTableName + " WHERE size=0 AND length(md5)<10 AND fileId=" + fileId + " AND id<" + maxRevisionId;
//										log4j.debug("[mySQL] DELETE null revision :" + sql);
//										stmt.executeUpdate(sql);
										needDelMap.put(fileId, maxRevision);//操作完再统一删除
									}
									if(oldFileId>0 && reallyFileId!=oldFileId)
									{
										sql = "UPDATE " + FileRevisionTableName + " set status=0, storePath='" + archiveFilePath + "',md5='" + fileMD5 + "',size=" + uFile.length()
										+ ",rollPath='" + rootPath + "' WHERE fileId=" + oldFileId;// +" AND revision=" + maxRevision;
										log4j.debug("[mySQL] UPDATE old File revision :\n" + sql);
										stmt.executeUpdate(sql);
									}
//									fSql = "update " + FileSysTableName + " set md5 ='" + fileMD5 + "',size = " + uFile.length() + ",revision = " + revision + ",revisionCount =" + revisionCount
//											+ ",description ='" + description + "',lastModified=" + nowDate.getTime() + ",status =0 WHERE id =" + fileId;
									fSql = "update " +FileSysTableName+" set name ='"+fileName+"',pid="+pid+",path='"+path+"',md5 ='" + fileMD5 + "',size = " + uFile.length() + ",description ='" + checkNameForSpecialChars(description) + "',lastModified=" + nowDate.getTime() + ",status =0 WHERE id =" + reallyFileId;
									if(oldFileId>0 && fileId!=oldFileId)
									{
										fSql = "update " +FileSysTableName+" set md5 ='" + fileMD5 + "',size = " + uFile.length() + ",description ='" + checkNameForSpecialChars(description) + "',lastModified=" + nowDate.getTime() + " WHERE id =" + reallyFileId;
									}

									log4j.debug("[mySQL] UPDATE file :\n" + fSql);
									
									int update = stmt.executeUpdate(fSql);
									
									
									if (update > 0) {
										/**
										 * 如果是共享文件的修改, 则需要同时考虑来源文件的修改.
										 */
//										if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_RECIVEFILE)) {
//											/**
//											 * 来源文件应该固定为 FileSysTable_MYFILE 吧
//											 */
//											if (permissions!=null && permissions.contains("w")) {
//												fSql = "update " +FileSysTable_MYFILE+" set name ='"+fileName+"',pid="+pid+",path='"+path+"',md5 ='" + fileMD5 + "',size = " + uFile.length() + ",description ='" + checkNameForSpecialChars(description) + "',lastModified=" + nowDate.getTime() + ",status =0 WHERE id =" + srcFileId;
//												stmt.executeUpdate(fSql);
//											}
//											
//											sql = "UPDATE " + FileRevisionTable_MYFILE + " set  status=0, storePath='" + archiveFilePath + "',name='"+fileName+"',md5='" + fileMD5 + "',size=" + uFile.length()
//													+ ",lastModified=" + nowDate.getTime() + ",rollPath='" + rootPath + "' WHERE fileId=" + srcFileId;// +" AND revision=" + maxRevision;
//													log4j.debug("[mySQL] UPDATE revision :\n" + sql);
//											if (stmt.executeUpdate(sql) > 0) {
////														sql = "DELETE FROM " + FileRevisionTableName + " WHERE size=0 AND length(md5)<10 AND fileId=" + fileId + " AND id<" + maxRevisionId;
////														log4j.debug("[mySQL] DELETE null revision :" + sql);
////														stmt.executeUpdate(sql);
//												needDelMap.put(fileId, maxRevision);//操作完再统一删除
//											}
////											if(oldFileId>0 && reallyFileId!=oldFileId)
////											{
////												sql = "UPDATE " + FileRevisionTable_MYFILE + " set status=0, storePath='" + archiveFilePath + "',md5='" + fileMD5 + "',size=" + uFile.length()
////												+ ",rollPath='" + rootPath + "' WHERE fileId=" + oldFileId;// +" AND revision=" + maxRevision;
////												log4j.debug("[mySQL] UPDATE old File revision :\n" + sql);
////												stmt.executeUpdate(sql);
////											}
//											
//										}
									}
									
									// 日志
									if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_RECIVEFILE)) {
										String sqlLog = "insert into jweb_file_log (user_id,path_id,file_id,file_revision,note,ip,create_date,agent_client,operation,content_length,read_length) values ("
												+ userId + "," + pid + "," + fileId + "," + maxRevision + ",'CIFS更新文件','','" + sf.format(nowDate) + "','CIFS','w'," + 0 + "," + 0 + ")";
										log4j.debug("[mySQL] sqlLog SQL:" + sqlLog);
										stmt.executeUpdate(sqlLog);
									} else if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_COMMFILE)) {
										String sqlLog = "insert into jweb_commonfile_log (user_id,path_id,file_id,file_revision,note,ip,create_date,agent_client,operation,content_length,read_length) values ("
												+ userId + "," + pid + "," + fileId + "," + maxRevision + ",'CIFS更新文件','','" + sf.format(nowDate) + "','CIFS','w'," + 0 + "," + 0 + ")";
										log4j.debug("[mySQL] sqlLog SQL:" + sqlLog);
										stmt.executeUpdate(sqlLog);
									}
									
									alreadyIds +=","+reallyFileId+",";//已经更新的fileId
									if(reallyFileId != move_id){
										 sql ="DELETE FROM " +FileSysTableName+" WHERE id = "+move_id;
										stmt.executeUpdate(sql);
									}
								} else {
									sql = "UPDATE " + FileRevisionTableName + " set status=-1, storePath='" + archiveFilePath + "',md5='" + fileMD5 + "',size=" + uFile.length()
									+ ",lastModified=" + nowDate.getTime() + ",rollPath='" + rootPath + "' WHERE fileId=" + reallyFileId;
									stmt.executeUpdate(sql);
									log4j.error("DBI#saveJarData,fileList["+i+"] ERROR 被更新的版本记录已经不存在，不执行更新; fileId:" + fileId);
								}
							} catch (SQLException e) {
								if (conn != null) conn.rollback();
								log4j.error("DBI#saveJarData,fileList["+i+"] SQLException error ; \nsql:"+ sql+ "\nfSql:"+ fSql+ "  ; "+ e.getMessage());
							}
						} else if(uploadFileExist==false) {
							log4j.error("DBI#saveJarData,fileList["+i+"] ERROR to store  update file not exists fileId:" + fileId + ", fileExist:" + fileExist + ", uploadFileExist:" + uploadFileExist);
						} else
						{
							log4j.warn("DBI#saveJarData,fileList["+i+"] ERROR to store  file exists fileId:" + fileId + ", fileExist:" + fileExist + ", uploadFileExist:" + uploadFileExist);
						}
					}
				}
				else
				{
					//删除不必要的文件
					if(!alreadyIds.contains(","+reallyFileId+","))
					{
						sql = "UPDATE " +FileSysTableName+" SET status = -2,lastModified=" + nowDate.getTime() + " WHERE status>-1 AND id = " + reallyFileId;
						log4j.debug("[mySQL] DELETE uploadFile not exists :" + sql);
						stmt.executeUpdate(sql);
					}
					log4j.error("DBI#saveJarData,fileList["+i+"] uploadFile not exists : fileId:" +  dbDetails.getFileId() + ", file path:" + (null != uFile?uFile.getAbsolutePath():"null"));
				}
			}
			
			//删除不需要的
			if(null != needDelMap && needDelMap.size()>0)
			{
				String[] delArr = null;
				Iterator iterator = needDelMap.keySet().iterator();                
	            while (iterator.hasNext()) {    
	            	Object key = iterator.next();    
	            	sql = "UPDATE " + FileRevisionTableName + " SET status=-2 WHERE size=0 AND length(md5)<10 AND fileId=" + key + " AND revision<" +needDelMap.get(key);
					log4j.debug("[mySQL] DELETE null revision :" + sql);
					stmt.executeUpdate(sql);
	            }
			}

			conn.commit();
		} catch (SQLException ex) {
			try {
				if (conn != null) conn.rollback();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			// DEBUG
			log4j.error("DBI#saveJarData  SQLException " , ex);
			// Rethrow the exception
			throw new DBException(ex.getMessage());
		} finally {
			// Close the statement
			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException ex) {
				}
			}
			// Release the database connection
			if (conn != null) {
				try {
					conn.setAutoCommit(true);
				} catch (SQLException e) {
					e.printStackTrace();
				}
				releaseConnection(conn);
			}
		}

		// Return the allocated Jar id

		return jarId;
	}

	
	/**
	 * Return the file loader data table name
	 * 
	 * @return String
	 */
	protected final String getDataTableName() {
		return DataTable_COMMFILE;
	}
	
	/**
	 * Return the retention table name
	 * 
	 * @return String
	 */
	protected final String getRetentionTableName() {
	  return RetentionTable_COMMFILE;
	}
	/**
	 * Return the streams table name
	 * 
	 * @return String
	 */
	protected final String getStreamsTableName() {
		return StreamsTable_COMMFILE;
	}
	
	/**
	 * Return the file loader queue table name
	 * 
	 * @return String
	 */
	protected final String getQueueTableName() {
		return QueueTable_COMMFILE;
	}
	
	/**
	 * Return the file loader transaction table name
	 * 
	 * @return String
	 */
	protected final String getTransactionTableName() {
		return TransactQueueTable_COMMFILE;
	}
	
	/**
	 * Return the FileSysTableName by shareName
	 * 
	 * @return String
	 */
	protected final String getFileSysTableName(String shareName) {

		String FileSysTableName = FileSysTable_COMMFILE;
		if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_USERFILE)) {
			FileSysTableName = FileSysTable_MYFILE;
		} else if (shareName.equals(DBUtil.SHARENAME_WAS_COMMFILE)) {
			FileSysTableName = FileSysTable_COMMFILE;
		} else if(shareName.equals(DBUtil.SHARENAME_WAS_RECIVEFILE))
		{
//			FileSysTableName = FileSysTable_SHAREFILE;
			FileSysTableName = FileSysTable_MYFILE;
		}
		return FileSysTableName;
	}
	
	/**
	 * Return the RevisionTableName by shareName
	 * 
	 * @return String
	 */
	protected final String getRevisionTableName(String shareName) {
		String FileRevisionTableName = FileRevisionTable_COMMFILE;
		if (shareName.equalsIgnoreCase(DBUtil.SHARENAME_WAS_USERFILE)) {
			FileRevisionTableName = FileRevisionTable_MYFILE;
		} else if (shareName.equals(DBUtil.SHARENAME_WAS_COMMFILE)) {
			FileRevisionTableName = FileRevisionTable_COMMFILE;
		} else if(shareName.equals(DBUtil.SHARENAME_WAS_RECIVEFILE))
		{
//			FileRevisionTableName = FileRevisionTable_SHAREFILE;
			FileRevisionTableName = FileRevisionTable_MYFILE;
		}
		return FileRevisionTableName;
	}

}