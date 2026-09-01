import { Component, input, output, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

type SecretState = 'stored' | 'replacing' | 'revealed' | 'error';

@Component({
  selector: 'kb-secret-input',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="kb-secret-input" [class.error]="state() === 'error'">
      @if (state() === 'stored') {
        <div class="secret-display">
          <code class="masked-value">{{ maskedValue() }}</code>
          <div class="secret-actions">
            <button type="button" class="kb-btn kb-btn--ghost" (click)="reveal()">
              <svg class="icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M2 10l6-6 6 6M2 10l6 6 6-6"/>
              </svg>
              <span>Reveal</span>
            </button>
            <button type="button" class="kb-btn kb-btn--outline" (click)="startReplace()">
              Replace
            </button>
          </div>
        </div>
      }

      @if (state() === 'replacing') {
        <div class="secret-replace">
          <label class="kb-field">
            <span class="kb-field-label">{{ label() }}</span>
            <input
              type="password"
              class="kb-field-input"
              [(ngModel)]="newValue"
              (keydown.enter)="save()"
              placeholder="Enter new value"
              autocomplete="new-password"
            />
          </label>
          <div class="secret-actions">
            <button type="button" class="kb-btn kb-btn--primary" (click)="save()" [disabled]="!newValue().trim()">
              Save
            </button>
            <button type="button" class="kb-btn kb-btn--ghost" (click)="cancelReplace()">
              Cancel
            </button>
          </div>
        </div>
      }

      @if (state() === 'revealed') {
        <div class="secret-display">
          <code class="revealed-value">{{ storedValue() }}</code>
          <div class="secret-actions">
            <button type="button" class="kb-btn kb-btn--ghost" (click)="hide()">
              <svg class="icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M10 7.5l6 6M10 7.5l-6 6M10 7.5v-15"/>
              </svg>
              <span>Hide</span>
            </button>
            <button type="button" class="kb-btn kb-btn--outline" (click)="startReplace()">
              Replace
            </button>
            <button type="button" class="kb-btn kb-btn--ghost" (click)="copy()">
              <svg class="icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M8 3v14M8 3h8a2 2 0 012 2v10a2 2 0 01-2 2H8a2 2 0 01-2-2V5a2 2 0 012-2h8M16 3H8"/>
              </svg>
              <span>Copy</span>
            </button>
          </div>
        </div>
      }

      @if (state() === 'error') {
        <p class="kb-field-error">{{ errorMessage() }}</p>
      }
    </div>
  `,
  styles: [`
    .kb-secret-input { display: grid; gap: 8px; }
    .secret-display { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
    .masked-value { font-family: var(--kb-font-mono); font-size: 13px; color: var(--kb-color-muted-foreground); letter-spacing: 2px; background: var(--kb-color-muted); padding: 8px 12px; border-radius: var(--kb-radius-md); border: 1px solid var(--kb-color-border); }
    .revealed-value { font-family: var(--kb-font-mono); font-size: 13px; color: var(--kb-color-foreground); background: var(--kb-color-surface); padding: 8px 12px; border-radius: var(--kb-radius-md); border: 1px solid var(--kb-color-border); }
    .secret-actions { display: flex; gap: 8px; flex-wrap: wrap; }
    .secret-replace { display: grid; gap: 8px; }
    .icon { width: 16px; height: 16px; flex-shrink: 0; }
    .kb-field { display: grid; gap: 4px; }
    .kb-field-label { font-size: 12px; font-weight: 600; color: var(--kb-color-foreground); }
    .kb-field-input { min-height: 40px; border: 1px solid var(--kb-color-border-strong); border-radius: var(--kb-radius-lg); padding: 8px 12px; background: var(--kb-color-surface); color: var(--kb-color-foreground); font-size: 14px; }
    .kb-field-input:focus { outline: none; border-color: var(--kb-color-primary); box-shadow: 0 0 0 3px var(--kb-color-ring); }
    .kb-field-error { font-size: 12px; color: var(--kb-color-danger); margin: 0; }

    .kb-btn { display: inline-flex; align-items: center; gap: 6px; padding: 8px 14px; border-radius: var(--kb-radius-lg); font-weight: 500; font-size: 13px; cursor: pointer; border: 1px solid transparent; transition: all 120ms var(--kb-ease-standard); }
    .kb-btn:disabled { opacity: 0.5; cursor: not-allowed; }
    .kb-btn--primary { background: var(--kb-color-primary); color: var(--kb-color-primary-foreground); border-color: var(--kb-color-primary); }
    .kb-btn--primary:hover:not(:disabled) { background: var(--kb-color-primary-hover); }
    .kb-btn--ghost { background: transparent; color: var(--kb-color-foreground); }
    .kb-btn--ghost:hover:not(:disabled) { background: var(--kb-color-muted); }
    .kb-btn--outline { background: transparent; color: var(--kb-color-foreground); border-color: var(--kb-color-border-strong); }
    .kb-btn--outline:hover:not(:disabled) { background: var(--kb-color-muted); border-color: var(--kb-color-border); }
  `]
})
export class SecretInputComponent {
  label = input.required<string>();
  value = input<string>('');

  reveal = output<void>();
  replace = output<string>();
  save = output<string>();
  copied = output<void>();

  protected state = signal<SecretState>('stored');
  protected newValue = signal('');
  protected errorMessage = signal('');

  protected storedValue = computed(() => this.value());
  protected maskedValue = computed(() => '••••••••');

  startReplace(): void {
    this.state.set('replacing');
    this.newValue.set('');
    this.errorMessage.set('');
  }

  cancelReplace(): void {
    this.state.set('stored');
    this.newValue.set('');
  }

  doReveal(): void {
    this.state.set('revealed');
    this.reveal.emit();
  }

  doHide(): void {
    this.state.set('stored');
  }

  doSave(): void {
    const v = this.newValue().trim();
    if (!v) { this.errorMessage.set('Value cannot be empty'); return; }
    this.state.set('stored');
    this.save.emit(v);
    this.newValue.set('');
  }

  doCopy(): void {
    navigator.clipboard.writeText(this.storedValue());
    this.copied.emit();
  }

  reveal() { this.doReveal(); }
  hide() { this.doHide(); }
  startReplace() { this.startReplace(); }
  cancelReplace() { this.cancelReplace(); }
  save() { this.doSave(); }
  copy() { this.doCopy(); }
}