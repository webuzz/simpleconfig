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

import static im.webuzz.config.generator.GeneratorConfig.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import im.webuzz.config.InternalConfigUtils;
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
	
	protected Set<Field> commentGeneratedFields;

	public CommentWriter(CommentWrapper wrapper) {
		this.commentWrapper = wrapper;
		this.annWriter = new AnnotationWriter();
		this.commentClassWriter = new CommentClassWriter();
		this.commentGeneratedFields = Collections.synchronizedSet(new HashSet<Field>());
	}

	public int appendConfigComment(StringBuilder builder, ConfigComment configAnn) {
		if (configAnn == null) return 0;
		String[] comments = configAnn.value();
		if (comments == null || comments.length == 0) return 0;
		if (comments.length == 1) {
			commentWrapper.startLineComment(builder);
			builder.append(comments[0]);
			commentWrapper.endLineComment(builder);
		} else {
			commentWrapper.startBlockComment(builder);
			for (int i = 0; i < comments.length; i++) {
				commentWrapper.addMiddleComment(builder).append(comments[i]).append("\r\n");
			}
			commentWrapper.endBlockComment(builder); // If ended, line break should be appended.
		}
		return comments.length;
	}
	
	protected int appendFieldAnnotation(StringBuilder builder, Annotation[] anns) {
		for (Annotation ann : anns) {
			commentWrapper.startLineComment(builder);
			annWriter.appendAnnotation(builder, ann, anns.length > 1);
			commentWrapper.endLineComment(builder);
		}
		return anns.length;
	}

	protected int appendAllFieldAnnotations(StringBuilder annBuilder, Field f) {
		int annCount = 0;
		List<Annotation> anns = InternalConfigUtils.getAllKnownAnnotations(f,
				new Class<?>[] { ConfigNotNull.class, ConfigNotEmpty.class, ConfigLength.class, ConfigPreferredCodec.class });
		Class<?>[] annotationOrders = new Class<?>[] {
			ConfigOverridden.class,
			Configurable.class,
			ConfigLocalOnly.class,
			ConfigNotNull.class,
			ConfigNotEmpty.class,
			ConfigLength.class,
			ConfigEnum.class,
			ConfigPattern.class,
			ConfigPreferredCodec.class,
			ConfigNonNegative.class,
			ConfigPositive.class,
			ConfigRange.class,
			ConfigNumberEnum.class,
			ConfigSince.class,
		};
		
		int allSize = anns.size();
		for (Class<?> clz : annotationOrders) {
			List<Annotation> annotations = new ArrayList<Annotation>();
			for (Annotation ann : anns) {
				if (clz.isAssignableFrom(ann.annotationType())) {
					annotations.add(ann);
				}
			}
			int size = annotations.size();
			if (size == 0) continue;
			annCount += appendFieldAnnotation(annBuilder, annotations.toArray(new Annotation[size]));
			if (annCount == allSize) break;
		}
		return annCount;
	}

	protected int appendAllTypeAnnotations(StringBuilder annBuilder, Class<?> clz) {
		int annCount = 0;
		List<Annotation> anns = InternalConfigUtils.getAllKnownAnnotations(clz, null);
		int annSize = 0;
		if (anns == null || (annSize = anns.size()) == 0) return 0;
		Class<?>[] annotationOrders = new Class<?>[] {
			ConfigOverridden.class,
			ConfigKeyPrefix.class,
			ConfigLocalOnly.class,
			ConfigSince.class,
		};
		
		for (Class<?> annClz : annotationOrders) {
			List<Annotation> annotations = new ArrayList<Annotation>();
			for (Annotation ann : anns) {
				if (annClz.isAssignableFrom(ann.annotationType())) {
					annotations.add(ann);
				}
			}
			int filteredSize = annotations.size();
			if (filteredSize > 0) {
				annCount += appendFieldAnnotation(annBuilder, annotations.toArray(new Annotation[filteredSize]));
				if (annCount == annSize) break;
			}
		}
		return annCount;
	}
	
	protected void generateTypeComment(StringBuilder builder, Class<?> clz) {
		if (addTypeComment) appendConfigComment(builder, clz.getAnnotation(ConfigComment.class));
		if (addTypeAnnotationComment) appendAllTypeAnnotations(builder, clz);
	}

	protected void generateFieldComment(StringBuilder builder, Field f, boolean topConfigClass) {
		if (!topConfigClass && !commentGeneratedFields.add(f)) return; // already generated for given object fields
		/*
		if ("configurationPackages".equals(f.getName())) {
			System.out.println("Debug");
		}
		//*/
		if (addFieldComment) appendConfigComment(builder, f.getAnnotation(ConfigComment.class));
		if (addFieldAnnotationComment) appendAllFieldAnnotations(builder, f);
		if (addFieldTypeComment) {
			Class<?> type = f.getType();
			if (!skipSimpleTypeComment
					|| (type != int.class && type != String.class && type != boolean.class)) {
				Type paramType = f.getGenericType();
				commentWrapper.startLineComment(builder);
				commentClassWriter.appendFieldType(builder, type, paramType);
				commentWrapper.endLineComment(builder);
			}
		}
	}

}
