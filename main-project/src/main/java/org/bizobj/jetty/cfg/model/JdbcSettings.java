package org.bizobj.jetty.cfg.model;

public class JdbcSettings {
	private String datasourceName;
    private String jdbcUrl;
    private String dbUser;
    private String dbPass;
    
    public JdbcSettings(String datasourceName, String jdbcUrl, String dbUser, String dbPass) {
    	this.datasourceName = datasourceName;
		this.jdbcUrl = jdbcUrl;
		this.dbUser = dbUser;
		this.dbPass = dbPass;
	}

	public String getJdbcUrl() {
		return jdbcUrl;
	}
	public String getDbUser() {
		return dbUser;
	}
	public String getDbPass() {
		return dbPass;
	}
    public String getJndiName(){
        return "jdbc/"+this.datasourceName;
    }
	
	/**
	 * Analysis JDBC URL String: user/pass@... , and build {@link JdbcSettings}
	 * @param datasourceName
	 * @param rawUrl
	 * @return
	 */
	public static JdbcSettings build(String datasourceName, String rawUrl){
        int firstAt = rawUrl.indexOf('@');
        if (firstAt<0){
        	return new JdbcSettings(datasourceName, rawUrl, "", "");
        }else{
        	String userpass = rawUrl.substring(0, firstAt);
        	String url = rawUrl.substring(firstAt+1);
        	String[] tmp = userpass.split("\\/");
        	String user = tmp.length>0?tmp[0]:"";
        	String pass = tmp.length>1?tmp[1]:"";
        	
        	return new JdbcSettings(datasourceName, url, user, pass);
        }
	}

    private String _jdbcUrl(){
        return (null==this.jdbcUrl)?"":this.jdbcUrl;
    }
    /** jdbc:oracle:thin:@localhost:1521:XE */
    private boolean isOracle(){
        return _jdbcUrl().startsWith("jdbc:oracle:thin:");
    }
    /** jdbc:sqlserver://localhost:1433;databaseName=orderMgr */
    private boolean isMSSQL(){
        return _jdbcUrl().startsWith("jdbc:sqlserver://");
    }
    /** jdbc:jtds:sqlserver://localhost:1433/orderMgr */
    private boolean isMSSQL_JTDS(){
        return _jdbcUrl().startsWith("jdbc:jtds:sqlserver://");
    }
    /** jdbc:mysql://localhost:3306/orderMgr?useUnicode=true&amp;characterEncoding=UTF-8 */
    private boolean isMySQL(){
        return _jdbcUrl().startsWith("jdbc:mysql://");
    }
    /** jdbc:hsqldb:hsql://localhost/TestHSQLDB */
    private boolean isHSQL(){
        return _jdbcUrl().startsWith("jdbc:hsqldb:");
    }
    
    public String getJdbcDriver(){
        if (isOracle()){
            return "oracle.jdbc.driver.OracleDriver";
        }else if (isMSSQL()){
            return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        }else if (isMSSQL_JTDS()){
            return "net.sourceforge.jtds.jdbc.Driver";
        }else if (isMySQL()){
            return "com.mysql.jdbc.Driver";
        }else if (isHSQL()){
            return "org.hsqldb.jdbcDriver";
        }else{
            throw new RuntimeException("Unknown database type ["+this.jdbcUrl+"]");
        }
    }
    public String getValidationQuery(){
        if (isOracle()){
            return "SELECT 1 From dual";
        }else if (isMSSQL()){
            return "Select 1";
        }else if (isMSSQL_JTDS()){
            return "Select 1";
        }else if (isMySQL()){
            return "Select 1";
        }else if (isHSQL()){
            return "Select COUNT(*) As X From INFORMATION_SCHEMA.SYSTEM_USERS Where 1=0";
        }else{
            throw new RuntimeException("Unknown database type ["+this.jdbcUrl+"]");
        }
    }
}