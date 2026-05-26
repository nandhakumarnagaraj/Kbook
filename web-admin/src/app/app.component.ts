import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { trigger, transition, style, animate, query, group } from '@angular/animations';

const fadeSlide = trigger('routeAnimations', [
  transition('* <=> *', [
    style({ position: 'relative' }),
    query(':enter, :leave', [
      style({ position: 'absolute', width: '100%', top: 0, left: 0 })
    ], { optional: true }),
    query(':enter', [
      style({ opacity: 0, transform: 'translateY(16px)' })
    ], { optional: true }),
    group([
      query(':leave', [
        animate('200ms ease', style({ opacity: 0, transform: 'translateY(-12px)' }))
      ], { optional: true }),
      query(':enter', [
        animate('350ms 50ms ease', style({ opacity: 1, transform: 'translateY(0)' }))
      ], { optional: true })
    ])
  ])
]);

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: `
    <div [@routeAnimations]="prepareRoute(o)" style="min-height: 100vh;">
      <router-outlet #o="outlet" />
    </div>
  `,
  animations: [fadeSlide]
})
export class AppComponent {
  prepareRoute(outlet: any) {
    return outlet?.activatedRouteData?.['animation'] || '';
  }
}
