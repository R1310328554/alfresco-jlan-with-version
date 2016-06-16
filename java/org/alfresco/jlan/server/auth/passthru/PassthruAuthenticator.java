/*
 * Copyright (C) 2006-2013 Alfresco Software Limited.
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

package org.alfresco.jlan.server.auth.passthru;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.StringTokenizer;

import org.springframework.extensions.config.ConfigElement;
import org.alfresco.jlan.debug.Debug;
import org.alfresco.jlan.server.SessionListener;
import org.alfresco.jlan.server.SrvSession;
import org.alfresco.jlan.server.auth.AuthContext;
import org.alfresco.jlan.server.auth.AuthenticatorException;
import org.alfresco.jlan.server.auth.CifsAuthenticator;
import org.alfresco.jlan.server.auth.ClientInfo;
import org.alfresco.jlan.server.auth.ICifsAuthenticator;
import org.alfresco.jlan.server.auth.NTLanManAuthContext;
import org.alfresco.jlan.server.auth.UserAccount;
import org.alfresco.jlan.server.auth.ntlm.NTLM;
import org.alfresco.jlan.server.auth.ntlm.NTLMMessage;
import org.alfresco.jlan.server.auth.ntlm.TargetInfo;
import org.alfresco.jlan.server.auth.ntlm.Type1NTLMMessage;
import org.alfresco.jlan.server.auth.ntlm.Type2NTLMMessage;
import org.alfresco.jlan.server.auth.ntlm.Type3NTLMMessage;
import org.alfresco.jlan.server.auth.spnego.NegTokenInit;
import org.alfresco.jlan.server.auth.spnego.NegTokenTarg;
import org.alfresco.jlan.server.auth.spnego.OID;
import org.alfresco.jlan.server.auth.spnego.SPNEGO;
import org.alfresco.jlan.server.config.InvalidConfigurationException;
import org.alfresco.jlan.server.config.ServerConfiguration;
import org.alfresco.jlan.server.core.NoPooledMemoryException;
import org.alfresco.jlan.server.core.ShareType;
import org.alfresco.jlan.server.core.SharedDevice;
import org.alfresco.jlan.smb.Capability;
import org.alfresco.jlan.smb.Protocol;
import org.alfresco.jlan.smb.SMBStatus;
import org.alfresco.jlan.smb.dcerpc.UUID;
import org.alfresco.jlan.smb.server.SMBServer;
import org.alfresco.jlan.smb.server.SMBSrvException;
import org.alfresco.jlan.smb.server.SMBSrvPacket;
import org.alfresco.jlan.smb.server.SMBSrvSession;
import org.alfresco.jlan.smb.server.VirtualCircuit;
import org.alfresco.jlan.util.DataPacker;
import org.alfresco.jlan.util.HexDump;

/**
 * Passthru Authenticator Class
 * <p>
 * Authenticate users accessing the CIFS server by validating the user against a domain controller
 * or other server on the network.
 * 
 * @author GKSpencer
 */
public class PassthruAuthenticator extends CifsAuthenticator implements SessionListener {

	// Constants

	public final static int DefaultSessionTmo 	= 5000; // 5 seconds
	public final static int MinSessionTmo 		= 2000; // 2 seconds
	public final static int MaxSessionTmo 		= 30000; // 30 seconds

	public final static int MinCheckInterval 	= 10; // 10 seconds
	public final static int MaxCheckInterval 	= 15 * 60; // 15 minutes

	// Passthru keep alive interval

	public final static long PassthruKeepAliveInterval = 60000L; // 60 seconds

	// NTLM flags mask, used to mask out features that are not supported

	private static final int NTLM_FLAGS = NTLM.Flag56Bit + NTLM.Flag128Bit + NTLM.FlagLanManKey + NTLM.FlagNegotiateNTLM
			+ NTLM.FlagNegotiateUnicode;

	// Passthru servers used to authenticate users

	private PassthruServers m_passthruServers;

	// SMB server

	private SMBServer m_server;

	// Sessions that are currently in the negotiate/session setup state

	private Hashtable m_sessions;

	/**
	 * Default Constructor
	 * 
	 * <p>Default to user mode security with encrypted password support.
	 */
	public PassthruAuthenticator() {

		// Allocate the session table

		m_sessions = new Hashtable();

		// Enable extended security session setup

		setExtendedSecurity(true);
	}

	/**
	 * Authenticate the connection to a particular share, called when the SMB server is in share
	 * security mode
	 * 
	 * @param client ClientInfo
	 * @param share SharedDevice
	 * @param sharePwd String
	 * @param sess SrvSession
	 * @return int
	 */
	public int authenticateShareConnect(ClientInfo client, SharedDevice share, String sharePwd, SrvSession sess) {

		// If the server is in share mode security allow the user access

		if ( this.getAccessMode() == SHARE_MODE)
			return Writeable;

		// Check if the IPC$ share is being accessed

		if ( share.getType() == ShareType.ADMINPIPE)
			return Writeable;

		// Check if the user is allowed to access the specified shared device
		//
		// If a user does not have access to the requested share the connection will still be
		// allowed
		// but any attempts to access files or search directories will result in a 'no access
		// rights'
		// error being returned to the client.

		UserAccount user = null;
		if ( client != null)
			user = getUserDetails(client.getUserName());

		if ( user == null) {

			// Check if the guest account is enabled

			return allowGuest() ? Writeable : NoAccess;
		}
		else if ( user.hasShare(share.getName()) == false)
			return NoAccess;

		// Allow user to access this share

		return Writeable;
	}

	/**
	 * Authenticate a session setup by a user
	 * 
	 * @param client ClientInfo
	 * @param sess SrvSession
	 * @param alg int
	 * @return int
	 */
	public int authenticateUser(ClientInfo client, SrvSession sess, int alg) {

		// The null session will only be allowed to connect to the IPC$ named pipe share.

		if ( client.isNullSession()) {
			// Debug

			if ( hasDebug())
				Debug.println("Null CIFS logon allowed");

			return ICifsAuthenticator.AUTH_ALLOW;
		}

		// Check if this is a guest logon

		int authSts = AUTH_DISALLOW;

		if ( client.isGuest() || client.getUserName().equalsIgnoreCase(getGuestUserName())) {
			// Check if guest logons are allowed

			if ( allowGuest() == false)
				return AUTH_DISALLOW;

			// Get a guest authentication token

			doGuestLogon(client, sess);

			// Indicate logged on as guest

			authSts = AUTH_GUEST;

			// DEBUG

			if ( hasDebug())
				Debug.println("Authenticated user " + client.getUserName() + " sts=" + getStatusAsString(authSts));

			// Return the guest status

			return authSts;
		}

		// Find the active authentication session details for the server session

		PassthruDetails passDetails = (PassthruDetails) m_sessions.get(sess.getUniqueId());

		if ( passDetails != null) {

			try {

				// Authenticate the user by passing the hashed password to the authentication server
				// using the session that has already been setup.

				AuthenticateSession authSess = passDetails.getAuthenticateSession();
				authSess.doSessionSetup(client.getUserName(), client.getANSIPassword(), client.getPassword());

				// Check if the user has been logged on as a guest

				if ( authSess.isGuest()) {

					// Check if the local server allows guest access

					if ( allowGuest() == true) {
						// Get a guest authentication token

						doGuestLogon(client, sess);

						// Allow the user access as a guest

						authSts = ICifsAuthenticator.AUTH_GUEST;

						// Debug

						if ( hasDebug())
							Debug.println("Passthru authenticate user=" + client.getUserName() + ", GUEST");
					}
				}
				else {
					// Allow the user full access to the server

					authSts = ICifsAuthenticator.AUTH_ALLOW;

					// Debug

					if ( hasDebug())
						Debug.println("Passthru authenticate user=" + client.getUserName() + ", FULL");
				}
			}
			catch (Exception ex) {

				// Debug

				Debug.println(ex.getMessage());
			}

			// Keep the authentication session if the user session is an SMB session, else close the
			// session now

			if ( (sess instanceof SMBSrvSession) == false) {

				// Remove the passthru session from the active list

				m_sessions.remove(sess.getUniqueId());

				// Close the passthru authentication session

				try {

					// Close the authentication session

					AuthenticateSession authSess = passDetails.getAuthenticateSession();
					authSess.CloseSession();

					// DEBUG

					if ( hasDebug())
						Debug.println("Closed auth session, sessId=" + authSess.getSessionId());
				}
				catch (Exception ex) {

					// Debug

					Debug.println("Passthru error closing session (auth user) " + ex.getMessage());
				}
			}
		}
		else {

			// DEBUG

			if ( hasDebug())
				Debug.println("  No PassthruDetails for " + sess.getUniqueId());
		}

		// Return the authentication status

		return authSts;
	}

	/**
	 * Return an authentication context for the new session
	 * 
	 * @return AuthContext
	 */
	public AuthContext getAuthContext(SMBSrvSession sess) {

		// Make sure the SMB server listener is installed

		if ( m_server == null && sess instanceof SMBSrvSession) {
			SMBSrvSession smbSess = (SMBSrvSession) sess;
			m_server = smbSess.getSMBServer();

			// Install the server listener

			m_server.addSessionListener(this);
		}

		// Open a connection to the authentication server, use normal session setup

		AuthContext authCtx = null;

		try {

			AuthenticateSession authSess = m_passthruServers.openSession();
			if ( authSess != null) {

				// Create an entry in the active sessions table for the new session

				PassthruDetails passDetails = new PassthruDetails(sess, authSess);
				m_sessions.put(sess.getUniqueId(), passDetails);

				// Use the challenge key returned from the authentication server

				authCtx = new NTLanManAuthContext(authSess.getEncryptionKey());
				sess.setAuthenticationContext(authCtx);

				// DEBUG

				if ( hasDebug())
					Debug.println("Passthru sessId=" + authSess.getSessionId() + ", auth ctx=" + authCtx);
			}
		}
		catch (Exception ex) {

			// Debug

			Debug.println("Passthru error getting challenge " + ex.getMessage());
		}

		// Return the authentication context

		return authCtx;
	}

	/**
	 * Generate the CIFS negotiate response packet, the authenticator should add authentication
	 * specific fields to the response.
	 * 
	 * @param sess SMBSrvSession
	 * @param respPkt SMBSrvPacket
	 * @param extendedSecurity boolean
	 * @exception AuthenticatorException
	 */
	public void generateNegotiateResponse(SMBSrvSession sess, SMBSrvPacket respPkt, boolean extendedSecurity)
		throws AuthenticatorException {

		// If the client does not support extended security then return a standard negotiate
		// response
		// with an 8 byte challenge

		if ( extendedSecurity == false) {
			super.generateNegotiateResponse(sess, respPkt, extendedSecurity);
			return;
		}

		// Make sure the extended security negotiation flag is set

		if ( (respPkt.getFlags2() & SMBSrvPacket.FLG2_EXTENDEDSECURITY) == 0)
			respPkt.setFlags2(respPkt.getFlags2() + SMBSrvPacket.FLG2_EXTENDEDSECURITY);

		// Get the negotiate response byte area position

		int pos = respPkt.getByteOffset();
		byte[] buf = respPkt.getBuffer();

		// Pack the CIFS server GUID into the negotiate response

		UUID serverGUID = sess.getSMBServer().getServerGUID();
		serverGUID.storeUUID(buf, pos, false);

		pos += 16;

		// Set the negotiate response length

		respPkt.setByteCount(pos - respPkt.getByteOffset());
	}

	/**
	 * Process the CIFS session setup request packet and build the session setup response
	 * 
	 * @param sess SMBSrvSession
	 * @param reqPkt SMBSrvPacket
	 * @exception SMBSrvException
	 */
	public void processSessionSetup(SMBSrvSession sess, SMBSrvPacket reqPkt)
		throws SMBSrvException {

		// Check that the received packet looks like a valid NT session setup andX request

		if ( reqPkt.checkPacketIsValid(12, 0) == false)
			throw new SMBSrvException(SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);

		// Check if the request is using security blobs or the older hashed password format

		if ( reqPkt.getParameterCount() == 13) {

			// Process the standard password session setup

			super.processSessionSetup(sess, reqPkt);
			return;
		}

		// Extract the session details

		int maxBufSize = reqPkt.getParameter(2);
		int maxMpx = reqPkt.getParameter(3);
		int vcNum = reqPkt.getParameter(4);
		int secBlobLen = reqPkt.getParameter(7);
		int capabs = reqPkt.getParameterLong(10);

		// Extract the client details from the session setup request

		int dataPos = reqPkt.getByteOffset();
		byte[] buf = reqPkt.getBuffer();

		// Determine if ASCII or unicode strings are being used

		boolean isUni = reqPkt.isUnicode();

		// Make a note of the security blob position

		int secBlobPos = dataPos;

		// Extract the clients primary domain name string

		dataPos += secBlobLen;
		reqPkt.setPosition(dataPos);

		String domain = "";

		if ( reqPkt.hasMoreData()) {

			// Extract the callers domain name

			domain = reqPkt.unpackString(isUni);

			if ( domain == null)
				throw new SMBSrvException(SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
		}

		// Extract the clients native operating system

		String clientOS = "";

		if ( reqPkt.hasMoreData()) {

			// Extract the callers operating system name

			clientOS = reqPkt.unpackString(isUni);

			if ( clientOS == null)
				throw new SMBSrvException(SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
		}

		// Store the client maximum buffer size, maximum multiplexed requests count and client
		// capability flags

		sess.setClientMaximumBufferSize(maxBufSize != 0 ? maxBufSize : SMBSrvSession.DefaultBufferSize);
		sess.setClientMaximumMultiplex(maxMpx);
		sess.setClientCapabilities(capabs);

		// Create the client information and store in the session

		ClientInfo client = ClientInfo.createInfo("", null);
		client.setDomain(domain);
		client.setOperatingSystem(clientOS);

		client.setLogonType(ClientInfo.LogonNormal);

		// Set the remote address, if available

		if ( sess.hasRemoteAddress())
			client.setClientAddress(sess.getRemoteAddress().getHostAddress());

		// Set the process id for this client, for multi-stage logons

		client.setProcessId(reqPkt.getProcessId());

		// Get the current sesion setup object, or null

		Object setupObj = sess.getSetupObject(client.getProcessId());

		// Process the security blob

		byte[] respBlob = null;
		boolean isNTLMSSP = false;

		try {

			// Check if the blob has the NTLMSSP signature

			if ( secBlobLen >= NTLM.Signature.length) {

				// Check for the NTLMSSP signature

				int idx = 0;
				while (idx < NTLM.Signature.length && buf[secBlobPos + idx] == NTLM.Signature[idx])
					idx++;

				if ( idx == NTLM.Signature.length)
					isNTLMSSP = true;
			}

			// Process the security blob

			if ( isNTLMSSP == true) {

				// DEBUG

				if ( hasDebug())
					Debug.println("NT Session setup NTLMSSP, MID=" + reqPkt.getMultiplexId() + ", UID=" + reqPkt.getUserId()
							+ ", PID=" + reqPkt.getProcessId());

				// Process an NTLMSSP security blob

				respBlob = doNtlmsspSessionSetup(sess, client, buf, secBlobPos, secBlobLen, isUni);
			}
			else {
				// Process an SPNEGO security blob

				respBlob = doSpnegoSessionSetup(sess, client, buf, secBlobPos, secBlobLen, isUni);
			}
		}
		catch (SMBSrvException ex) {

			// Cleanup any stored context

			sess.removeSetupObject(client.getProcessId());

			// Rethrow the exception

			throw ex;
		}

		// Debug

		if ( Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_NEGOTIATE)) {
			if ( respBlob == null)
				Debug.println("[SMB] User " + client.getUserName() + " logged on "
						+ (client != null ? " (type " + client.getLogonTypeString() + ")" : ""));
			else
				Debug.println("[SMB] Two stage logon (" + (isNTLMSSP ? "NTLMSSP" : "SPNEGO") + ")");
		}

		// Update the client information if not already set

		if ( sess.getClientInformation() == null || sess.getClientInformation().getUserName().length() == 0) {

			// Set the client details for the session

			sess.setClientInformation(client);
		}

		// Get the response blob length, it can be null

		int respLen = respBlob != null ? respBlob.length : 0;

		// Check if there is/was a session setup object stored in the session, this indicates a
		// multi-stage session setup so set the status code accordingly

		SMBSrvPacket respPkt = reqPkt;
		boolean loggedOn = false;

		if ( isNTLMSSP == true || sess.hasSetupObject(client.getProcessId()) || setupObj != null) {

			// NTLMSSP has two stages, if there is a stored setup object then indicate more
			// processing  required

			if ( sess.hasSetupObject(client.getProcessId()))
				respPkt.setLongErrorCode(SMBStatus.NTMoreProcessingRequired);
			else {
				respPkt.setLongErrorCode(SMBStatus.NTSuccess);

				// Indicate that the user is logged on

				loggedOn = true;
			}

			// Set the parameter count then check if the security blob will fit into the current
			// packet buffer

			respPkt.setParameterCount(4);
			int reqLen = respLen + 100; // allow for strings

			if ( reqLen > respPkt.getAvailableLength()) {

				try {

					// Allocate a new buffer for the response

					respPkt = sess.getPacketPool().allocatePacket(respPkt.getByteOffset() + reqLen, reqPkt);
				}
				catch (NoPooledMemoryException ex) {

					// DEBUG

					if ( Debug.EnableDbg && hasDebug())
						Debug.println("Authenticator failed to allocate packet from pool, reqSiz="
								+ (respPkt.getByteOffset() + respLen));

					// Return a server error to the client

					throw new SMBSrvException(SMBStatus.NTInvalidParameter, SMBStatus.SRVNoBuffers, SMBStatus.ErrSrv);
				}
			}
			
			// Fill in the rest of the packet header
			
			respPkt.setParameter(0, 0xFF); 	// No chained response
			respPkt.setParameter(1, 0); 	// Offset to chained response

			respPkt.setParameter(2, 0); 	// Action
			respPkt.setParameter(3, respLen);
		}
		else {

			// Build a completed session setup response

			respPkt.setLongErrorCode(SMBStatus.NTSuccess);

			// Build the session setup response SMB

			respPkt.setParameterCount(12);
			respPkt.setParameter(0, 0xFF); 	// No chained response
			respPkt.setParameter(1, 0); 	// Offset to chained response

			respPkt.setParameter(2, SMBSrvSession.DefaultBufferSize);
			respPkt.setParameter(3, SMBSrvSession.NTMaxMultiplexed);
			respPkt.setParameter(4, 0); 	// virtual circuit number
			respPkt.setParameterLong(5, 0); // session key
			respPkt.setParameter(7, respLen); // security blob length
			respPkt.setParameterLong(8, 0); // reserved
			respPkt.setParameterLong(10, getServerCapabilities());

			// Indicate that the user is logged on

			loggedOn = true;
		}

		// If the user is logged on then allocate a virtual circuit

		int uid = 0;

		if ( loggedOn == true) {

			// Check for virtual circuit zero, disconnect any other sessions from this client
			
			if ( vcNum == 0 && hasSessionCleanup()) {
			
				// Disconnect other sessions from this client, cleanup any open files/locks/oplocks
				
				int discCnt = sess.disconnectClientSessions();

				// DEBUG

				if ( discCnt > 0 && Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_NEGOTIATE))
					Debug.println("[SMB] Disconnected " + discCnt + " existing sessions from client, sess=" + sess);
			}
			
			// Clear any stored session setup object for the logon

			sess.removeSetupObject(client.getProcessId());

			// Create a virtual circuit for the new logon

			VirtualCircuit vc = new VirtualCircuit(vcNum, client);
			uid = sess.addVirtualCircuit(vc);

			if ( uid == VirtualCircuit.InvalidUID) {

				// DEBUG

				if ( hasDebug() && sess.hasDebug(SMBSrvSession.DBG_NEGOTIATE))
					Debug.println("Failed to allocate UID for virtual circuit, " + vc);

				// Failed to allocate a UID

				throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
			}
			else if ( hasDebug() && sess.hasDebug(SMBSrvSession.DBG_NEGOTIATE)) {

				// DEBUG

				Debug.println("Allocated UID=" + uid + " for VC=" + vc);
			}
		}

		// Common session setup response

		respPkt.setCommand(reqPkt.getCommand());
		respPkt.setByteCount(0);

		respPkt.setTreeId(0);
		respPkt.setUserId(uid);

		// Set the various flags

		int flags = respPkt.getFlags();
		flags &= ~SMBSrvPacket.FLG_CASELESS;
		respPkt.setFlags(flags);

		int flags2 = SMBSrvPacket.FLG2_LONGFILENAMES + SMBSrvPacket.FLG2_EXTENDEDSECURITY + SMBSrvPacket.FLG2_LONGERRORCODE;
		if ( isUni)
			flags2 += SMBSrvPacket.FLG2_UNICODE;
		respPkt.setFlags2(flags2);

		// Pack the security blob

		int pos = respPkt.getByteOffset();
		buf = respPkt.getBuffer();

		if ( respBlob != null) {
			System.arraycopy(respBlob, 0, buf, pos, respBlob.length);
			pos += respBlob.length;
		}

		// Pack the OS, dialect and domain name strings

		if ( isUni)
			pos = DataPacker.wordAlign(pos);

		pos = DataPacker.putString("Java", buf, pos, true, isUni);
		pos = DataPacker.putString("LinkAPP CIFS Server " + sess.getServer().isVersion(), buf, pos, true, isUni);
		pos = DataPacker.putString(sess.getSMBServer().getCIFSConfiguration().getDomainName(), buf, pos, true, isUni);

		respPkt.setByteCount(pos - respPkt.getByteOffset());
	}

	/**
	 * Process an NTLMSSP security blob
	 * 
	 * @param sess SMBSrvSession
	 * @param client ClientInfo
	 * @param secbuf byte[]
	 * @param secpos int
	 * @param seclen int
	 * @param unicode boolean
	 * @exception SMBSrvException
	 */
	private final byte[] doNtlmsspSessionSetup(SMBSrvSession sess, ClientInfo client, byte[] secbuf, int secpos, int seclen,
			boolean unicode)
		throws SMBSrvException {

		// Determine the NTLmSSP message type

		int msgType = NTLMMessage.isNTLMType(secbuf, secpos);
		byte[] respBlob = null;

		if ( msgType == -1) {

			// DEBUG

			if ( hasDebug()) {
				Debug.println("Invalid NTLMSSP token received");
				Debug.println("  Token=" + HexDump.hexString(secbuf, secpos, seclen, " "));
			}

			// Return a logon failure status

			throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
		}

		// Check for a type 1 NTLMSSP message

		else if ( msgType == NTLM.Type1) {

			// Create the type 1 NTLM message from the token

			Type1NTLMMessage type1Msg = new Type1NTLMMessage(secbuf, secpos, seclen);

			// Build the type 2 NTLM response message
			//
			// Get the flags from the client request and mask out unsupported features

			int ntlmFlags = type1Msg.getFlags() & NTLM_FLAGS;

			// Generate a challenge for the response

			NTLanManAuthContext ntlmCtx = (NTLanManAuthContext) getAuthContext(sess);

			if ( ntlmCtx == null)
				throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);

			// Build a type2 message to send back to the client, containing the challenge

			String domain = sess.getSMBServer().getServerName();

			List tList = new ArrayList();

			tList.add(new TargetInfo(NTLM.TargetDomain, domain));
			tList.add(new TargetInfo(NTLM.TargetServer, sess.getServerName()));
			tList.add(new TargetInfo(NTLM.TargetDNSDomain, domain));
			tList.add(new TargetInfo(NTLM.TargetFullDNS, domain));

			ntlmFlags = NTLM.FlagChallengeAccept + NTLM.FlagRequestTarget + NTLM.FlagNegotiateNTLM + NTLM.FlagNegotiateUnicode
					+ NTLM.FlagKeyExchange + NTLM.FlagTargetInfo + NTLM.Flag56Bit;

			// NTLM.FlagAlwaysSign + NTLM.FlagNegotiateSign +

			Type2NTLMMessage type2Msg = new Type2NTLMMessage();

			type2Msg.buildType2(ntlmFlags, domain, ntlmCtx.getChallenge(), null, tList);

			// Store the type 2 message in the session until the session setup is complete

			sess.setSetupObject(client.getProcessId(), type2Msg);

			// Set the response blob using the type 2 message

			respBlob = type2Msg.getBytes();
		}
		else if ( msgType == NTLM.Type3) {

			// Create the type 3 NTLM message from the token

			Type3NTLMMessage type3Msg = new Type3NTLMMessage(secbuf, secpos, seclen, unicode);

			// Make sure a type 2 message was stored in the first stage of the session setup

			if ( sess.hasSetupObject(client.getProcessId()) == false
					|| sess.getSetupObject(client.getProcessId()) instanceof Type2NTLMMessage == false) {

				// Clear the setup object

				sess.removeSetupObject(client.getProcessId());

				// Return a logon failure

				throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
			}

			// Determine if the client sent us NTLMv1 or NTLMv2

			if ( type3Msg.hasFlag(NTLM.Flag128Bit) && type3Msg.hasFlag(NTLM.FlagNTLM2Key)) {

				// Debug

				if ( hasDebug())
					Debug.println("Received NTLMSSP/NTLMv2, not supported");

				// Return a logon failure

				throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
			}
			else {

				// Looks like an NTLMv1 blob

				doNTLMv1Logon(sess, client, type3Msg);

				// Debug

				if ( hasDebug())
					Debug.println("Logged on using NTLMSSP/NTLMv1");
			}
		}

		// Return the response blob

		return respBlob;
	}

	/**
	 * Perform an NTLMv1 logon using the NTLMSSP type3 message
	 * 
	 * @param sess SMBSrvSession
	 * @param client ClientInfo
	 * @param type3Msg Type3NTLMMessage
	 * @exception SMBSrvException
	 */
	private final void doNTLMv1Logon(SMBSrvSession sess, ClientInfo client, Type3NTLMMessage type3Msg)
		throws SMBSrvException {

		// Get the type 2 message that contains the challenge sent to the client

		Type2NTLMMessage type2Msg = (Type2NTLMMessage) sess.getSetupObject(client.getProcessId());
		sess.removeSetupObject(client.getProcessId());

		// Get the NTLM logon details

		String userName = type3Msg.getUserName();

		// Check for a null logon

		if ( userName.length() == 0) {

			// DEBUG

			if ( hasDebug())
				Debug.println("Null logon");

			// Indicate a null logon in the client information

			client.setLogonType(ClientInfo.LogonNull);
			return;
		}

		// Find the active authentication session details for the server session

		PassthruDetails passDetails = (PassthruDetails) m_sessions.get(sess.getUniqueId());

		if ( passDetails != null) {

			try {

				// Authenticate the user by passing the hashed password to the authentication server
				// using the session that has already been setup.

				AuthenticateSession authSess = passDetails.getAuthenticateSession();
				authSess.doSessionSetup(userName, type3Msg.getLMHash(), type3Msg.getNTLMHash());

				// Check if the user has been logged on as a guest

				if ( authSess.isGuest()) {

					// Check if the local server allows guest access

					if ( allowGuest() == true) {

						// Get a guest authentication token

						doGuestLogon(client, sess);

						// Debug

						if ( hasDebug())
							Debug.println("Passthru authenticate user=" + userName + ", GUEST");
					}
				}
				else {

					// Indicate that the client is logged on

					client.setLogonType(ClientInfo.LogonNormal);

					// Debug

					if ( hasDebug())
						Debug.println("Passthru authenticate user=" + userName + ", FULL");
				}

				// Update the client details

				client.setDomain(type3Msg.getDomain());
				client.setUserName(userName);
			}
			catch (Exception ex) {

				// Debug

				Debug.println(ex.getMessage());

				// Indicate logon failure

				throw new SMBSrvException(SMBStatus.NTErr, SMBStatus.NTLogonFailure);
			}
			finally {
				// Remove the passthru session from the active list

				m_sessions.remove(sess.getUniqueId());

				// Close the passthru authentication session

				try {

					// Close the authentication session

					AuthenticateSession authSess = passDetails.getAuthenticateSession();
					authSess.CloseSession();

					// DEBUG

					if ( hasDebug())
						Debug.println("Closed auth session, sessId=" + authSess.getSessionId());
				}
				catch (Exception ex) {

					// Debug

					Debug.println("Passthru error closing session (auth user) " + ex.getMessage());
				}
			}
		}
		else {

			// DEBUG

			if ( hasDebug())
				Debug.println("  No PassthruDetails for " + sess.getUniqueId());

			// Indicate logon failure

			throw new SMBSrvException(SMBStatus.NTErr, SMBStatus.NTLogonFailure);
		}
	}

	/**
	 * Process an SPNEGO security blob
	 * 
	 * @param sess SMBSrvSession
	 * @param client ClientInfo
	 * @param secbuf byte[]
	 * @param secpos int
	 * @param seclen int
	 * @param unicode boolean
	 * @exception SMBSrvException
	 */
	private final byte[] doSpnegoSessionSetup(SMBSrvSession sess, ClientInfo client, byte[] secbuf, int secpos, int seclen,
			boolean unicode)
		throws SMBSrvException {
		// Check the received token type, if it is a target token and there is a stored session
		// setup object, this is the second
		// stage of an NTLMSSP session setup that is wrapped with SPNEGO

		int tokType = -1;

		try {
			tokType = SPNEGO.checkTokenType(secbuf, secpos, seclen);
		}
		catch (IOException ex) {
		}

		// Check for the second stage of an NTLMSSP logon

		NegTokenTarg negTarg = null;

		if ( tokType == SPNEGO.NegTokenTarg && sess.hasSetupObject(client.getProcessId())
				&& sess.getSetupObject(client.getProcessId()) instanceof Type2NTLMMessage) {
			// Get the NTLMSSP blob from the NegTokenTarg blob

			NegTokenTarg negToken = new NegTokenTarg();

			try {
				// Decode the security blob

				negToken.decode(secbuf, secpos, seclen);
			}
			catch (IOException ex) {
				// Log the error

				if ( hasDebug())
					Debug.println("Passthru error on session startup: " + ex.getMessage());

				// Return a logon failure status

				throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
			}

			// Get the second stage NTLMSSP blob

			byte[] ntlmsspBlob = negToken.getResponseToken();

			// Perform an NTLMSSP session setup

			byte[] ntlmsspRespBlob = doNtlmsspSessionSetup(sess, client, ntlmsspBlob, 0, ntlmsspBlob.length, unicode);

			// NTLMSSP is a two stage process, set the SPNEGO status

			int spnegoSts = SPNEGO.AcceptCompleted;

			if ( sess.hasSetupObject(client.getProcessId()))
				spnegoSts = SPNEGO.AcceptIncomplete;

			// Package the NTLMSSP response in an SPNEGO response

			negTarg = new NegTokenTarg(spnegoSts, null, ntlmsspRespBlob);
		}
		else if ( tokType == SPNEGO.NegTokenInit) {
			// Parse the SPNEGO security blob to get the Kerberos ticket

			NegTokenInit negToken = new NegTokenInit();

			try {
				// Decode the security blob

				negToken.decode(secbuf, secpos, seclen);
			}
			catch (IOException ex) {
				// Log the error

				if ( hasDebug())
					Debug.println("Passthru error on session startup: " + ex.getMessage());

				// Return a logon failure status

				throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
			}

			// Determine the authentication mechanism the client is using and logon

			String oidStr = null;
			if ( negToken.numberOfOids() > 0)
				oidStr = negToken.getOidAt(0).toString();

			if ( oidStr != null && oidStr.equals(OID.ID_NTLMSSP)) {
				// NTLMSSP logon, get the NTLMSSP security blob that is inside the SPNEGO blob

				byte[] ntlmsspBlob = negToken.getMechtoken();

				// Perform an NTLMSSP session setup

				byte[] ntlmsspRespBlob = doNtlmsspSessionSetup(sess, client, ntlmsspBlob, 0, ntlmsspBlob.length, unicode);

				// NTLMSSP is a two stage process, set the SPNEGO status

				int spnegoSts = SPNEGO.AcceptCompleted;

				if ( sess.hasSetupObject(client.getProcessId()))
					spnegoSts = SPNEGO.AcceptIncomplete;

				// Package the NTLMSSP response in an SPNEGO response

				negTarg = new NegTokenTarg(spnegoSts, OID.NTLMSSP, ntlmsspRespBlob);
			}
			else {
				// Debug

				if ( hasDebug()) {
					Debug.println("No matching authentication OID found");
					Debug.println("  " + negToken.toString());
				}

				// No valid authentication mechanism

				throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
			}
		}
		else {
			// Unknown SPNEGO token type

			if ( hasDebug()) {
				Debug.println("Unknown SPNEGO token type");
			}

			// Return a logon failure status

			throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
		}

		// Generate the NegTokenTarg blob

		byte[] respBlob = null;

		try {
			// Generate the response blob

			respBlob = negTarg.encode();
		}
		catch (IOException ex) {
			// Debug

			if ( hasDebug()) {
				Debug.println("Failed to encode NegTokenTarg: " + ex.getMessage());
			}

			// Failed to build response blob

			throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
		}

		// Return the SPNEGO response blob

		return respBlob;
	}

	/**
	 * Initialzie the authenticator
	 * 
	 * @param config ServerConfiguration
	 * @param params ConfigElement
	 * @exception InvalidConfigurationException
	 */
	public void initialize(ServerConfiguration config, ConfigElement params)
		throws InvalidConfigurationException {

		// Call the base class

		super.initialize(config, params);

		// Check if session cleanup should be disabled, when a session setup request is received
		// on virtual circuit zero
		
		if ( params.getChild("disableSessionCleanup") != null) {
			
			// Disable session cleanup
			
			setSessionCleanup( false);

			// Debug

			if ( Debug.EnableInfo && hasDebug())
				Debug.println("[SMB] Disabled session cleanup (for virtual circuit zero logons)");
		}
		
		// Check if the passthru session protocol order has been specified

		ConfigElement protoOrder = params.getChild("protocolOrder");
		if ( protoOrder != null) {

			// Parse the protocol order list

			StringTokenizer tokens = new StringTokenizer(protoOrder.getValue(), ",");
			int primaryProto = Protocol.None;
			int secondaryProto = Protocol.None;

			// There should only be one or two tokens

			if ( tokens.countTokens() > 2)
				throw new InvalidConfigurationException("Invalid protocol order list, " + protoOrder.getValue());

			// Get the primary protocol

			if ( tokens.hasMoreTokens()) {
				// Parse the primary protocol

				String primaryStr = tokens.nextToken();

				if ( primaryStr.equalsIgnoreCase("TCPIP"))
					primaryProto = Protocol.NativeSMB;
				else if ( primaryStr.equalsIgnoreCase("NetBIOS"))
					primaryProto = Protocol.TCPNetBIOS;
				else
					throw new InvalidConfigurationException("Invalid protocol type, " + primaryStr);

				// Check if there is a secondary protocol, and validate

				if ( tokens.hasMoreTokens()) {
					// Parse the secondary protocol

					String secondaryStr = tokens.nextToken();

					if ( secondaryStr.equalsIgnoreCase("TCPIP") && primaryProto != Protocol.NativeSMB)
						secondaryProto = Protocol.NativeSMB;
					else if ( secondaryStr.equalsIgnoreCase("NetBIOS") && primaryProto != Protocol.TCPNetBIOS)
						secondaryProto = Protocol.TCPNetBIOS;
					else
						throw new InvalidConfigurationException("Invalid secondary protocol, " + secondaryStr);
				}
			}

			// Set the protocol order used for passthru authentication sessions

			AuthSessionFactory.setProtocolOrder(primaryProto, secondaryProto);

			// DEBUG

			if ( hasDebug())
				Debug.println("Protocol order primary=" + Protocol.asString(primaryProto) + ", secondary="
						+ Protocol.asString(secondaryProto));
		}

		// Check if the offline check interval has been specified

		ConfigElement checkInterval = params.getChild("offlineCheckInterval");
		if ( checkInterval != null) {
			try {
				// Validate the check interval value

				int offlineCheck = Integer.parseInt(checkInterval.getValue());

				// Range check the value

				if ( offlineCheck < MinCheckInterval || offlineCheck > MaxCheckInterval)
					throw new InvalidConfigurationException("Invalid offline check interval, valid range is " + MinCheckInterval
							+ " to " + MaxCheckInterval);

				// Set the offline check interval for offline passthru servers

				m_passthruServers = new PassthruServers(offlineCheck);

				// DEBUG

				if ( hasDebug())
					Debug.println("Using offline check interval of " + offlineCheck + " seconds");
			}
			catch (NumberFormatException ex) {
				throw new InvalidConfigurationException("Invalid offline check interval specified");
			}
		}
		else {
			// Create the passthru server list with the default offline check interval

			m_passthruServers = new PassthruServers();
		}

		// Enable passthru servers debugging, if enabled for the authenticator

		if ( hasDebug())
			m_passthruServers.setDebug(true);

		// Check if the session timeout has been specified

		ConfigElement sessTmoElem = params.getChild("Timeout");
		if ( sessTmoElem != null) {

			try {

				// Validate the session timeout value

				int sessTmo = Integer.parseInt(sessTmoElem.getValue());

				// Range check the timeout

				if ( sessTmo < MinSessionTmo || sessTmo > MaxSessionTmo)
					throw new InvalidConfigurationException("Invalid session timeout, valid range is " + MinSessionTmo + " to "
							+ MaxSessionTmo);

				// Set the session timeout for connecting to an authentication server

				m_passthruServers.setConnectionTimeout(sessTmo);
			}
			catch (NumberFormatException ex) {
				throw new InvalidConfigurationException("Invalid timeout value specified");
			}
		}

		// Check if a server name has been specified

		String srvList = null;
		ConfigElement srvNamesElem = params.getChild("Server");

		if ( srvNamesElem != null && srvNamesElem.getValue().length() > 0) {

			// Check if the server name was already set

			if ( srvList != null)
				throw new InvalidConfigurationException("Set passthru server via local server or specify name");

			// Get the passthru authenticator server name

			srvList = srvNamesElem.getValue();
		}

		// If the passthru server name has been set initialize the passthru connection

		if ( srvList != null) {

			// Initialize using a list of server names/addresses

			m_passthruServers.setServerList(srvList);
		}
		else {

			// Get the domain/workgroup name

			String domainName = null;
			ConfigElement domNameElem = params.getChild("Domain");

			if ( domNameElem != null && domNameElem.getValue().length() > 0) {

				// Check if the authentication server has already been set, ie. server name was also
				// specified

				if ( srvList != null)
					throw new InvalidConfigurationException("Specify server or domain name for passthru authentication");

				domainName = domNameElem.getValue();
			}

			// If the domain name has been set initialize the passthru connection

			if ( domainName != null) {

				// Initialize using the domain

				try {
					m_passthruServers.setDomain(domainName);
				}
				catch (IOException ex) {
					throw new InvalidConfigurationException("Failed to set domain, " + ex.getMessage());
				}
			}
		}

		// Check if we have an authentication server

		if ( m_passthruServers.getTotalServerCount() == 0)
			throw new InvalidConfigurationException("No valid authentication servers found for passthru");

		// Install the SMB server listener so we receive callbacks when sessions are
		// opened/closed on the SMB server

		SMBServer smbServer = (SMBServer) config.findServer("SMB");
		if ( smbServer != null)
			smbServer.addSessionListener(this);
	}

	/**
	 * Return the server capability flags
	 * 
	 * @return int
	 */
	public int getServerCapabilities() {

		return Capability.Unicode + Capability.RemoteAPIs + Capability.NTSMBs + Capability.NTFind + Capability.NTStatus
                + Capability.LargeFiles + Capability.LargeRead + Capability.LargeWrite + Capability.ExtendedSecurity
                + Capability.InfoPassthru + Capability.Level2Oplocks;
	}

	/**
	 * Close the authenticator, perform cleanup
	 */
	public void closeAuthenticator() {

		// Close the passthru authentication server list

		if ( m_passthruServers != null)
			m_passthruServers.shutdown();
	}

	/**
	 * SMB server session closed notification
	 * 
	 * @param sess SrvSession
	 */
	public void sessionClosed(SrvSession sess) {

		// Check if there is an active session to the authentication server for this local
		// session

		PassthruDetails passDetails = (PassthruDetails) m_sessions.get(sess.getUniqueId());

		if ( passDetails != null) {

			// Remove the passthru session from the active list

			m_sessions.remove(sess.getUniqueId());

			// Close the passthru authentication session

			try {

				// Close the authentication session

				AuthenticateSession authSess = passDetails.getAuthenticateSession();
				authSess.CloseSession();

				// DEBUG

				if ( hasDebug())
					Debug.println("Closed auth session, sessId=" + authSess.getSessionId());
			}
			catch (Exception ex) {

				// Debug

				if ( hasDebug())
					Debug.println("Passthru error closing session (closed) " + ex.getMessage());
			}
		}
	}

	/**
	 * SMB server session created notification
	 * 
	 * @param sess SrvSession
	 */
	public void sessionCreated(SrvSession sess) {

	}

	/**
	 * User successfully logged on notification
	 * 
	 * @param sess SrvSession
	 */
	public void sessionLoggedOn(SrvSession sess) {

		// Check if there is an active session to the authentication server for this local
		// session

		PassthruDetails passDetails = (PassthruDetails) m_sessions.get(sess.getUniqueId());

		if ( passDetails != null) {
			// Remove the passthru session from the active list

			m_sessions.remove(sess.getUniqueId());

			// Close the passthru authentication session

			try {
				// Close the authentication session

				AuthenticateSession authSess = passDetails.getAuthenticateSession();
				authSess.CloseSession();

				// DEBUG

				if ( hasDebug())
					Debug.println("Closed auth session, sessId=" + authSess.getSessionId());
			}
			catch (Exception ex) {

				// Debug

				if ( hasDebug())
					Debug.println("Passthru error closing session (logon) " + ex.getMessage());
			}
		}
	}
}
