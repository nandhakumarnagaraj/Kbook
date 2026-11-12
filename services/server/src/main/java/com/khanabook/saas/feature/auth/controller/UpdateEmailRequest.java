package com.khanabook.saas.feature.auth.controller;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateEmailRequest {
	@NotBlank(message = "New email address cannot be empty")
	@Email(message = "Invalid email address format")
	private String newEmail;

	@NotBlank(message = "OTP is required")
	@Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
	private String otp;
}
