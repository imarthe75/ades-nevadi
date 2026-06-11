import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy, TemplateRef, ContentChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PickListModule } from 'primeng/picklist';

@Component({
  selector: 'apex-shuttle',
  standalone: true,
  imports: [CommonModule, PickListModule],
  template: `
    <div class="apex-shuttle-wrapper">
      <label *ngIf="label" class="apex-item-label">{{label}}</label>
      <p-pickList 
        [source]="source" 
        [target]="target" 
        (onMoveToTarget)="onMoveToTarget.emit($event)"
        (onMoveToSource)="onMoveToSource.emit($event)"
        (onMoveAllToTarget)="onMoveAllToTarget.emit($event)"
        (onMoveAllToSource)="onMoveAllToSource.emit($event)"
        [sourceHeader]="sourceHeader" 
        [targetHeader]="targetHeader" 
        [dragdrop]="dragdrop" 
        [responsive]="responsive" 
        [sourceStyle]="sourceStyle" 
        [targetStyle]="targetStyle" 
        [filterBy]="filterBy" 
        [sourceFilterPlaceholder]="sourceFilterPlaceholder" 
        [targetFilterPlaceholder]="targetFilterPlaceholder"
        [showSourceControls]="showSourceControls"
        [showTargetControls]="showTargetControls"
        styleClass="apex-p-picklist">
        
        <ng-template let-item pTemplate="item">
          <ng-container *ngIf="itemTemplate; else defaultItem">
            <ng-container *ngTemplateOutlet="itemTemplate; context: {$implicit: item}"></ng-container>
          </ng-container>
          <ng-template #defaultItem>
            <div>{{displayField ? item[displayField] : item}}</div>
          </ng-template>
        </ng-template>
      </p-pickList>
    </div>
  `,
  styles: [`
    .apex-shuttle-wrapper {
      display: flex;
      flex-direction: column;
      width: 100%;
      margin-bottom: 1rem;
    }
    .apex-item-label {
      font-weight: 500;
      margin-bottom: 0.5rem;
      color: var(--text-color);
    }
    ::ng-deep .apex-p-picklist .p-picklist-list {
      border-radius: var(--border-radius);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApexShuttleComponent {
  @Input() source: any[] = [];
  @Input() target: any[] = [];
  @Input() label?: string;
  @Input() sourceHeader: string = 'Available';
  @Input() targetHeader: string = 'Selected';
  @Input() displayField: string = '';
  @Input() dragdrop: boolean = true;
  @Input() responsive: boolean = true;
  @Input() filterBy: string = '';
  @Input() sourceFilterPlaceholder: string = 'Search...';
  @Input() targetFilterPlaceholder: string = 'Search...';
  @Input() sourceStyle: any = { height: '250px' };
  @Input() targetStyle: any = { height: '250px' };
  @Input() showSourceControls: boolean = true;
  @Input() showTargetControls: boolean = true;

  @Output() onMoveToTarget = new EventEmitter<any>();
  @Output() onMoveToSource = new EventEmitter<any>();
  @Output() onMoveAllToTarget = new EventEmitter<any>();
  @Output() onMoveAllToSource = new EventEmitter<any>();

  @ContentChild('item') itemTemplate?: TemplateRef<any>;
}
