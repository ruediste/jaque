package com.trigersoft.jaque.expression;

import static java.util.stream.Collectors.joining;

import java.util.List;

/**
 * Invocation of an expression as lambda expression.
 */
public class LambdaInvocationExpression extends InvocationExpression {

	protected LambdaInvocationExpression(Expression target, List<Class<?>> paramTypes, List<Expression> arguments) {
		super(ExpressionType.Invoke, target, target.getResultType(), paramTypes, arguments);
	}

	@Override
	protected <T> T visit(ExpressionVisitor<T> v) {
		return v.visit(this);
	}

	@Override
	public String toString() {
		StringBuilder sb=new StringBuilder();
		sb.append("((");
		for (int i=0; i<getParameterTypes().size(); i++){
			if (i>0)
				sb.append(", ");
			Class<?> type = getParameterTypes().get(i);
			sb.append(type);
			sb.append(" P"+i);
		}
		sb.append(") -> {");
		sb.append(getInstance());
		sb.append("}(");
		sb.append(getArguments().stream().map(Object::toString).collect(joining(",")));
		sb.append(")");
		return sb.toString();
	}

}
