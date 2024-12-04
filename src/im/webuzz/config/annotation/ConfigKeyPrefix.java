/*******************************************************************************
 * Copyright (c) 2010 - 2024 webuzz.im and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Source hosted at
 * https://github.com/webuzz/simpleconfig
 * 
 * Contributors:
 *   Zhou Renjian / zhourenjian@gmail.com - initial API and implementation
 *******************************************************************************/

package im.webuzz.config.annotation;

import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Supports using annotation to configure configuration file name.
 * 
 * It has the same effect of adding a static final field:
 * static final String configKeyPrefix = "...";
 * 
 * Using this annotation will make target configuration class dependent of this
 * class, which will require jar file packing this class, while using static
 * final field configKeyPrefix will keep target configuration class independent
 * of any other classes.
 * 
 * @author zhourenjian
 */
@Target({TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigKeyPrefix {
	String value();
}
