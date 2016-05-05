package com.trigersoft.jaque.expression;

public enum TenaryExpressionType {

	Conditional("?");
	private final String operator;

	private TenaryExpressionType(String operator) {
		this.operator = operator;
	}

	@Override
	public String toString() {
		return operator;
	}
}
