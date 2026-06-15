/*
 * Public API Surface of apex-component-library
 * Conservados: componentes con funcionalidad genuina sobre PrimeNG
 */

// ── Data & Reports ─────────────────────────────────────────────────────────
export * from './lib/interactive-grid/interactive-grid.component';
export * from './lib/report/report.component';
export type { ReportColumn, ReportConfig } from './lib/report/report.component';
export * from './lib/data-reporter/data-reporter.component';

// ── Navigation & Layout ────────────────────────────────────────────────────
export * from './lib/navigation/navigation.component';
export * from './lib/breadcrumb/breadcrumb.component';
export * from './lib/modal-dialog/modal-dialog.component';

// ── Feedback ───────────────────────────────────────────────────────────────
export * from './lib/alert/alert.component';
export * from './lib/notifications/notification.service';
export * from './lib/notifications/toast-container.component';

// ── Forms ──────────────────────────────────────────────────────────────────
export * from './lib/form/form.component';
export * from './lib/search/search.component';
export * from './lib/popuplov/popuplov.component';
export type { ApexLOVItem } from './lib/popuplov/popuplov.component';
export * from './lib/file-upload/file-upload.component';

// ── Data Display ───────────────────────────────────────────────────────────
export * from './lib/chart/chart.component';
export * from './lib/list/list.component';
export type { ApexListItem, ApexListItemAction } from './lib/list/list.component';
export * from './lib/timeline/timeline.component';
export * from './lib/iconlist/iconlist.component';
export type { ApexIconListItem } from './lib/iconlist/iconlist.component';
export * from './lib/medialist/medialist.component';
export type { ApexMediaListItem } from './lib/medialist/medialist.component';

// ── Utilities ──────────────────────────────────────────────────────────────
export * from './lib/export/export.service';
export * from './lib/dynamic-actions/dynamic-action.service';
export * from './lib/dynamic-actions/dynamic-action.directive';
export * from './lib/dynamic-actions/dynamic-action-target.directive';
export * from './lib/validations/apex-validators';
export * from './lib/validations/server-validation.service';
