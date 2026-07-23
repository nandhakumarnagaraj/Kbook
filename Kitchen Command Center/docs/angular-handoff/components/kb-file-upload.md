# kb-file-upload

## Purpose

Drop-zone / file-picker for single-file uploads (OCR menu import in this project). Handles validation, upload progress, and polling progress presentation.

## Suggested selector

`kb-file-upload`

## Suggested standalone component

`KbFileUploadComponent`

## Inputs

- `accept: string` — e.g. `image/png,image/jpeg,application/pdf`.
- `maxSizeBytes: number`
- `state: 'idle' | 'validating' | 'uploading' | 'processing' | 'success' | 'partial' | 'error'`
- `progress?: number` — 0–100.
- `filename?: string`
- `errorMessage?: string`
- `partialMessage?: string` — for OCR partial-extraction results.

## Outputs

- `fileSelected: EventEmitter<File>` — the host validates + uploads.
- `retry: EventEmitter<void>`
- `cancel: EventEmitter<void>`

## Content projection

- `[slot="hint"]` — instruction text ("PNG, JPG, PDF up to 10 MB").

## Visual variants

Single variant. Dashed border on idle, filled surface during upload, tinted background on state.

## Sizes

Min height 160px on desktop, 120px on mobile.

## CSS classes

`.kb-upload`, `.kb-upload--dragover`, `.kb-upload--<state>`, `.kb-upload__progress`, `.kb-upload__filename`.

## CSS variables consumed

`--kb-color-border-strong`, `--kb-color-primary`, `--kb-color-danger`, `--kb-color-warning`, `--kb-radius-xl`, `--kb-duration-base`.

## States

- **Idle**: dashed border, prompt text, `Choose file` button.
- **Dragover** (idle): border becomes `--kb-color-primary`.
- **Validating**: transient state before upload begins.
- **Uploading**: progress bar 0–100.
- **Processing**: indeterminate progress with "Extracting menu items…".
- **Success**: green check + filename + `View items` action.
- **Partial**: warning tint + `partialMessage` (e.g. "3 items need prices"), continue action.
- **Error**: red tint + `errorMessage` + `Retry` action.

## Mobile behavior

Full width. Drag-and-drop is not required — the picker button is always visible.

## Keyboard behavior

`Choose file` button focusable; Enter/Space opens the native file picker. Drop-zone `Space` also opens picker for keyboard users.

## Accessibility

- Wrap in `<label for="...">` around the native `<input type="file">` for keyboard/AT support.
- Announce state changes via `aria-live="polite"` region.
- Progress bar uses `role="progressbar"` with `aria-valuemin`, `aria-valuemax`, `aria-valuenow` (or `aria-valuetext="Processing"` when indeterminate).

## Related prototype

`/proto/owner/menu` (OCR upload).
