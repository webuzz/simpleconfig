package im.webuzz.config.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// For example:
// @ConfigLength(min = 1, max = 32)
// @ConfigLength(min = 8)
// @ConfigLength(max = 32)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigLength {
	int min() default 0;
	int max() default Integer.MAX_VALUE;
}
