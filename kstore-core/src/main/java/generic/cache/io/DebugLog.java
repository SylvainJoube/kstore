package generic.cache.io;

import java.util.concurrent.atomic.AtomicInteger;

/** 
 *  Local debug, very handy for me !
 *  
 *  @author Sylvain Joube
 */
class DebugLog {
	public final static boolean SHOW_LOG = true;
	public final static boolean SHOW_ERRORS = true;
	public final static boolean SHOW_WARNINGS = true;
	
	enum MsgType {
		info, warning, error;
	}
	
	private static int baseLogLevel = 2;
	
	private static AtomicInteger traceExeStep = new AtomicInteger(0);
	
	// log simple
	public static void log(String message) {
		log(message, 1);
	}
	public static void info(String message) {
		info(message, 1);
	}
	

	public static void log(String message, int level) {
		if (SHOW_LOG) {
			fullLog(message, MsgType.info, level);
		}
	}
	public static void info(String message, int level) {
		if (SHOW_LOG) {
			fullLog(message, MsgType.info, level);
		}
	}

	// erreur
	public static void error(String message, int level) {
		if (SHOW_ERRORS) {
			fullLog(message, MsgType.error, level);
		}
	}
	public static void error(String message) {
		error(message, 1);
	}

	// warning
	public static void warning(String message, int level) {
		if (SHOW_WARNINGS) {
			fullLog(message, MsgType.warning, level);
		}
	}
	public static void warning(String message) {
		warning(message, 1);
	}
	
	public static void traceExec() {
		info("" + traceExeStep.getAndAdd(1) + " trace", 1);
	}
	
	public static void resetExec() {
		traceExeStep.set(0);
	}
	
	
	
	// log générique
	private static void fullLog(String message, MsgType type, int level) {
		
		StackTraceElement[] slist = new Exception().getStackTrace();			
		StackTraceElement e = slist[baseLogLevel + level]; // level 2 est bon
		
		// I was actuallt too lazy to use a StringBuilder here
		
		String prefix = null;
		if (e.isNativeMethod()) {
			prefix = "[native]";
		} else {
			String fileName = e.getFileName();
			//String classFullPrefix = null;
			
			if (fileName != null) {
				String className = fileName.substring(0, fileName.lastIndexOf(".")); //.substring(f.getAbsolutePath().lastIndexOf("\\")+1)
				
				String methodName = e.getMethodName();
				
				if (methodName != null) {
					prefix = "[" + className + "." + methodName + "]";
				} else {
					prefix = "[" + className + ".?]";
				}
			}
		}
		
		if (prefix == null) {
			prefix = "";
		} else {
			prefix = prefix + "  ";
		}
		
		
		// Code adapté à mes besoins
		String fileLink = 
				" "
				+ (e.isNativeMethod() ? "(Native Method)" :
					(e.getFileName() != null && e.getLineNumber() >= 0 ?
							"(" + e.getFileName() + ":" + e.getLineNumber() + ")" :
								(e.getFileName() != null ?  "("+e.getFileName()+")" : "(Unknown Source)")));
		
		String spacesBeforeFileLink = "     ";
		
		String fullMessage = prefix + message + spacesBeforeFileLink + fileLink;
		
		switch (type) {
		case info:    System.out.println(fullMessage); break;
		case warning: System.err.println("WARNING " + fullMessage); break;
		case error:   System.err.println("ERROR " + fullMessage); break;
		default: break;
		}
		
		
	}
	
	
}