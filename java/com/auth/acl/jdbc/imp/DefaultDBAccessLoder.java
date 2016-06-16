package com.auth.acl.jdbc.imp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.auth.acl.jdbc.AccessDTO;
import com.auth.acl.jdbc.DBAccessLoader;
import com.auth.acl.jdbc.Exception.LoadAccessException;
import com.util.DBUtil;

public class DefaultDBAccessLoder implements DBAccessLoader {

	private Logger log4j = Logger.getLogger(this.getClass());
	private JdbcTemplate springjdbcTemp;

	private final String DEFAULT_ALL_ACCESS_SQL = "SELECT   us.username ,  ux.*  FROM    jweb_users  us  LEFT  JOIN   ("
			+ " SELECT   u.id, s.name, s.status,   s.type, u.uid,  u.rights  FROM  jweb_pool_sharing s   LEFT JOIN jweb_sharing_ref_user u "
			+ "   ON s.id = u.sid)    ux   ON  us.id =  ux.uid  ";

	private final String DEFAULT_FIND_BYNAMESQL = DEFAULT_ALL_ACCESS_SQL
			+ "  WHERE  us.username =  ?  and ux.name=? ";

	private final String COUNT_SQL = "SELECT   COUNT(*)  FROM   jweb_pool_sharing s  ,("
			+ " SELECT    su.sid  FROM jweb_users  u  INNER JOIN   jweb_sharing_ref_user  su  ON  u.id = su.uid  WHERE  u.username=? "
			+ ")  us   WHERE s.id  = us.sid  AND  NAME=?";

	public JdbcTemplate getSpringjdbcTemp() {
		return springjdbcTemp;
	}

	public void setSpringjdbcTemp(JdbcTemplate springjdbcTemp) {
		this.springjdbcTemp = springjdbcTemp;
	}

	@SuppressWarnings("unchecked")
	public String LoadAccessByName(String username, String shareName) throws LoadAccessException {
		String access = "";
		log4j.debug("LoadAccessByName-- username=" + username + "; shareName="+ shareName);
		if("IPC$".equalsIgnoreCase(shareName) || StringUtils.isEmpty(shareName))
		{
			//空连接
			access = "rw";
		}
		else if(shareName.endsWith(DBUtil.SPECIAL_CHAR))
		{
			//用户网盘CIFS
			if((username+DBUtil.SPECIAL_CHAR).equalsIgnoreCase(shareName))
			{
				//用户自己的目录，直接返回有权限
				access = "rw";
			}
			else
			{
				//无权限
			}
		}
		else
		{
			//归档库
			Object[] args = new Object[] { username, shareName };
			List<AccessDTO> accessList= springjdbcTemp.query(DEFAULT_FIND_BYNAMESQL, new accessMapper(),args);
			if(null != accessList)
			{
				for (AccessDTO DTO : accessList) {
					String accessStr = DTO.getAccess();
					if (!"".equals(accessStr)) {
						access += accessStr;//权限累加
					}
				}
			}
		}
		return access;
	}

	@SuppressWarnings("unchecked")
	public List<AccessDTO> loadAllAccessForDevice() throws LoadAccessException {
		log4j.debug("loadAllAccessForDevice");
		return (List<AccessDTO>) springjdbcTemp.queryForObject(
				DEFAULT_FIND_BYNAMESQL, new accessMapper());
	}

	@SuppressWarnings("unchecked")
	class accessMapper implements RowMapper {
		public Object mapRow(ResultSet rs, int arg1) throws SQLException {
			AccessDTO access = new AccessDTO();
			access.setUsername(rs.getString("username"));
			access.setShareName(rs.getString("name"));
			access.setAccess(rs.getString("rights"));
			return access;
		}
	}

	public boolean hasAccess(String username, String shareName)	throws LoadAccessException {
		log4j.debug("hasAccess-- username=" + username + " ; share=" + shareName);
		if("IPC$".equalsIgnoreCase(shareName) || StringUtils.isEmpty(shareName))
		{
			//空连接
			return true;
		}
		else if(shareName.endsWith(DBUtil.SPECIAL_CHAR))
		{
			//用户网盘CIFS
			if((username+DBUtil.SPECIAL_CHAR).equalsIgnoreCase(shareName))
			{
				//用户自己的目录，直接返回有权限
				return true;
			}
			else
			{
				//无权限
				return false;
			}
		}
		//归档
		Object[] args = new Object[] { username, shareName };
		Long count = springjdbcTemp.queryForLong(COUNT_SQL, args);
		return count > 0 ? true : false;
	}

}
