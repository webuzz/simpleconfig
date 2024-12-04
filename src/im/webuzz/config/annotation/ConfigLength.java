package im.webuzz.config.annotation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target(FIELD)
@interface LengthTags {
	ConfigLength[] value();
}

// For example:
// @ConfigLength(min = 1, max = 32)
// @ConfigLength(min = 8)
// @ConfigLength(max = 32)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(LengthTags.class)
public @interface ConfigLength {
	int min() default 0;
	int max() default Integer.MAX_VALUE;
	int depth() default 0;
}
