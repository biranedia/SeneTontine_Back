-- Photo de profil (base64), alimentee automatiquement par le selfie KYC et modifiable
-- ensuite par l'utilisateur (voir User.avatarBase64, KycService.submit, UserController).
ALTER TABLE app_user ADD COLUMN avatar_base64 TEXT;
