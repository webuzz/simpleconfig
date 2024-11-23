package im.webuzz.config.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Mark the field that it can only be updated by local file.
// If a field contains value that should only be updated from local side,
// mark it local only, so it won't be updated from remote server
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigLocalOnly {

}
