package com.senetontine.repository;

import java.time.Instant;
import com.senetontine.model.User;
import com.senetontine.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByPhone(String phone);
    boolean existsByPhone(String phone);

    // Tableau de bord admin (AdminService.setRole) : avant de retrograder un admin, il faut
    // s'assurer qu'il n'est pas le DERNIER - sinon plus personne ne pourrait jamais promouvoir
    // qui que ce soit d'autre, la plateforme se retrouverait sans aucun admin.
    long countByRole(UserRole role);

    // Liste/recherche admin des utilisateurs : plafonnee comme toutes les autres listes de
    // l app (voir TontineRepository...) pour eviter de charger
    // une table utilisateurs illimitee en memoire. Cherche a la fois sur le nom et le
    // telephone - un admin peut avoir seulement l'un ou l'autre sous la main.
    List<User> findTop100ByFullNameContainingIgnoreCaseOrPhoneContainingIgnoreCaseOrderByIdDesc(String name, String phone);
    List<User> findTop100ByOrderByIdDesc();

    List<User> findByCreatedAtGreaterThanEqual(Instant from);

}
