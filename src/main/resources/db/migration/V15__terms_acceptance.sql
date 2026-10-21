-- Horodatage de l'acceptation des Conditions Generales d'Utilisation a l'inscription (voir
-- AuthService.register / RegisterRequest.acceptedTerms). NULL pour les comptes crees AVANT
-- l'introduction de cet ecran de consentement - on ne peut pas retroactivement leur faire
-- accepter des CGU qui n'existaient pas encore au moment de leur inscription.
ALTER TABLE app_user ADD COLUMN tos_accepted_at TIMESTAMP;
