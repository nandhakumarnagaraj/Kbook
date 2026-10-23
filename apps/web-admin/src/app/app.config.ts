import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideServiceWorker } from '@angular/service-worker';
import { routes } from './app.routes';
import { jwtInterceptor } from './core/auth/jwt.interceptor';
import { FirebaseInitService } from './core/firebase/firebase.init';
import { KBNotificationService } from './core/firebase/kb-notification.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(withInterceptors([jwtInterceptor])),
    provideServiceWorker('ngsw-worker.js'),
    FirebaseInitService,
    KBNotificationService,
  ],
};
