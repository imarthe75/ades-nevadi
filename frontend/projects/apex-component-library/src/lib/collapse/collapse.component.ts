import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy, ContentChildren, QueryList, AfterContentInit, TemplateRef, ContentChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AccordionModule } from 'primeng/accordion';

@Component({
  selector: 'apex-collapse-panel',
  standalone: true,
  imports: [CommonModule],
  template: `<ng-content></ng-content>`
})
export class ApexCollapsePanelComponent {
  @Input() header: string = '';
  @Input() icon?: string;
  @Input() disabled: boolean = false;
  @Input() selected: boolean = false;
  
  @ContentChild('content') contentTemplate?: TemplateRef<any>;
}

@Component({
  selector: 'apex-collapse',
  standalone: true,
  imports: [CommonModule, AccordionModule, ApexCollapsePanelComponent],
  template: `
    <div class="apex-collapse-wrapper">
      <p-accordion [multiple]="multiple" [value]="activeIndex">
        <p-accordion-panel *ngFor="let panel of panels" [value]="panel.header" [disabled]="panel.disabled">
          <p-accordion-header>
            <span class="apex-accordion-header-text">
              <i *ngIf="panel.icon" [class]="panel.icon" style="margin-right: 0.5rem"></i>
              {{panel.header}}
            </span>
          </p-accordion-header>
          <p-accordion-content>
            <ng-container *ngIf="panel.contentTemplate; else fallbackContent">
              <ng-container *ngTemplateOutlet="panel.contentTemplate"></ng-container>
            </ng-container>
            <ng-template #fallbackContent>
               <ng-content></ng-content>
            </ng-template>
          </p-accordion-content>
        </p-accordion-panel>
      </p-accordion>
    </div>
  `,
  styles: [`
    .apex-collapse-wrapper {
      width: 100%;
    }
    .apex-accordion-header-text {
      font-weight: 500;
      display: flex;
      align-items: center;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApexCollapseComponent implements AfterContentInit {
  @Input() multiple: boolean = false;
  @Input() activeIndex: number | number[] | null = null;
  
  @Output() onOpen = new EventEmitter<any>();
  @Output() onClose = new EventEmitter<any>();

  @ContentChildren(ApexCollapsePanelComponent) panels!: QueryList<ApexCollapsePanelComponent>;

  ngAfterContentInit() {
    // If we want to capture the panels content, they are usually projected. 
    // In primeNG p-accordion we need to render the content inside the tab.
  }
}
