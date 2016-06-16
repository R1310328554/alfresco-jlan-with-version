package com.fileSystem.imp;


import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

import org.apache.log4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.base.config.ShareConfig;
import com.base.config.UserShareConfig;
import com.dao.BaseDao;
import com.fileSystem.FileSystemDAO;
import com.googlecode.ehcache.annotations.Cacheable;
import com.util.DBUtil;
import com.util.SpringUtil;

public class FilesMappingDaoImp implements FileSystemDAO {

	private Logger log4j = Logger.getLogger(this.getClass());
	private JdbcTemplate springjdbcTemp;
	private CacheManager cacheManager;

	private static String sharesQuery_all_sql = " SELECT   s.id , s.name ,s.status ,o.path,s.pool_id, s.type, DATE_FORMAT(ADDTIME,'%Y%m%d %H:%i:%s') AS createtime" +
			"  from  jweb_pool_sharing  s  join jweb_backupequipment o on o.id="
		+ " (select  equipment_id from  jweb_backupmediapool  where id=s.pool_id )"
		+ "   where  s.STATUS=1";
	
	private static String findShares_byPoolId_sql = " SELECT   s.id , s.name ,s.status ,o.path,s.pool_id, s.type, DATE_FORMAT(ADDTIME,'%Y%m%d %H:%i:%s') AS createtime" +
			"  from  jweb_pool_sharing  s  join jweb_backupequipment o on o.id="
		+ " (select  equipment_id from  jweb_backupmediapool  where id=s.pool_id )"
		+ "   where  s.STATUS=1";

	private static String findShares_byName_sql = "SELECT   id,name,status,path,pool_id,type,DATE_FORMAT(ADDTIME,'%Y%m%d %H:%i:%s') AS createtime"
			+ " FROM jweb_pool_sharing  where  name=? and STATUS=1 ";

	private static String findShare_byShareId_sql = "select o.id as id,s.path as path1, o.path as path2 from jweb_backupequipment s  join  jweb_backupmediaroll o  on s.id=o.equipment_id  where  o.pool_id=?";
	
//	private static String userSharesQuery_all_sql = "SELECT id as id,username as name,DATE_FORMAT(lastvisit,'%Y%m%d %H:%i:%s') AS createtime,status FROM jweb_users WHERE status>=0 ORDER BY id";
	private static String userSharesQuery_byName_sql = "SELECT id as id,username as name,DATE_FORMAT(lastvisit,'%Y%m%d %H:%i:%s') AS createtime,status FROM jweb_users WHERE status>=0 AND username=? ";

	@SuppressWarnings("unchecked")
	public List<ShareConfig> findAllShares() throws SQLException {
		log4j.debug("findAllShares-- ");
		return springjdbcTemp.query(sharesQuery_all_sql, new RowMapper() {
			public Object mapRow(ResultSet rs, int arg1) throws SQLException {
				ShareConfig shareDTO = new ShareConfig();
				int shareId = rs.getInt("pool_id");
				shareDTO.setId(shareId);
				shareDTO.setName(rs.getString("name"));
				shareDTO.setPath(rs.getString("path"));
				shareDTO.setStatus(rs.getInt("status"));
				shareDTO.setPool_id(rs.getInt("pool_id"));
				shareDTO.setCreatTime(rs.getString("createtime"));
				return shareDTO;
			}

		});
	}
	
	@SuppressWarnings("unchecked")
	public List<UserShareConfig> findUserShares() throws SQLException {
		log4j.debug("findAllShares-- ");
		return springjdbcTemp.query(DBUtil.userSharesQuery_all_sql, new RowMapper() {
			public Object mapRow(ResultSet rs, int arg1) throws SQLException {
				UserShareConfig shareDTO = new UserShareConfig();
				
				String shareName = rs.getString("name")+DBUtil.SPECIAL_CHAR;
				int shareId = rs.getInt("id");
				shareDTO.setId(shareId);
				shareDTO.setName(shareName);
				shareDTO.setStatus(rs.getInt("status"));
				shareDTO.setCreatTime(rs.getString("createtime"));
				
				//批量添加到缓存，减少findUserShareByName查询
				Cache cache = cacheManager.getCache("UserShareConfigCache");
				Element element = new Element(shareName,shareDTO);
				cache.put(element);
				
				return shareDTO;
			}

		});
	}
	
	/**
	 *  根据共享名查询指定共享信息 
	 */
	@Cacheable(cacheName = "ShareConfigCache")
	public UserShareConfig findUserShareByName(String shareName) throws SQLException {
		Cache cache = cacheManager.getCache("UserShareConfigCache");
		Element element  = cache.get(shareName);
		if(null != element)
		{
			log4j.debug(shareName+ "使用缓存： " + cache.getName());
			return (UserShareConfig) element.getObjectValue();
		}
		
		log4j.debug("findUserShareByName-- shareName="+shareName);
		String username = shareName.replace(DBUtil.SPECIAL_CHAR,"");
		UserShareConfig shareDTO = null;
		Object[] args = new Object[] { username };
		List<Map<String, Object>> shareList = springjdbcTemp.queryForList(userSharesQuery_byName_sql, args);
		if(null != shareList && shareList.size()>0)
		{
			Map<String, Object> map = shareList.get(0);
			shareDTO = new UserShareConfig();
			long shareId = Long.parseLong(map.get("id").toString());
			shareDTO.setId(shareId);
			shareDTO.setName(map.get("name")+DBUtil.SPECIAL_CHAR);
			int status = null !=map.get("status")?Integer.parseInt(map.get("status").toString()):0;
			String createtime = null !=map.get("createtime")?map.get("createtime").toString():"";	
			shareDTO.setStatus(status);
			shareDTO.setCreatTime(createtime);
		}
		
		element = new Element(shareName,shareDTO);
		cache.put(element);
//		cacheManager.addCache(cache);
		
		return shareDTO;
//		return springjdbcTemp.queryForObject(findShares_byName_sql, args,ShareConfig.class);//减少MAP对象的使用
	}
	
//	public ShareConfig findFileByPoolId(Long poolId) throws SQLException {
//		log4j.debug("findFileByPoolId-- poolId="+poolId);
//		Object[] args = new Object[] {poolId};
//		return springjdbcTemp.queryForObject(findShares_byPoolId_sql, args,ShareConfig.class);
//	}

	/**
	 *  根据共享名查询指定共享信息 
	 */
	@Cacheable(cacheName = "ShareConfigCache")
	public ShareConfig findFileByName(String shareName) throws SQLException {
		Cache cache = cacheManager.getCache("ShareConfigCache");
		Element element  = cache.get(shareName);
		if(null != element)
		{
			log4j.debug(shareName+ "使用缓存： " + cache.getName());
			return (ShareConfig) element.getObjectValue();
		}
		
		log4j.debug("findFileByName-- shareName="+shareName);
		ShareConfig shareDTO = null;
		Object[] args = new Object[] { shareName };
		List<Map<String, Object>> shareList = springjdbcTemp.queryForList(findShares_byName_sql, args);
		if(null != shareList && shareList.size()>0)
		{
			Map<String, Object> map = shareList.get(0);
			shareDTO = new ShareConfig();
			long id = Long.parseLong(map.get("id").toString());
			String name = null !=map.get("name")?map.get("name").toString():"";
			String path = null !=map.get("path")?map.get("path").toString():"";
			int status = null !=map.get("status")?Integer.parseInt(map.get("status").toString()):0;
			int pool_id = null !=map.get("pool_id")?Integer.parseInt(map.get("pool_id").toString()):0;
			String createtime = null !=map.get("createtime")?map.get("createtime").toString():"";	
			long totalSpace = 0l;
			long hasSpace = 0;
			try {
				List list = this.findDiskSpace(pool_id,path);
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
			shareDTO.setId(id);
			shareDTO.setName(name);
			shareDTO.setPath(path);
			shareDTO.setStatus(status);
			shareDTO.setPool_id(pool_id);
			shareDTO.setCreatTime(createtime);
			shareDTO.setTotalSpace(totalSpace);
			shareDTO.setFreeSpace(hasSpace);
		}
		
		element = new Element(shareName,shareDTO);
		cache.put(element);
//		cacheManager.addCache(cache);
		
		return shareDTO;
//		return springjdbcTemp.queryForObject(findShares_byName_sql, args,ShareConfig.class);//减少MAP对象的使用
	}
	 
	/**
	 * 按共享名，找存储池对应的卷信息
	 */
	@Cacheable(cacheName = "SharePathListCache")
	public String[] findPathListByShareName(String shareName)throws SQLException
	{  
		Cache cache = cacheManager.getCache("SharePathListCache");
		Element element  = cache.get(shareName);
		if(null != element)
		{
			log4j.debug(shareName+ "使用缓存2： " + cache.getName());
			return  (String[]) element.getObjectValue();
		}
		
		log4j.debug("findPathListByShareName-- shareName="+shareName);
		String[] pathList = null;//子目录
		ShareConfig share= this.findFileByName(shareName);
		if(null != share)
		{
			int poolId = share.getPool_id();
			List<Map<String, Object>> roolList = springjdbcTemp.queryForList(findShare_byShareId_sql, poolId);
			if(null != roolList && roolList.size()>0)
			{
				pathList = new String[roolList.size()];
				log4j.debug("roolList.size() :" + roolList.size());
				Map<String, Object> map = null;
				for(int i=0;i<roolList.size();i++)
				{
					map = roolList.get(i);
					String path2 = map.get("path2").toString();//用卷的目录名，作为卷名
					pathList[i]=path2;
					log4j.debug("存储池加载路径名称  : "+path2);
				}
			}
		}
		
		element = new Element(shareName,pathList);
		cache.put(element);
//		cacheManager.addCache(cache);
		
		return pathList;
	}

	public JdbcTemplate getSpringjdbcTemp() {
		return springjdbcTemp;
	}

	public void setSpringjdbcTemp(JdbcTemplate springjdbcTemp) {
		this.springjdbcTemp = springjdbcTemp;
	}

	public CacheManager getCacheManager() {
		return cacheManager;
	}

	public void setCacheManager(CacheManager cacheManager) {
		this.cacheManager = cacheManager;
	}
//查找对应的总大小 和剩余大小
	public List findDiskSpace(int poolId,String poolPath) throws SQLException {
		log4j.debug("findDiskSpace-- poolId="+poolId+" ,poolPath="+poolPath);
		// TODO Auto-generated method stub
		String sql = " select totalSpace from jweb_backupmediapool where id = "+poolId;
		String sql2 = "select sum(hasSpace) from jweb_backupmediaroll where pool_id ="+poolId;
		long totalSpace = this.springjdbcTemp.queryForLong(sql);
		long hasSpace = this.springjdbcTemp.queryForLong(sql2);//已用空间
		long freeSpace = totalSpace-hasSpace;
		//此处是比较 当服务器大于数据库 已数据库数据为主 服务器小于数据库 已服务器为主
		long rollTotalSize  =this.getUsableSpace(poolPath);
		if(rollTotalSize <= freeSpace)
		{
			freeSpace = rollTotalSize;
		}
		List list = new ArrayList();
		list.add(totalSpace);
		list.add(freeSpace);
		return list;
	}
	
	public static long getUsableSpace(String path1)
	{
		long usableSpace = 0;
		File path = new File(path1);
		usableSpace = path.getUsableSpace();
		return usableSpace;
	}
}


