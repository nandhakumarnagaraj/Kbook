package com.khanabook.saas.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateMobileRequest {
	@NotBlank(message = "New mobile number cannot be empty")
	@Pattern(regexp = "^\\d{10}$", message = "Phone number must be exactly 10 digits")
	@Size(min = 10, max = 10)
	private String newMobileNumber;

	@NotBlank(message = "OTP is required")
	@Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
	private String otp;
}
