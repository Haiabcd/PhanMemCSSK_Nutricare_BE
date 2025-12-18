package com.hn.nutricarebe.service.impl;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.hn.nutricarebe.dto.NotificationFirebase;
import com.hn.nutricarebe.service.FirebaseNotificationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FirebaseNotificationServiceImpl implements FirebaseNotificationService {

    FirebaseMessaging firebaseMessaging;


    @Override
    public void sendTestNotification(NotificationFirebase firebase) {
        try {
            Message message = Message.builder()
                    .setToken(firebase.getToken())
                    .setNotification(Notification.builder()
                            .setTitle(firebase.getTitle())
                            .setBody(firebase.getBody())
                            .build())
                    .build();

            firebaseMessaging.send(message);
        } catch (Exception e) {
            log.warn("[FCM] Failed to send notification, skip. token={}", firebase.getToken(), e);
        }
    }

}
