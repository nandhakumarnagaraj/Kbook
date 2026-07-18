import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { GlobalToastComponent } from './shared/global-toast.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, GlobalToastComponent],
  template: '<router-outlet /><app-global-toast />'
})
export class AppComponent {}
