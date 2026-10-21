package com.senetontine.service;

import com.senetontine.dto.NotificationDtos.NotificationInfo;
import com.senetontine.model.AppNotification;
import com.senetontine.model.NotificationType;
import com.senetontine.model.User;
import com.senetontine.repository.AppNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

// Notifications in-app, recuperees par polling cote client (meme choix que le chat de groupe :
// simple et fiable, pas de dependance a un service de push tiers). Appele depuis les services
// metier (WithdrawalService, PaymentService) au moment ou un evenement pertinent se produit -
// jamais depuis les controleurs, pour garantir qu'une notification n'est creee que si
// l'operation metier a reellement reussi (meme transaction).
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final AppNotificationRepository notificationRepository;

    public void notify(User user, NotificationType type, String title, String body) {
        AppNotification n = new AppNotification();
        n.setUser(user);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        notificationRepository.save(n);
    }

    public List<NotificationInfo> list(User user) {
        return notificationRepository.findTop50ByUserIdOrderByIdDesc(user.getId())
                .stream().map(this::toInfo).toList();
    }

    public long unreadCount(User user) {
        return notificationRepository.countByUserIdAndReadFalse(user.getId());
    }

    @Transactional
    public void markRead(User user, Long notificationId) {
        AppNotification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification introuvable"));
        if (!n.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cette notification ne vous appartient pas");
        }
        n.setRead(true);
        notificationRepository.save(n);
    }

    @Transactional
    public void markAllRead(User user) {
        notificationRepository.findTop50ByUserIdOrderByIdDesc(user.getId()).forEach(n -> {
            if (!n.isRead()) {
                n.setRead(true);
                notificationRepository.save(n);
            }
        });
    }

    private NotificationInfo toInfo(AppNotification n) {
        return new NotificationInfo(n.getId(), n.getType().name(), n.getTitle(), n.getBody(), n.isRead(), n.getCreatedAt());
    }
}
