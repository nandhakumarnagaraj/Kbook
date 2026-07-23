/*
 * REFERENCE ONLY — NOT IMPLEMENTED OR VERIFIED IN THE KHANABOOK ANGULAR APPLICATION.
 *
 * Presentational status pill. No business logic. See:
 * docs/angular-handoff/components/kb-status-badge.md
 */
import { ChangeDetectionStrategy, Component, Input } from "@angular/core";

export type KbBadgeTone = "neutral" | "success" | "warning" | "danger" | "info" | "primary";

export type KbBadgeSize = "sm" | "md";

@Component({
  selector: "kb-status-badge",
  standalone: true,
  templateUrl: "./kb-status-badge.component.html",
  styleUrls: ["./kb-status-badge.component.scss"],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class KbStatusBadgeComponent {
  @Input({ required: true }) tone!: KbBadgeTone;
  @Input({ required: true }) label!: string;
  @Input() size: KbBadgeSize = "sm";
  @Input() icon?: string;

  get classes(): string[] {
    return ["kb-badge", `kb-badge--${this.tone}`, `kb-badge--${this.size}`];
  }
}
