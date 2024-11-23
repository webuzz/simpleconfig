package im.webuzz.config.annotations;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target(FIELD)
@interface NotEmptyTags {
	ConfigNotEmpty[] value();
}

@Target({FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(NotEmptyTags.class)
public @interface ConfigNotEmpty {
	int depth() default 0;
}
