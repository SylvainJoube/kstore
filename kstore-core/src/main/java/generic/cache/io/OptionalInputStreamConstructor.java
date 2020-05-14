package generic.cache.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * Inspired by the LineReader interface.
 * 
 * 
 * @author Sylvain Joube
 * @version 2020-05 v1
 */
public interface OptionalInputStreamConstructor {
	
	InputStream create() throws IOException;
	
	/*public static interface LineReader {

		InputStream readNext() throws IOException;
	}*/

}
