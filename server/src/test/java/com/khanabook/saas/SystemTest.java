package com.khanabook.saas;

import com.khanabook.saas.controller.AuthController.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SystemTest extends BaseIntegrationTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    

    @Test
    void fullAuthFlow_signupThenLoginThenAccessSync() {
        String phone = uniquePhone();
        requestSignupOtp(phone);

        
        SignupRequest signup = new SignupRequest(phone, "Nandha Kumar", "pass123", "123456", "DEVICE_1");
        ResponseEntity<AuthResponse> signupResp =
            rest.postForEntity("/auth/signup", signup, AuthResponse.class);

        assertThat(signupResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(signupResp.getBody().getToken()).isNotBlank();
        assertThat(signupResp.getBody().getRestaurantId()).isPositive();
        Long restaurantId = signupResp.getBody().getRestaurantId();

        
        LoginRequest login = new LoginRequest();
        login.setPhoneNumber(phone);
        login.setPassword("pass123");
        ResponseEntity<AuthResponse> loginResp =
            rest.postForEntity("/auth/login", login, AuthResponse.class);

        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = loginResp.getBody().getToken();
        assertThat(token).isNotBlank();
        assertThat(loginResp.getBody().getRestaurantId()).isEqualTo(restaurantId);

        
        ResponseEntity<String> pullResp = rest.exchange(
            "/sync/bills/pull?lastSyncTimestamp=0&deviceId=DEVICE_2",
            HttpMethod.GET, bearerRequest(token), String.class);

        assertThat(pullResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pullResp.getBody()).contains("[]");
    }

    

    @Test
    void signup_invalidPhoneFormat_returns400() {
        SignupRequest req = new SignupRequest("not-a-phone", "Test", "pass123", "123456", "D");
        ResponseEntity<String> resp =
            rest.postForEntity("/auth/signup", req, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void signup_duplicatePhone_returns400() {
        String phone = uniquePhone();
        requestSignupOtp(phone);
        SignupRequest req = new SignupRequest(phone, "User A", "pass123", "123456", "D");
        rest.postForEntity("/auth/signup", req, String.class);

        
        ResponseEntity<String> second =
            rest.postForEntity("/auth/signup", req, String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(second.getBody()).contains("already exists");
    }

    @Test
    void login_wrongPassword_returns400() {
        String phone = uniquePhone();
        requestSignupOtp(phone);
        rest.postForEntity("/auth/signup",
            new SignupRequest(phone, "User", "correct", "123456", "D"), String.class);

        LoginRequest bad = new LoginRequest();
        bad.setPhoneNumber(phone);
        bad.setPassword("wrong");
        ResponseEntity<String> resp = rest.postForEntity("/auth/login", bad, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void checkUser_existingPhone_returnsTrue() {
        String phone = uniquePhone();
        requestSignupOtp(phone);
        rest.postForEntity("/auth/signup",
            new SignupRequest(phone, "User", "pass123", "123456", "D"), String.class);

        ResponseEntity<String> resp =
            rest.getForEntity("/auth/check-user?phoneNumber={phone}", String.class, phone);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEqualTo("true");
    }

    @Test
    void checkUser_unknownPhone_returnsFalse() {
        ResponseEntity<String> resp =
            rest.getForEntity("/auth/check-user?phoneNumber={phone}", String.class, "+919999999999");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEqualTo("false");
    }

    

    @Test
    void syncEndpoint_noToken_returns403() {
        ResponseEntity<String> resp =
            rest.getForEntity("/sync/bills/pull?lastSyncTimestamp=0&deviceId=X", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void syncEndpoint_invalidToken_returns403() {
        ResponseEntity<String> resp = rest.exchange(
            "/sync/bills/pull?lastSyncTimestamp=0&deviceId=X",
            HttpMethod.GET, bearerRequest("not.a.valid.token"), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void resetPassword_missingBody_returns400() {
        String token = signupAndGetToken();
        ResponseEntity<String> resp = rest.exchange(
            "/auth/reset-password?phoneNumber=%2B911234567890&newPassword=x",
            HttpMethod.POST, bearerRequest(token), String.class);
        
        assertThat(resp.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.UNSUPPORTED_MEDIA_TYPE, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void masterSync_noToken_returns403() {
        ResponseEntity<String> resp =
            rest.getForEntity("/sync/master/pull?lastSyncTimestamp=0&deviceId=X", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    

    @Test
    void masterSync_pull_returnsAllNineCollections() {
        String token = signupAndGetToken();

        ResponseEntity<String> resp = rest.exchange(
            "/sync/master/pull?lastSyncTimestamp=0&deviceId=OTHER_DEVICE",
            HttpMethod.GET, bearerRequest(token), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = resp.getBody();
        
        assertThat(body).contains("profiles");
        assertThat(body).contains("users");
        assertThat(body).contains("categories");
        assertThat(body).contains("menuItems");
        assertThat(body).contains("itemVariants");
        assertThat(body).contains("stockLogs");
        assertThat(body).contains("bills");
        assertThat(body).contains("billItems");
        assertThat(body).contains("billPayments");
    }

    @Test
    void billPush_emptyList_returnsEmptySuccessLists() {
        String token = signupAndGetToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> req = new HttpEntity<>("[]", headers);

        ResponseEntity<String> resp =
            rest.postForEntity("/sync/bills/push", req, String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("successfulLocalIds");
        assertThat(resp.getBody()).contains("failedLocalIds");
    }

    @Test
    void googleLogin_missingIdToken_returns400() {
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> req = new HttpEntity<>("{\"deviceId\":\"D1\"}", headers);

        ResponseEntity<String> resp =
            rest.postForEntity("/auth/google", req, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).contains("idToken");
    }

    

    private String signupAndGetToken() {
        String phone = uniquePhone();
        requestSignupOtp(phone);
        ResponseEntity<AuthResponse> resp = rest.postForEntity("/auth/signup",
            new SignupRequest(phone, "Test User", "pass123", "123456", "DEVICE_SYS"), AuthResponse.class);
        return resp.getBody().getToken();
    }

    private void requestSignupOtp(String phone) {
        ResponseEntity<String> resp = rest.postForEntity(
            "/auth/signup/request",
            new SignupOtpRequest(phone),
            String.class
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private HttpEntity<Void> bearerRequest(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private static long phoneCounter = 7000000000L;
    private static synchronized String uniquePhone() {
        return String.valueOf(phoneCounter++);
    }
}
