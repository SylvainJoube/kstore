package generic.cache.io;

import java.io.IOException;
import java.io.InputStream;

/** 
 *  Refers to the {@link CachedInputStream#CachedInputStream} constructor.
 *  This function is only called when the given URI was not found in the caches locally stored.
 *  Should create an InputStream accessing the ressource with the URI passed to
 *  the {@link CachedInputStream#CachedInputStream} constructor.
 *  
 *  @see CachedInputStream#CachedInputStream(String, OptionalInputStreamConstructor)
 *
 *  @author Sylvain Joube
 *  @version 2020-05 v1
 */
public interface OptionalInputStreamConstructor {
	
	/** Refers to the {@link CachedInputStream#CachedInputStream} constructor.
	 *  This function is only called when the given URI was not found in the caches locally stored.
	 *  Should create an InputStream accessing the ressource with the URI passed to
	 *  the {@link CachedInputStream#CachedInputStream} constructor.
	 *  
	 *  @see CachedInputStream#CachedInputStream(String, OptionalInputStreamConstructor)
	 * */
	InputStream create() throws IOException;

}
