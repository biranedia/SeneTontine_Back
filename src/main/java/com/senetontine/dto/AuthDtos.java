package com.senetontine.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AuthDtos {

    public record RegisterRequest(
            @NotBlank String fullName,
            // Accepte les formats senegalais courants : 9 chiffres commencant par 7 (77/78/76/
            // 70/75...), avec ou sans prefixe +221/00221 et espaces/tirets de separation.
            // Sans ce controle, un numero mal saisi (lettres, trop court...) etait accepte tel
            // quel a l'inscription alors qu'il sert d'identifiant unique de compte et de cle de
            // recherche pour "ajouter un membre par telephone" - une variante mal formee pouvait
            // aussi fragmenter artificiellement les compteurs du limiteur anti brute-force.
            @NotBlank
            @Pattern(regexp = "^(\\+221|00221)?[ -]?7[0-9]([ -]?[0-9]){7}$",
                    message = "Numéro de téléphone sénégalais invalide (ex: 77 123 45 67)")
            String phone,
            String email,
            @NotBlank
            @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                    message = "Le mot de passe doit contenir au moins une lettre et un chiffre")
            String password,
            // Validee server-side : un client altere (ou un appel direct a l'API sans passer
            // par l'ecran d'inscription) ne doit jamais pouvoir creer de compte sans consentement
            // explicite aux CGU - la case a cocher cote app n'est qu'une commodite d'UX, pas la
            // seule protection.
            @AssertTrue(message = "Tu dois accepter les Conditions Générales d'Utilisation pour créer un compte")
            boolean acceptedTerms
    ) {}

    public record LoginRequest(
            @NotBlank String phone,
            @NotBlank String password
    ) {}

    // token: pour l'instant un identifiant simple ; a remplacer par un vrai JWT
    // signe (voir note dans SecurityConfig) avant la mise en production.
    public record AuthResponse(
            Long userId,
            String fullName,
            String phone,
            String token,
            String role
    ) {}
}
