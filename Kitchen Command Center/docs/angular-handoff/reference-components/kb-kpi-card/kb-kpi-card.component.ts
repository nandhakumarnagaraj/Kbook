/*
 * REFERENCE ONLY — NOT IMPLEMENTED OR VERIFIED IN THE KHANABOOK ANGULAR APPLICATION.
 *
 * Presentational KPI card. No data fetching. See:
 * docs/angular-handoff/components/kb-kpi-card.md
 */
import { ChangeDetectionStrategy, Component, Input } from "@angular/core";

export type KbKpiVariant = "default" | "hero";
export type KbKpiFormat = "number" | "currency-inr" | "percent" | "plain";

@Component({
  selector: "kb-kpi-card",
  standalone: true,
  templateUrl: "./kb-kpi-card.component.html",
  styleUrls: ["./kb-kpi-card.component.scss"],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class KbKpiCardComponent {
  @Input({ required: true }) label!: string;
  @Input({ required: true }) value!: string | number;
  @Input() format: KbKpiFormat = "plain";
  @Input() delta?: number;
  @Input() deltaLabel?: string;
  @Input() variant: KbKpiVariant = "default";
  @Input() loading = false;

  get trendClass(): string | null {
    if (this.delta == null) return null;
    return this.delta >= 0 ? "kb-kpi-card__delta--up" : "kb-kpi-card__delta--down";
  }

  get trendText(): string | null {
    if (this.delta == null) return null;
    const sign = this.delta > 0 ? "+" : "";
    return `${sign}${this.delta.toFixed(1)}%`;
  }
}
