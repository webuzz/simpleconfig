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

package im.webuzz.config.generator;

import static im.webuzz.config.generator.GeneratorConfig.addFieldComment;
import static im.webuzz.config.generator.GeneratorConfig.addTypeComment;
import static im.webuzz.config.generator.GeneratorConfig.skipSimpleTypeComment;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import im.webuzz.config.annotation.*;

public class CommentWriter {

	public static interface CommentWrapper {

		// To provide a line of comment, e.g. a field's type
		public void startLineComment(StringBuilder builder);
		public void endLineComment(StringBuilder builder);
		
		// To provide more information about a type or a field 
		public void startBlockComment(StringBuilder builder);
		public StringBuilder addMiddleComment(StringBuilder builder);
		public void endBlockComment(StringBuilder builder);
	}
	
	protected CommentWrapper commentWrapper;
	public AnnotationWriter annWriter;
	public CommentClassWriter commentClassWriter;
	
	public static Set<Field> commentGeneratedFields = Collections.synchronizedSet(new HashSet<Field>());

	public CommentWriter(CommentWrapper wrapper) {
		this.commentWrapper = wrapper;
		this.annWriter = new AnnotationWriter();
		this.commentClassWriter = new CommentClassWriter();
	}

	public boolean appendConfigComment(StringBuilder builder, ConfigComment configAnn) {
		if (configAnn == null) return false;
		String[] comments = configAnn.value();
		if (comments == null || comments.length == 0) return false;
		if (comments.length > 1) {
			commentWrapper.startBlockComment(builder);
			for (int i = 0; i < comments.length; i++) {
				commentWrapper.addMiddleComment(builder).append(comments[i]).append("\r\n");
			}
			commentWrapper.endBlockComment(builder); // If ended, line break should be appended.
		} else {
			commentWrapper.startLineComment(builder);
			builder.append(comments[0]);
			commentWrapper.endLineComment(builder);
		}
		return true;
	}


	protected int appendFieldAnnotation(StringBuilder builder, Annotation[] anns) {
		for (Annotation ann : anns) {
			commentWrapper.startLineComment(builder);
			annWriter.appendAnnotation(builder, ann, anns.length > 1);
			commentWrapper.endLineComment(builder);
		}
		return anns.length;
	}

	public int appendAllFieldAnnotations(StringBuilder annBuilder, Field f) {
		int annCount = 0;
		// The followings are array/list/set/map/object related
		annCount += appendFieldAnnotation(annBuilder, f.getAnnotationsByType(ConfigNotNull.class));
		annCount += appendFieldAnnotation(annBuilder, f.getAnnotationsByType(ConfigNotEmpty.class));
		annCount += appendFieldAnnotation(annBuilder, f.getAnnotationsByType(ConfigLength.class));
		// The followings are string related
		annCount += appendFieldAnnotation(annBuilder, f.getAnnotationsByType(ConfigEnum.class));
		annCount += appendFieldAnnotation(annBuilder, f.getAnnotationsByType(ConfigPattern.class));
		annCount += appendFieldAnnotation(annBuilder, f.getAnnotationsByType(ConfigPreferredCodec.class));
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
			commentWrapper.startLineComment(builder);
			Type paramType = f.getGenericType();
			commentClassWriter.appendFieldType(builder, type, paramType);
			commentWrapper.endLineComment(builder);
		}
	}

}
