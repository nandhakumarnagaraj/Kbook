package com.khanabook.saas.webadmin;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.NotificationEvent;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.service.PushNotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class NotificationControllerTest extends BaseIntegrationTest {

    private static final Long RESTAURANT_ID = 9501L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.khanabook.saas.repository.UserRepository userRepository;

    @Autowired
    private com.khanabook.saas.repository.RestaurantProfileRepository restaurantProfileRepository;

    @MockBean
    private PushNotificationService pushNotificationService;

    @org.junit.jupiter.api.BeforeEach
    void cleanDb() {
        userRepository.deleteAll();
        restaurantProfileRepository.deleteAll();
    }

    @Test
    void testSendTestNotification() throws Exception {
        String token = persistUserAndGetToken("owner-notify@test.com", RESTAURANT_ID, UserRole.OWNER);

        mockMvc.perform(post("/notifications/test")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hello\",\"message\":\"This is a test notification\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        Mockito.verify(pushNotificationService).pushToRestaurant(
                eq(RESTAURANT_ID),
                eq("Hello"),
                eq("This is a test notification"),
                eq("system"),
                any(),
                any(),
                any()
        );
    }

    @Test
    void testRegisterDeviceToken() throws Exception {
        String token = persistUserAndGetToken("owner-notify@test.com", RESTAURANT_ID, UserRole.OWNER);

        mockMvc.perform(post("/notifications/device-token")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"dummy-fcm-token\",\"platform\":\"android\",\"deviceId\":\"device-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        Mockito.verify(pushNotificationService).registerToken(
                eq(RESTAURANT_ID),
                eq("dummy-fcm-token"),
                eq("android"),
                eq("device-123")
        );
    }

    @Test
    void testUnregisterDeviceToken() throws Exception {
        String token = persistUserAndGetToken("owner-notify@test.com", RESTAURANT_ID, UserRole.OWNER);

        mockMvc.perform(delete("/notifications/device-token")
                        .header("Authorization", "Bearer " + token)
                        .param("deviceId", "device-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        Mockito.verify(pushNotificationService).unregisterToken(
                eq(RESTAURANT_ID),
                eq("device-123")
        );
    }

    @Test
    void testGetNotifications() throws Exception {
        String token = persistUserAndGetToken("owner-notify@test.com", RESTAURANT_ID, UserRole.OWNER);

        NotificationEvent event = new NotificationEvent();
        event.setId(10L);
        event.setTitle("Test Event");
        event.setMessage("Message");

        Mockito.when(pushNotificationService.getNotifications(eq(RESTAURANT_ID), eq(50)))
                .thenReturn(List.of(event));
        Mockito.when(pushNotificationService.getUnreadCount(eq(RESTAURANT_ID)))
                .thenReturn(5L);

        mockMvc.perform(get("/notifications")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.notifications[0].id").value(10))
                .andExpect(jsonPath("$.unreadCount").value(5));
    }

    @Test
    void testGetUnreadCount() throws Exception {
        String token = persistUserAndGetToken("owner-notify@test.com", RESTAURANT_ID, UserRole.OWNER);

        Mockito.when(pushNotificationService.getUnreadCount(eq(RESTAURANT_ID)))
                .thenReturn(12L);

        mockMvc.perform(get("/notifications/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.unreadCount").value(12));
    }

    @Test
    void testMarkAsRead() throws Exception {
        String token = persistUserAndGetToken("owner-notify@test.com", RESTAURANT_ID, UserRole.OWNER);

        mockMvc.perform(post("/notifications/10/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        Mockito.verify(pushNotificationService).markAsRead(eq(10L));
    }

    @Test
    void testMarkAllAsRead() throws Exception {
        String token = persistUserAndGetToken("owner-notify@test.com", RESTAURANT_ID, UserRole.OWNER);

        mockMvc.perform(post("/notifications/mark-all-read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        Mockito.verify(pushNotificationService).markAllAsRead(eq(RESTAURANT_ID));
    }
}
