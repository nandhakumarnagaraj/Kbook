package com.khanabook.saas.exception;

public class DuplicateMenuItemException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public DuplicateMenuItemException(String message) {
		super(message);
	}
}
