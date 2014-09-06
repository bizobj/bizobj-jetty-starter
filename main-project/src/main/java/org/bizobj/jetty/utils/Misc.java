package org.bizobj.jetty.utils;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class Misc {
	public static String[] findClasspathResources(String resourceName){
		try{
			List<String> results = new ArrayList<String>();
			
			ClassLoader cl = Misc.class.getClassLoader();
			Enumeration<URL> urls = cl.getResources(resourceName);
			while(urls.hasMoreElements()){
				URL u = urls.nextElement();
				String url = u.toString();
				//ref: org.springframework.util.ResourceUtils.toURI(String)
				URI i = new URI(url.replace(" ", "%20"));
				results.add(i.toString());
			}
			return results.toArray(new String[0]);
		}catch(Exception ex){
			throwRuntime(ex);
			return null;	//Never reached code
		}
	}

    /**
	 * Catch all kinds of Exceptions and throw as RuntimeException
	 * @param t
	 * @throws RuntimeException
	 */
	public static void throwRuntime(Throwable throwable) throws RuntimeException {
		if (throwable instanceof RuntimeException) {
            throw (RuntimeException) throwable;
        }
        throw new RuntimeException(throwable);
	}
	
	/**
	 * Find the root cause which match the given level exception type, if not found, return null
	 * @param topEx The exception in the top of stack
	 * @param stopLevel exception type to find
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static <T> T findCause(Throwable topEx, Class<T> stopLevel){
		Throwable cause = topEx;
		while(null!=cause){
			if (stopLevel.isInstance(cause)){
				return (T) cause;
			}
			cause = cause.getCause();
		}
		return null;
	}
}
