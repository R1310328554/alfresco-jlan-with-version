package com.auth;

import java.util.Collection;

import org.springframework.security.acls.domain.PrincipalSid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public class DefaultAuthentication implements Authentication {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String username;
	private Collection<GrantedAuthority> Authority = null;
	private boolean authenticated =false;

	public DefaultAuthentication(Collection<GrantedAuthority> Authority,
			String username, boolean authenticated ) {
		this.username = username;
		this.Authority = Authority;
		this.authenticated = authenticated;
	}

	public Collection<GrantedAuthority> getAuthorities() {
		
		return this.Authority;
	}

	public Object getCredentials() {

		return new PrincipalSid(this.username);
	}

	public Object getDetails() {
		
		return null;
	}

	public Object getPrincipal() {
		
		return new PrincipalSid(this.username);
	}

	public boolean isAuthenticated() {
		
		return this.authenticated;
	}

	public void setAuthenticated(boolean isAuthenticated)
			throws IllegalArgumentException {
		
		 this.authenticated = isAuthenticated;

	}

	public String getName() {
		
		return this.username;
	}

}
