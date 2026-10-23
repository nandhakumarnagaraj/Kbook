import { Pipe, PipeTransform } from '@angular/core';

/**
 * Pure pipe exposing Math static methods in templates.
 *
 * Usage:
 *   {{ value | math:'abs' }}
 *   {{ value | math:'round' }}
 *   {{ a | math:'max':b }}
 *
 * Supported ops: abs, round, floor, ceil, max, min
 */
@Pipe({
  name: 'math',
  standalone: true,
  pure: true,
})
export class MathPipe implements PipeTransform {
  transform(value: number, op: 'abs' | 'round' | 'floor' | 'ceil' | 'max' | 'min' = 'abs', ...args: number[]): number {
    switch (op) {
      case 'abs': return Math.abs(value);
      case 'round': return Math.round(value);
      case 'floor': return Math.floor(value);
      case 'ceil': return Math.ceil(value);
      case 'max': return Math.max(value, ...args);
      case 'min': return Math.min(value, ...args);
      default: return value;
    }
  }
}
