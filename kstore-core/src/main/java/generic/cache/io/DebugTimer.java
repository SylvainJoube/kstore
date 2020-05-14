package generic.cache.io;

import java.util.ArrayList;
import java.util.List;

/**
 * 
 * Timer, used for debug and benchmak purposes.
 * 
 * @author Sylvain Joube
 *
 */
class DebugTimer {
	
	static class LocalMessage {
		public long mstime;
		public String message;
		
		public LocalMessage(String msg) {
			message = msg;
			mstime = System.currentTimeMillis();
		}
	}
	
	
	protected static List<LocalMessage> lsteps = new ArrayList<>();
	
	public static void reset() {
		lsteps.clear();
	}
	
	public static void step(String message) {
		LocalMessage msg = new LocalMessage(message);
		lsteps.add(msg);
	}
	
	public static void printDelta() {
		LocalMessage lastMessage = null;
		for (LocalMessage msg : lsteps) {
			if (lastMessage != null) {
				long delta = msg.mstime - lastMessage.mstime;
				DebugLog.info(msg.message + " : " + delta, 1);
			} else {
				DebugLog.info("Start time(ms) : " + msg.mstime + " " + msg.message, 1);
			}
			lastMessage = msg;
		}
	}
	
}
