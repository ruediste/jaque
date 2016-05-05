/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.trigersoft.jaque.expression;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;

class ExpressionClassCracker {

	private static final String DUMP_FOLDER_SYSTEM_PROPERTY = "jdk.internal.lambda.dumpProxyClasses";
	private static final URLClassLoader lambdaClassLoader;
	private static final String lambdaClassLoaderCreationError;

	static {
		String folderPath = System.getProperty(DUMP_FOLDER_SYSTEM_PROPERTY);
		if (folderPath == null) {
			lambdaClassLoaderCreationError = "Ensure that the '" + DUMP_FOLDER_SYSTEM_PROPERTY
					+ "' system property is properly set.";
			lambdaClassLoader = null;
		} else {
			File folder = new File(folderPath);
			if (!folder.isDirectory()) {
				lambdaClassLoaderCreationError = "Ensure that the '" + DUMP_FOLDER_SYSTEM_PROPERTY
						+ "' system property is properly set (" + folderPath + " does not exist).";
				lambdaClassLoader = null;
			} else {
				URL folderURL;
				try {
					folderURL = folder.toURI().toURL();
				} catch (MalformedURLException mue) {
					throw new RuntimeException(mue);
				}

				lambdaClassLoaderCreationError = null;
				lambdaClassLoader = new URLClassLoader(new URL[] { folderURL });
			}
		}
	}

	LambdaExpression<?> lambda(Object lambda) {
		Class<?> lambdaClass = lambda.getClass();
		if (!lambdaClass.isSynthetic())
			throw new IllegalArgumentException("The requested object is not a Java lambda");

		if (lambda instanceof Serializable) {
			SerializedLambda extracted = SerializedLambda.extractLambda((Serializable) lambda);

			ExpressionClassVisitor actualVisitor = parseClass(lambdaClass.getClassLoader(),
					classFilePath(extracted.implClass), extracted.implMethodName, extracted.implMethodSignature);
			Object[] capturedArgs = extracted.capturedArgs;

			return createLambda(actualVisitor, capturedArgs);

		}

		ExpressionClassVisitor lambdaVisitor = parseFromFileSystem(lambda, lambdaClass);

		InvocationExpression invocationOfActualMethod = (InvocationExpression) Expression
				.stripConverts(lambdaVisitor.getResult());

		// the actual implementation of the lambda
		Method actualMethod = (Method) ((MemberExpression) invocationOfActualMethod).getMember();

		// short-circuit method references
		if (!actualMethod.isSynthetic()) {
			return Expression.lambda(lambdaVisitor.getReturnType(), invocationOfActualMethod,
					Collections.unmodifiableList(Arrays.asList(lambdaVisitor.getParameterTypes())));
		}

		// TODO: in fact must recursively parse all the synthetic methods,
		// so must have a relevant visitor. and then another visitor to
		// reduce forwarded calls

		Class<?> actualClass = actualMethod.getDeclaringClass();
		ClassLoader actualClassLoader = actualClass.getClassLoader();
		String actualClassPath = classFilePath(actualClass.getName());

		// visitor of the actual implementation of the lambda
		ExpressionClassVisitor actualVisitor = parseClass(actualClassLoader, actualClassPath, actualMethod);

		// create a lambda invocation using the captured arguments
		LambdaInvocationExpression lambdaInvocation = Expression.invokeLambda(
				Arrays.asList(actualVisitor.getParameterTypes()),
				Expression.convert(actualVisitor.getResult(), actualVisitor.getReturnType()),
				invocationOfActualMethod.getArguments());

		return Expression.lambda(lambdaVisitor.getReturnType(),
				Expression.convert(lambdaInvocation, lambdaVisitor.getReturnType()),
				Arrays.asList(lambdaVisitor.getParameterTypes()));
	}

	/**
	 * 
	 * @param lambdaInterfaceMethod
	 *            implemented mehtod of the lambda interface
	 * @param lambdaImpl
	 *            method implementing the lambda expression
	 * @param capturedArgs
	 *            captured arguments, including leading this reference if
	 *            applicable
	 */
	<T> LambdaExpression<T> parseLambdaMethod(Method lambdaInterfaceMethod, Member lambdaImpl, Object[] capturedArgs) {
		boolean isStatic = (lambdaImpl.getModifiers() & Modifier.STATIC) != 0;

		if (!lambdaImpl.isSynthetic()) {
			// this means we're dealing with a method reference
			Class<?>[] parameterTypes = ((Method) lambdaImpl).getParameterTypes();
			List<Expression> parameters = new ArrayList<>();
			for (int i = 0; i < ((Method) lambdaImpl).getParameterCount(); i++) {
				parameters.add(new ParameterExpression(parameterTypes[i], i));
			}
			return new LambdaExpression<>(lambdaInterfaceMethod,
					new MemberExpression(MemberExpressionType.MethodAccess,
							isStatic ? null : new ThisExpression(lambdaImpl.getDeclaringClass()), lambdaImpl,
							((Method) lambdaImpl).getReturnType(), Arrays.asList(parameterTypes), parameters),
					isStatic ? null : capturedArgs[0], new Object[] {});
		}
		// parse the method
		ExpressionClassVisitor visitor = parseClass(lambdaImpl.getDeclaringClass().getClassLoader(),
				lambdaImpl.getDeclaringClass().getName().replace('.', '/') + ".class", (Method) lambdaImpl);

		// bind the local variable references to the this reference, captured
		// arguments and parameters
		Expression expr;
		{
			int firstCapturedArgIndex = isStatic ? 0 : 1;
			int firstParameterIndex = firstCapturedArgIndex + capturedArgs.length;

			expr = visitor.getResult().accept(new CopyExpressionVisitor() {
				@Override
				public Expression visit(GetLocalVariableExpression e) {
					if (e.getIndex() >= firstParameterIndex) {
						return new ParameterExpression(e.getResultType(), e.getIndex() - firstParameterIndex);
					}
					if (e.getIndex() >= firstCapturedArgIndex)
						return new CapturedArgumentExpression(e.getResultType(), e.getIndex() - firstCapturedArgIndex);
					return new ThisExpression(e.getResultType());
				}
			});
		}
		return new LambdaExpression<T>(lambdaInterfaceMethod, expr, isStatic ? null : capturedArgs[0],
				isStatic ? capturedArgs : Arrays.copyOfRange(capturedArgs, 1, capturedArgs.length));

	}

	private LambdaExpression<?> createLambda(ExpressionClassVisitor actualVisitor, Object[] capturedArgs) {
		ArrayList<Expression> args = new ArrayList<>();
		for (int i = 0; i < capturedArgs.length; i++) {
			args.add(Expression.constant(capturedArgs[i], actualVisitor.getParameterTypes()[i]));
		}
		return createLambda(actualVisitor, args);
	}

	private LambdaExpression<?> createLambda(ExpressionClassVisitor actualVisitor, List<Expression> capturedArgs) {
		if (capturedArgs == null || capturedArgs.size() == 0) {
			// no arguments were captured, simply create a lambda expression
			// from the invoked method
			return Expression.lambda(actualVisitor.getReturnType(),
					Expression.convert(actualVisitor.getResult(), actualVisitor.getReturnType()),
					Arrays.asList(actualVisitor.getParameterTypes()));
		}

		// create a lambda expression binding the actual lambda expression to
		// the captured args
		List<Expression> args = new ArrayList<>(actualVisitor.getParameterTypes().length);
		int capturedLength = capturedArgs.size();
		for (int i = 0; i < capturedLength; i++) {
			args.add(capturedArgs.get(i));
		}

		List<Class<?>> finalParameterTypes = new ArrayList<>();
		for (int y = capturedLength; y < actualVisitor.getParameterTypes().length; y++) {
			ParameterExpression arg = Expression.parameter(actualVisitor.getParameterTypes()[y], y - capturedLength);
			args.add(arg);
			finalParameterTypes.add(actualVisitor.getParameterTypes()[y]);
		}

		LambdaInvocationExpression boundInvocation = Expression
				.invokeLambda(Arrays.asList(actualVisitor.getParameterTypes()), actualVisitor.getResult(), args);
		return Expression.lambda(actualVisitor.getReturnType(),
				Expression.convert(boundInvocation, actualVisitor.getReturnType()), finalParameterTypes);
	}

	private ExpressionClassVisitor parseFromFileSystem(Object lambda, Class<?> lambdaClass) {
		if (lambdaClassLoader == null)
			throw new RuntimeException(lambdaClassLoaderCreationError);

		String lambdaClassPath = lambdaClassFilePath(lambdaClass);
		Method lambdaMethod = findFunctionalMethod(lambdaClass);
		return parseClass(lambdaClassLoader, lambdaClassPath, lambdaMethod);
	}

	private String lambdaClassFilePath(Class<?> lambdaClass) {
		String lambdaClassName = lambdaClass.getName();
		String className = lambdaClassName.substring(0, lambdaClassName.lastIndexOf('/'));
		return classFilePath(className);
	}

	private String classFilePath(String className) {
		return className.replace('.', '/') + ".class";
	}

	private Method findFunctionalMethod(Class<?> functionalClass) {
		for (Method m : functionalClass.getMethods()) {
			if (!m.isDefault()) {
				return m;
			}
		}
		throw new IllegalArgumentException("Not a lambda expression. No non-default method.");
	}

	private ExpressionClassVisitor parseClass(ClassLoader classLoader, String classFilePath, Method method) {
		return parseClass(classLoader, classFilePath, method.getName(), Type.getMethodDescriptor(method));
	}

	private ExpressionClassVisitor parseClass(ClassLoader classLoader, String classFilePath, String method,
			String methodDescriptor) {
		ExpressionClassVisitor visitor = new ExpressionClassVisitor(method, methodDescriptor, classLoader);
		try {
			try (InputStream classStream = getResourceAsStream(classLoader, classFilePath)) {
				ClassReader reader = new ClassReader(classStream);
				reader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
				return visitor;
			}
		} catch (IOException e) {
			throw new RuntimeException("error parsing class file " + classFilePath, e);
		}
	}

	private InputStream getResourceAsStream(ClassLoader classLoader, String path) throws FileNotFoundException {
		InputStream stream = classLoader.getResourceAsStream(path);
		if (stream == null)
			throw new FileNotFoundException(path);
		return stream;
	}

}
