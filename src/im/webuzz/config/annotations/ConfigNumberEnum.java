package im.webuzz.config.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// For example
// @ConfigEnum({"ACTIVE", "INACTIVE", "PENDING"})
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigNumberEnum {
	double[] value();
}