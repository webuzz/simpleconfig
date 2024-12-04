package im.webuzz.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// For example:
// @ConfigRange(min = 0, max = 100)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigRange {
	double min() default Double.MIN_VALUE;
	double max() default Double.MAX_VALUE;
}
