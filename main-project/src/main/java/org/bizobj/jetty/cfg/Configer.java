package org.bizobj.jetty.cfg;

import java.util.Map;

/**
 * The interface to define the configuration of jetty server starter
 * @author bizobj.org
 */
public interface Configer {
	/**
	 * The HTTP Port
	 * @return
	 */
	public int getHttpPort();
	
	/**
	 * The context path, without both the heading and tailing "/".
	 * @return
	 */
	public String getContextPath();
	
	/**
	 * One or more JDBC URL to create datasource.
	 * @return key - datasourceName, value - jdbc url;
	 *          The format of url is [user/password@jdbc-url], such as "sa/XXXX@jdbc:hsqldb:mem:bizobj"
	 */
	public Map<String, String> getJdbcUrls();
}
