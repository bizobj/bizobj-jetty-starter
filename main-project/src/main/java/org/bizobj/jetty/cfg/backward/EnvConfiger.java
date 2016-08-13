package org.bizobj.jetty.cfg.backward;

import java.util.HashMap;
import java.util.Map;

import org.bizobj.jetty.ContextStarter;
import org.bizobj.jetty.cfg.Configer;

/**
 * Configer which read configurations from Environment variables:
 * <pre>
 *  - HTTP_PORT: http port, default is 8080
 *  - CTX_PATH:  context path
 *  - JDBC_URL:  jdbc url
 * </pre>
 * @author bizobj.org
 */
public class EnvConfiger implements Configer {
	public static final String VAR_JDBC_URL = "JDBC_URL";
	public static final String VAR_CTX_PATH = "CTX_PATH";
	public static final String VAR_HTTP_PORT = "HTTP_PORT";

	@Override
	public int getHttpPort() {
		int httpPort = Integer.valueOf(_readEnv(VAR_HTTP_PORT, ContextStarter.DEFAULT_HTTP_PORT));
		return httpPort;
	}

	@Override
	public String getContextPath() {
		String ctxPath = _readEnv(VAR_CTX_PATH, ContextStarter.DEFAULT_CTX_PATH);
		return ctxPath;
	}

	/**
	 * NOTE: 1.The jndi name is the same as context path; 2.supports only ONE jdbc url.
	 */
	@Override
	public Map<String, String> getJdbcUrls() {
		String url = _readEnv(VAR_JDBC_URL, ContextStarter.DEFAULT_JDBC_URL);
		Map<String, String> result = new HashMap<String, String>();
		result.put(getContextPath(), url);	//The jndi name is the same as context path
		return result;	//Yes, this implementation supports only ONE jdbc url
	}

    private static String _readEnv(String var, String defVal){
        String v = System.getProperty(var);
        if (null==v){
            v=System.getenv(var);
        }
        if (null==v){
            v=defVal;
        }
        if (null!=v){
            System.setProperty(var, v);        //Remember the real variable value into System Properties
        }
        return v;
    }
}
