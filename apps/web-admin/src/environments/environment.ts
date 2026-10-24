export const environment = {
  production: false,
  appVersion: 'dev',
  // Relative in dev so the Angular dev-server proxy (see proxy.conf.json) can
  // serve it same-origin — the backend has no CORS config, and preflight
  // OPTIONS requests to it are refused. Prod uses the absolute URL.
  apiBaseUrl: '/api/v1',
  googleClientId: '836086274000-csivf8msgpphl69hn6ftqdeb1mjceaak.apps.googleusercontent.com',
  firebase: {
    apiKey: "YOUR_API_KEY",
    authDomain: "YOUR_PROJECT_ID.firebaseapp.com",
    projectId: "YOUR_PROJECT_ID",
    storageBucket: "YOUR_PROJECT_ID.appspot.com",
    messagingSenderId: "YOUR_MESSAGING_SENDER_ID",
    appId: "YOUR_APP_ID",
    measurementId: "G-YOUR_MEASUREMENT_ID"
  },
  vapidKey: "YOUR_VAPID_KEY"
};
