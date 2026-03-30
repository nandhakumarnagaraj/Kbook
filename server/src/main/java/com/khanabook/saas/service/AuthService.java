package com.khanabook.saas.service;

import com.khanabook.saas.controller.AuthController.AuthResponse;
import com.khanabook.saas.controller.AuthController.LoginRequest;
import com.khanabook.saas.controller.AuthController.SignupRequest;

public interface AuthService {

	AuthResponse login(LoginRequest request);

	AuthResponse signup(SignupRequest request);

	AuthResponse googleLogin(com.khanabook.saas.controller.AuthController.GoogleLoginRequest request);

	void requestSignupOtp(String phoneNumber);

	void requestPasswordResetOtp(String phoneNumber);

	void resetPassword(String phoneNumber, String otp, String newPassword);

	boolean checkUserExists(String phoneNumber);
}
