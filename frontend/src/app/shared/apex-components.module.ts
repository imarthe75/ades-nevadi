import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import {
  ApexAlertComponent,
  ApexBreadcrumbComponent,
  ApexButtonComponent,
  ApexCardComponent,
  ApexFormComponent,
  ApexFormItemComponent,
  ApexInteractiveGridComponent,
  ApexModalDialogComponent,
  ApexNavigationComponent,
  ApexReportComponent,
  ApexSearchComponent,
  ApexTabsComponent,
  ApexDataReporterComponent,
  ApexAIInteractiveReportComponent,
  ApexBadgeComponent,
  ApexChartComponent,
  ApexTreeComponent,
  ApexCarouselComponent,
  ApexCollapseComponent,
  ApexListComponent,
  ApexPopupLOVComponent,
  ApexRTEComponent,
  ApexShuttleComponent,
  ApexTimelineComponent,
  ApexCtxMenuComponent,
  ApexIconListComponent,
  ApexMediaListComponent,
  ApexSliderComponent,
  ApexSpinnerComponent,
  ApexDynamicActionDirective,
  ApexToastContainerComponent,
  ApexFileUploadComponent
} from 'apex-component-library';

const MODULE_IMPORTS = [
  CommonModule,
  FormsModule,
  ReactiveFormsModule,
  // Phase 1
  ApexAlertComponent,
  ApexBreadcrumbComponent,
  ApexButtonComponent,
  ApexCardComponent,
  ApexFormComponent,
  ApexFormItemComponent,
  ApexInteractiveGridComponent,
  ApexModalDialogComponent,
  ApexNavigationComponent,
  ApexReportComponent,
  ApexSearchComponent,
  ApexTabsComponent,
  ApexDataReporterComponent,
  ApexAIInteractiveReportComponent,
  // Phase 2
  ApexBadgeComponent,
  ApexChartComponent,
  ApexTreeComponent,
  ApexCarouselComponent,
  ApexCollapseComponent,
  // Phase 3
  ApexListComponent,
  ApexPopupLOVComponent,
  ApexRTEComponent,
  ApexShuttleComponent,
  ApexTimelineComponent,
  // Phase 4
  ApexCtxMenuComponent,
  ApexIconListComponent,
  ApexSliderComponent,
  ApexSpinnerComponent,
  // Phase 5
  ApexDynamicActionDirective,
  // Phase 6
  ApexToastContainerComponent,
  ApexFileUploadComponent
];

@NgModule({
  imports: [...MODULE_IMPORTS],
  exports: [...MODULE_IMPORTS]
})
export class ApexComponentsModule {}
