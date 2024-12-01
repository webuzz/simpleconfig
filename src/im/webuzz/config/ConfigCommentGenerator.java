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

package im.webuzz.config;

import static im.webuzz.config.GeneratorConfig.addFieldComment;
import static im.webuzz.config.GeneratorConfig.addTypeComment;
import static im.webuzz.config.GeneratorConfig.skipSimpleTypeComment;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

import im.webuzz.config.annotations.*;

public abstract class ConfigCommentGenerator extends ConfigCompactGenerator {

	protected Set<Field> commentGeneratedFields;

	public ConfigCommentGenerator() {
		commentGeneratedFields = new HashSet<Field>();
	}

	// To provide a line of comment, e.g. a field's type
	protected abstract void startLineComment(StringBuilder builder);
	protected abstract void endLineComment(StringBuilder builder);
	
	// To provide more information about a type or a field 
	protected abstract void startBlockComment(StringBuilder builder);
	protected abstract StringBuilder addMiddleComment(StringBuilder builder);
	protected abstract void endBlockComment(StringBuilder builder);

	protected boolean appendConfigComment(StringBuilder builder, ConfigComment configAnn) {
		if (configAnn == null) return false;
		String[] comments = configAnn.value();
		if (comments == null || comments.length == 0) return false;
		if (comments.length > 1) {
			startBlockComment(builder);
			for (int i = 0; i < comments.length; i++) {
				addMiddleComment(builder).append(comments[i]).append("\r\n");
			}
			endBlockComment(builder); // If ended, line break should be appended.
		} else {
			startLineComment(builder);
			builder.append(comments[0]);
			endLineComment(builder);
		}
		return true;
	}


	protected int appendFieldAnnotation(StringBuilder builder, Annotation[] anns) {
		for (Annotation ann : anns) {
			startLineComment(builder);
			ConfigValidator.appendAnnotation(builder, ann, anns.length > 1);
			endLineComment(builder);
		}
		return anns.length;
	}

	protected int appendAllFieldAnnotations(StringBuilder annBuilder, Field f) {
		int annCount = 0;
		// The followings are array/list/set/map/object related
		annCount += appendFieldAnnotation(annBuilder, f.getAnnotationsByType(ConfigNotNull.class));
		annCount += appendFieldAnnotation(annBuilder, f.getAnnotationsByType(ConfigNotEmpty.class));
		annCount += appendFieldAnnotation(annBuilder, f.getAnnotationsByType(ConfigLength.class));
		// The followings are string related
		annCount += appendFieldAnnotation(annBuilder, f.getAnnotationsByType(ConfigEnum.class));
		annCount += appendFieldAnnotation(annBuilder, f.getAnnotationsByType(ConfigPattern.class));
		annCount += appendFieldAnnotation(annBuilder, f.getAnnotationsByType(ConfigCodec.class));
		// The followings are number related
		annCount += appendFieldAnnotation(annBuilder, f.getAnnotationsByType(ConfigNonNegative.class));
		annCount += appendFieldAnnotation(annBuilder, f.getAnnotationsByType(ConfigPositive.class));
		annCount += appendFieldAnnotation(annBuilder, f.getAnnotationsByType(ConfigRange.class));
		annCount += appendFieldAnnotation(annBuilder, f.getAnnotationsByType(ConfigNumberEnum.class));
		// The followings are version controlling related
		annCount += appendFieldAnnotation(annBuilder, f.getAnnotationsByType(ConfigSince.class));
		return annCount;
	}

	protected void generateFieldComment(StringBuilder builder, Field f, boolean topConfigClass) {
		if (!commentGeneratedFields.add(f)) return; // already generated
		
		if (addFieldComment) {
			appendConfigComment(builder, f.getAnnotation(ConfigComment.class));
		}
		if (addTypeComment) {
			Class<?> type = f.getType();
			if (skipSimpleTypeComment
					&& (type == int.class || type == String.class || type == boolean.class)) {
				return;
			}
			appendAllFieldAnnotations(builder, f);
			startLineComment(builder);
			Type paramType = f.getGenericType();
			Utils.appendFieldType(builder, type, paramType, true);
			endLineComment(builder);
		}
	}

}
