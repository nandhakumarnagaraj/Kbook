package com.khanabook.saas.feature.auth.controller;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateEmailOtpRequest {
	@NotBlank(message = "New email address cannot be empty")
	@Email(message = "Invalid email address format")
	private String newEmail;
}
