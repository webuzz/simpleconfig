package im.webuzz.config.annotation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Retention(RUNTIME)
@Target(FIELD)
@interface CodecTags {
	ConfigCodec[] value();
}

@Target({FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(CodecTags.class)
public @interface ConfigCodec {
	String[] value() default {}; // Empty preferences
	boolean mapKey() default false;
	boolean mapValue() default false;
	int depth() default -1; // Default no depth limits
}
