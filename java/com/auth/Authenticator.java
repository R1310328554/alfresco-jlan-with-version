package com.auth;

import org.alfresco.jlan.server.SrvSession;
import org.alfresco.jlan.server.auth.CifsAuthenticator;
import org.alfresco.jlan.server.auth.ClientInfo;
import org.alfresco.jlan.server.auth.InvalidUserException;
import org.alfresco.jlan.server.auth.NTLanManAuthContext;
import org.alfresco.jlan.server.auth.UserAccount;
import org.alfresco.jlan.server.core.SharedDevice;
import org.apache.log4j.Logger;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.jdbc.JdbcDaoImpl;

public class Authenticator extends CifsAuthenticator {

	private JdbcDaoImpl userdetailsDAO = null;
	private Authentication defaultAuthentication = null;
	public static final String SECURITYKEY = "LINKAPP00USERKEY"; //用户密码加解密，密钥

	private Logger log4j = Logger.getLogger(this.getClass());

	public JdbcDaoImpl getUserdetailsDAO() {
		return userdetailsDAO;
	}

	public Authentication getDefaultAuthentication() {
		return defaultAuthentication;
	}

	public void setDefaultAuthentication(Authentication defaultAuthentication) {
		this.defaultAuthentication = defaultAuthentication;
	}

	public void setUserdetailsDAO(JdbcDaoImpl userdetailsDAO) {
		this.userdetailsDAO = userdetailsDAO;
	}

	/**
	 * Class constructor
	 */
	public Authenticator() {
		setAllowGuest(false);
		// setSecurityMode(SecurityMode.SignaturesEnabled);
		setAccessMode(USER_MODE);
		log4j.info(" use user Authention mode");

	}

	/**
	 * Allow any user to access the server
	 * 
	 * @param client
	 *            Client details.
	 * @param share
	 *            Shared device the user is connecting to.
	 * @param pwd
	 *            Share level password.
	 * @param sess
	 *            Server session
	 * @return int
	 */
	@SuppressWarnings("static-access")
	public int authenticateShareConnect(ClientInfo client, SharedDevice share,
			String pwd, SrvSession sess) {
		String username = client.getUserName();
		log4j.debug("authenticateShareConnect-- ClientInfo="+client+" , SharedDevice"+share);
		if (log4j.isDebugEnabled()) {
			log4j.debug(username + "connection ......");
		}
		// System.out.println("share :" + share.getType());

		return this.Writeable;
	}

	/**
	 * Allow any user to access the server.
	 * 
	 * @param client
	 *            Client details.
	 * @param sess
	 *            Server session
	 * @param alg
	 *            Encryption algorithm
	 * @return int
	 */
	@SuppressWarnings("static-access")
	public int authenticateUser(ClientInfo client, SrvSession sess, int alg) {

		// Check if the user exists in the user list
		int resultAccess = 0;
		String username = client.getUserName();
		if ("".equals(username) || username == null) {
			if (log4j.isDebugEnabled())
				log4j.debug(" fail : the  username  is  required!");
		

			return this.AUTH_BADUSER;
		}
		try {
			UserDetails userdetail = userdetailsDAO.loadUserByUsername(client.getUserName());
			String pwd = userdetail.getPassword();
			if (null != pwd) {
				pwd = AESUtil.decryptHexStrToStr(pwd, SECURITYKEY).trim();
			}
			UserAccount account = new UserAccount(userdetail.getUsername(),pwd);
			NTLanManAuthContext ntlmCtx = (NTLanManAuthContext) sess.getAuthenticationContext();
			 @SuppressWarnings("unused")
			byte [] ecdpwd = generateEncryptedPassword(pwd,ntlmCtx.getChallenge(),alg, account.getUserName(),client.getDomain());
//			log4j.debug("authenticateUser userdetail="+userdetail+" , pwd"+pwd+" ,pwd="+pwd+",ecdpwd :"+ecdpwd.toString() +"  client.pwd:"+ client.getPassword().toString()+" ,client.hasANSIPassword="+client.hasANSIPassword()+" , account.getPassword="+account.getPassword());
			 @SuppressWarnings("unused")
			byte[] pwdByte = client.getPassword();
			if (!validatePassword(account, client, sess.getAuthenticationContext(), alg))
				throw new InvalidUserException(" bad  password  !");

			resultAccess = this.AUTH_ALLOW;
			log4j.debug(" resultAccess = "+resultAccess);
		} catch (Exception ex) {
			if (log4j.isDebugEnabled())
				log4j.debug("authentication  failure  : bad user  or password  for user: "+ username);
		   
			resultAccess = this.AUTH_BADUSER;

		}

		return resultAccess;

	}

	/**
	 * The default authenticator does not use encrypted passwords.
	 * 
	 * @param sess
	 *            SrvSession
	 * @return byte[]
	 */
	public byte[] getChallengeKey(SrvSession sess) {
		return null;
	}


}
