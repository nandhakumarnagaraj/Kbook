package com.khanabook.saas.exception;

public class DuplicateStaffPhoneException extends IllegalArgumentException {

    public DuplicateStaffPhoneException() {
        super("Phone number already exists");
    }
}
