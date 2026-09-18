package com.khanabook.saas.core.exception;

public class DuplicateStaffPhoneException extends IllegalArgumentException {

    public DuplicateStaffPhoneException() {
        super("Phone number already exists");
    }
}
