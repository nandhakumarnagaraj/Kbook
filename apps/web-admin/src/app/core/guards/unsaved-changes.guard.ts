import { inject } from '@angular/core';
import { CanDeactivateFn } from '@angular/router';

/**
 * Unsaved-changes route guard.
 *
 * Prevents accidental navigation away from a dirty form page. A component opts in
 * by exposing either:
 *   - a `canDeactivate(): boolean` method (return false = has unsaved changes), or
 *   - a `dirty` signal (truthy = has unsaved changes).
 *
 * When changes are pending, the user is asked with the browser's native confirm
 * dialog (guards run synchronously, so a dialog component can't be used here).
 *
 * Usage in routes:
 *   { path: 'business/settings', ..., canDeactivate: [unsavedChangesGuard] }
 */
export const unsavedChangesGuard: CanDeactivateFn<unknown> = (component) => {
  const comp = component as { canDeactivate?(): boolean; dirty?: boolean };

  // Component explicitly opts out via canDeactivate() returning false
  if (typeof comp.canDeactivate === 'function' && comp.canDeactivate() === false) {
    return window.confirm('You have unsaved changes. Leave this page without saving?');
  }

  // Component exposes a dirty signal/flag
  if (comp.dirty) {
    return window.confirm('You have unsaved changes. Leave this page without saving?');
  }

  return true;
};
