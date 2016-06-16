package com.auth.acl;

import org.alfresco.jlan.server.auth.acl.ACLParseException;
import org.alfresco.jlan.server.auth.acl.AccessControl;
import org.alfresco.jlan.server.auth.acl.AccessControlParser;
import org.springframework.extensions.config.ConfigElement;

public class UserAccessControlParserForDB extends AccessControlParser {
	private final String parseType = "dbuser";

	public AccessControl createAccessControl(ConfigElement arg0)
			throws ACLParseException {
		// TODO Auto-generated method stub
		return null;
	}
	public String getType() {
		return parseType;
	}
}
