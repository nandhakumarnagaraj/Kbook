package com.khanabook.saas.exception;

public class BusinessSuspendedException extends RuntimeException {

	public BusinessSuspendedException() {
		super("Business is suspended");
	}

	public BusinessSuspendedException(String message) {
		super(message);
	}
}
