package org.json.rpc.server;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.servlet.ServletContext;

/**
 * Annotation indicating an injection dependency on one additional parameter:
 * {@link ServletContext}.
 * 
 * @see InjectedParameters for definition of the parameters
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD })
public @interface ServletContextAware {
	// intentionally empty
}
