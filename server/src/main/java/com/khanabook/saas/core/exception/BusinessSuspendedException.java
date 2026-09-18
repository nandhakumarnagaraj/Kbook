package com.khanabook.saas.core.exception;

public class BusinessSuspendedException extends RuntimeException {

	public BusinessSuspendedException() {
		super("Business is suspended");
	}

	public BusinessSuspendedException(String message) {
		super(message);
	}
}
