package im.webuzz.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// For example:
// @ConfigPattern(value = "\\d{4}-\\d{2}-\\d{2}")
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigPattern {
	String value() default ".*"; // Regular expression
}
