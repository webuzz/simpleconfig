package im.webuzz.config.annotation;

//import static java.lang.annotation.ElementType.FIELD;

//import java.lang.annotation.Retention;
//import java.lang.annotation.RetentionPolicy;
//import java.lang.annotation.Target;

/**
 * If this annotation is the first annotation configured for a given field, then all
 * existing annotations declared to the field from the source level will be discarded.
 *
 * This annotation will be created by AnnotationProxy only via configuration files.
 */
//@Target({FIELD})
//@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigOverridden {

}
