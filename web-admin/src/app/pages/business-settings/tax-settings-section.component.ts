import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';

export interface TaxSettings {
  gstEnabled: boolean;
  gstin: string;
  gstPercentage: number;
  customTaxName: string;
  customTaxPercentage: number;
}

@Component({
  selector: 'app-tax-settings-section',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule],
  template: `
    <section class="panel settings-section">
      <h3>Tax Configuration</h3>
      <div class="form-grid">
        <div class="field">
          <label class="toggle-label">
            <input type="checkbox" [ngModel]="tax().gstEnabled" (ngModelChange)="updateField('gstEnabled', $event)" />
            <span>Enable GST</span>
          </label>
        </div>
        <div class="field" *ngIf="tax().gstEnabled">
          <label>GSTIN</label>
          <input class="field-control" [ngModel]="tax().gstin" (ngModelChange)="updateField('gstin', $event)" placeholder="GST Number" />
        </div>
        <div class="field" *ngIf="tax().gstEnabled">
          <label>GST Percentage</label>
          <input class="field-control" type="number" [ngModel]="tax().gstPercentage" (ngModelChange)="updateField('gstPercentage', $event)" min="0" max="28" step="0.5" />
        </div>
        <div class="field" *ngIf="!tax().gstEnabled">
          <label>Custom Tax Name</label>
          <input class="field-control" [ngModel]="tax().customTaxName" (ngModelChange)="updateField('customTaxName', $event)" placeholder="e.g. Service Tax" />
        </div>
        <div class="field" *ngIf="!tax().gstEnabled">
          <label>Custom Tax %</label>
          <input class="field-control" type="number" [ngModel]="tax().customTaxPercentage" (ngModelChange)="updateField('customTaxPercentage', $event)" min="0" max="50" step="0.5" />
        </div>
      </div>
    </section>
  `
})
export class TaxSettingsSectionComponent {
  tax = input.required<TaxSettings>();

  taxChange = output<Partial<TaxSettings>>();

  updateField<K extends keyof TaxSettings>(field: K, value: TaxSettings[K]): void {
    this.taxChange.emit({ [field]: value } as Partial<TaxSettings>);
  }
}
