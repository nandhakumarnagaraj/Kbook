import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface BottomActionBarItem {
  label: string;
  icon: string;
  route?: string;
  badge?: string;
  badgeColor?: 'primary' | 'espresso' | 'warning' | 'system';
}

@Component({
  selector: 'kb-bottom-action-bar',
  standalone: true,
  imports: [CommonModule],
  template: `<ng-content></ng-content>`,
  styleUrl: './bottom-action-bar.component.css'
})
export class BottomActionBarComponent {
  @Input() items: BottomActionBarItem[] = [];
  @Input() selectedIndex: number = 0;
  @Output() indexChange = new EventEmitter<number>();

  onSelect(index: number) {
    this.indexChange.emit(index);
  }
}