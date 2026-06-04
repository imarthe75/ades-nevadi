export const environment = {
  production:       false,
  apiUrl:           'https://ades.setag.mx',
  oidcClientId:     'ades-frontend',
  oidcRedirectUri:  'https://ades.setag.mx/callback',

  // Endpoints del discovery document (auth.ades.setag.mx/application/o/ades-frontend/.well-known/openid-configuration)
  oidcAuthorizeUrl:   'https://auth.ades.setag.mx/application/o/authorize/',
  oidcTokenUrl:       'https://auth.ades.setag.mx/application/o/token/',
  oidcEndSessionUrl:  'https://auth.ades.setag.mx/application/o/ades-frontend/end-session/',
  oidcUserInfoUrl:    'https://auth.ades.setag.mx/application/o/userinfo/',
};
