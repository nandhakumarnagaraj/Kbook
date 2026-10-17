import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';

/**
 * Canonical header search input shared by every web-admin list page.
 *
 * Before this existed, Menu and Staff each hand-rolled a toggle-button search
 * (`showSearchInput` + `*ngIf` + a page-local `*-header-search` width class)
 * while Businesses used a third always-visible variant — same need, three
 * designs. This component is now the single standard: always visible,
 * compact fixed width, live-emit on every keystroke.
 *
 * Usage:
 *   <app-header-search
 *     [(value)]="searchTerm"
 *     (valueChange)="resetPage()"
 *     placeholder="Search staff"
 *     ariaLabel="Search staff members">
 *   </app-header-search>
 */
@Component({
  selector: 'app-header-search',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <input
      type="search"
      class="field-control header-search-input"
      [id]="inputId"
      [ngModel]="value"
      (ngModelChange)="valueChange.emit($event)"
      [placeholder]="placeholder"
      [attr.aria-label]="ariaLabel || placeholder"
    />
  `,
  styles: [
    `
      :host {
        display: inline-flex;
      }
      .header-search-input {
        width: 300px;
        max-width: 100%;
      }
      @media (max-width: 640px) {
        :host {
          width: 100%;
        }
        .header-search-input {
          width: 100%;
        }
      }
    `,
  ],
})
export class HeaderSearchComponent {
  private static nextId = 0;

  /** Current query. Two-way bindable via `[(value)]`. */
  @Input() value = '';
  @Input() placeholder = 'Search';
  @Input() ariaLabel = '';
  @Input() inputId = `header-search-${++HeaderSearchComponent.nextId}`;
  @Output() valueChange = new EventEmitter<string>();
}
