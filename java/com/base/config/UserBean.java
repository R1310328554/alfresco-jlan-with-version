package com.base.config;

public class UserBean {
	private int id;
	private String name;//用户真实姓名（全名）
	private String username; //用户名
	private String password; //用户密码
	private String securityHash;	//安全哈希
	private Integer departmentId;//部门id
	private int roleId;//系统角色id
	private int userSpaceSize;//用户空间大小
	
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	public String getSecurityHash() {
		return securityHash;
	}
	public void setSecurityHash(String securityHash) {
		this.securityHash = securityHash;
	}
	public Integer getDepartmentId() {
		return departmentId;
	}
	public void setDepartmentId(Integer departmentId) {
		this.departmentId = departmentId;
	}
	public int getRoleId() {
		return roleId;
	}
	public void setRoleId(int roleId) {
		this.roleId = roleId;
	}
	public int getUserSpaceSize() {
		return userSpaceSize;
	}
	public void setUserSpaceSize(int userSpaceSize) {
		this.userSpaceSize = userSpaceSize;
	}
}
