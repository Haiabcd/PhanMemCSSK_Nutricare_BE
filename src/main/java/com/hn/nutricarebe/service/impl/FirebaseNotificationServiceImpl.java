package com.hn.nutricarebe.service.impl;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.hn.nutricarebe.dto.NotificationFirebase;
import com.hn.nutricarebe.service.FirebaseNotificationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
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
            throw new RuntimeException("Failed to send Firebase notification", e);
        }
    }

}
