/*
 * Copyright TrigerSoft <kostat@trigersoft.com> 
 * 
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

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Describes a lambda expression consisting of
 * <ul>
 * <li>an expression representing the code of the lambda</li>
 * <li>values for captured arguments</li>
 * <li>a this instance, if applicable</li>
 * <li>the lambda interface and the method beeing implemented, defining
 * parameter types and return type</li>
 * </ul>
 * Please note that this is not an {@link Expression} itself.
 * <p>
 * The body expression contains a {@link ThisExpression} for references to the
 * this instance, {@link CapturedArgumentExpression}s to reference captured
 * arguments, which can be resolved using
 * {@link #getValue(CapturedArgumentExpression)} and {@link ParameterExpression}
 * s for the parameters of the lambda expression.
 * <p>
 * Use {@link #parse(Object)} method to get a lambda expression tree.
 * </p>
 * 
 * @param <F>
 *            type of the lambda represented by this LambdaExpression.
 * 
 * @author <a href="mailto://kostat@trigersoft.com">Konstantin Triger</a>
 */

public final class LambdaExpression<F> {

	private final Expression _body;
	private final Method lambdaInterfaceMethod;
	private final Object this_;
	private final Object[] capturedArgumentValues;

	public LambdaExpression(Method lambdaInterfaceMethod, Expression _body, Object this_,
			Object[] capturedArgumentValues) {
		super();
		this.lambdaInterfaceMethod = lambdaInterfaceMethod;
		this._body = _body;
		this.this_ = this_;
		this.capturedArgumentValues = capturedArgumentValues;
	}

	/**
	 * Gets the body of the lambda expression.
	 * 
	 * @return {@link Expression}
	 */
	public Expression getBody() {
		return _body;
	}

	/**
	 * Creates {@link LambdaExpression} representing the lambda expression tree.
	 * 
	 * @param <T>
	 *            the type of lambda to parse
	 * 
	 * @param lambda
	 *            - the lambda
	 * 
	 * @return {@link LambdaExpression} representing the lambda expression tree.
	 */
	@SuppressWarnings("unchecked")
	public static <T> LambdaExpression<T> parse(T lambda) {
		return (LambdaExpression<T>) new ExpressionClassCracker().lambda(lambda);
	}

	public static <T> LambdaExpression<T> parseLambdaMethod(Method lambdaInterfaceMethod, Member lambdaImplementation,
			Object[] capturedArgs) {
		return new ExpressionClassCracker().parseLambdaMethod(lambdaInterfaceMethod, lambdaImplementation,
				capturedArgs);
	}

	/**
	 * Produces a {@link Function} that represents the lambda expression.
	 * 
	 * @return {@link Function} that represents the lambda expression.
	 */
	public Function<Object[], ?> compile() {
		final Function<Object[], ?> f = _body.accept(Interpreter.Instance);
		return f;
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append('(');
		Class<?>[] paramTypes = lambdaInterfaceMethod.getParameterTypes();
		for (int i = 0; i < paramTypes.length; i++) {
			if (i > 0) {
				b.append(',');
				b.append(' ');
			}
			b.append(paramTypes[i].getName());
			b.append(" P");
			b.append(i);
		}
		b.append(')');
		b.append("->");
		b.append('{');
		b.append(getBody().toString());
		b.append('}');
		return b.toString();
	}

	public List<Class<?>> getParamTypes() {
		return Arrays.asList(lambdaInterfaceMethod.getParameterTypes());
	}

	public Class<?> getReturnType() {
		return lambdaInterfaceMethod.getReturnType();
	}

	public Object getValue(CapturedArgumentExpression e) {
		return capturedArgumentValues[e.getIndex()];
	}
}
