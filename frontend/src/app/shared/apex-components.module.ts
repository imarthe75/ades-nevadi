import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import {
  // Data & Reports
  ApexInteractiveGridComponent,
  ApexReportComponent,
  ApexDataReporterComponent,
  // Navigation & Layout
  ApexNavigationComponent,
  ApexBreadcrumbComponent,
  ApexModalDialogComponent,
  // Feedback
  ApexAlertComponent,
  ApexToastContainerComponent,
  // Forms
  ApexFormComponent,
  ApexFormItemComponent,
  ApexSearchComponent,
  ApexPopupLOVComponent,
  ApexFileUploadComponent,
  // Data Display
  ApexChartComponent,
  ApexListComponent,
  ApexTimelineComponent,
  ApexIconListComponent,
  ApexMediaListComponent,
  // Utilities
  ApexDynamicActionDirective,
} from 'apex-component-library';

const APEX_COMPONENTS = [
  CommonModule,
  FormsModule,
  ReactiveFormsModule,
  // Data & Reports
  ApexInteractiveGridComponent,
  ApexReportComponent,
  ApexDataReporterComponent,
  // Navigation & Layout
  ApexNavigationComponent,
  ApexBreadcrumbComponent,
  ApexModalDialogComponent,
  // Feedback
  ApexAlertComponent,
  ApexToastContainerComponent,
  // Forms
  ApexFormComponent,
  ApexFormItemComponent,
  ApexSearchComponent,
  ApexPopupLOVComponent,
  ApexFileUploadComponent,
  // Data Display
  ApexChartComponent,
  ApexListComponent,
  ApexTimelineComponent,
  ApexIconListComponent,
  ApexMediaListComponent,
  // Utilities
  ApexDynamicActionDirective,
];

@NgModule({
  imports: [...APEX_COMPONENTS],
  exports: [...APEX_COMPONENTS],
})
export class ApexComponentsModule {}
